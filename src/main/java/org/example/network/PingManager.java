// src/main/java/org/example/network/PingManager.java
package org.example.network;
import org.example.network.PingLogger;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.ConnectException; // Import for specific connection refused exception

public class PingManager {
    private final String host;
    private final int    port;
    private final String serverName;
    private final NetworkManager networkManagerOwner;
    private final int maxReconnectAttempts;

    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile Socket       socket; // volatile because it's updated by a different thread
    private volatile OutputStream out;
    private volatile InputStream  in; // volatile because it's updated by a different thread

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);

    private int currentReconnectAttempt = 0;

    private static final int PING_INTERVAL_SECONDS = 30;
    private static final int PING_RESPONSE_TIMEOUT_MS = 5000;
    private static final int RECONNECT_DELAY_MS = 5000; // Delay between socket reconnection attempts

    // Use NetworkManager's SignOnStatus enum
    private static final NetworkManager.SignOnStatus SIGN_ON_SUCCESS = NetworkManager.SignOnStatus.SUCCESS;
    private static final NetworkManager.SignOnStatus SIGN_ON_FAILURE = NetworkManager.SignOnStatus.FAILURE;
    private static final NetworkManager.SignOnStatus SIGN_OFF_ADVICE = NetworkManager.SignOnStatus.SIGN_OFF_ADVICE_RECEIVED;


    public PingManager(Socket socket, String serverName, NetworkManager owner, int maxAttempts) throws IOException {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            throw new IOException("PingManager: Initial socket is not valid.");
        }
        this.socket     = socket;
        this.serverName = serverName;
        this.networkManagerOwner = owner;
        this.maxReconnectAttempts = maxAttempts;

        this.host       = socket.getInetAddress().getHostAddress();
        this.port       = socket.getPort();
        this.out        = socket.getOutputStream();
        this.in         = socket.getInputStream();

        this.scheduler  = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PingMgr-" + serverName);
            t.setDaemon(true); // Allow application exit even if this thread is running
            return t;
        });
        PingLogger.log("PingManager created for " + serverName + " at " + host + ":" + port);
    }

    public void startSendingPing() {
        if (stopped.get()) {
            PingLogger.log("PingManager[" + serverName + "]: Cannot start, already stopped.");
            return;
        }
        PingLogger.log("PingManager[" + serverName + "]: Starting periodic pings every " + PING_INTERVAL_SECONDS + " seconds.");
        // Schedule the ping cycle to run periodically after an initial delay
        scheduler.scheduleWithFixedDelay(this::pingCycleWrapper, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stopSendingPing() {
        if (stopped.getAndSet(true)) {
            return; // Already stopped or stopping
        }
        PingLogger.log("PingManager[" + serverName + "]: Stopping scheduler.");
        scheduler.shutdownNow(); // Attempt to stop running tasks
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                PingLogger.log("PingManager[" + serverName + "]: Scheduler did not terminate within 5 seconds.");
            }
        } catch (InterruptedException e) {
            PingLogger.log("PingManager[" + serverName + "]: Interrupted while waiting for scheduler termination.");
            Thread.currentThread().interrupt(); // Restore interrupt flag
        }
        // Note: The socket itself is closed by NetworkManager owner during cleanup, not here directly.
        // However, closeSocketQuietly is used internally during reconnection attempts.
        PingLogger.log("PingManager[" + serverName + "]: Stopped.");
    }

    /**
     * Safely closes the internal socket and nulls references.
     * Used internally by the PingManager when detecting a problem or during its own reconnect logic.
     * The NetworkManager owner handles cleanup during shutdown or state reset externally.
     */
    private void closeSocketQuietly() {
        try {
            Socket currentSocket = this.socket; // Get current reference
            if (currentSocket != null && !currentSocket.isClosed()) {
                System.out.println("PingManager[" + serverName + "]: Closing socket internally.");
                currentSocket.close();
                System.out.println("PingManager[" + serverName + "]: Socket closed internally.");
            }
        } catch (IOException e) {
            System.err.println("PingManager[" + serverName + "]: Error closing socket internally: " + e.getMessage());
        } finally {
            // Set references to null *after* trying to close
            this.socket = null;
            this.out = null;
            this.in = null;
        }
    }

    public boolean isStopped() {
        return stopped.get();
    }

    /**
     * Wrapper for the ping cycle logic to catch unexpected errors.
     */
    private void pingCycleWrapper() {
        // isReconnecting check is done within pingCycle
        if (stopped.get() || Thread.currentThread().isInterrupted()) {
            System.out.println("PingManager[" + serverName + "]: Ping cycle interrupted or manager stopped.");
            return;
        }
        try {
            pingCycle();
        } catch (Throwable t) {
            // Catch any severe, unexpected errors that might crash the ping thread
            System.err.println("PingManager[" + serverName + "]: CRITICAL ERROR caught in ping cycle: " + t.getMessage());
            t.printStackTrace();
            handleConnectionProblem(); // Trigger reconnection sequence
        }
    }

    /**
     * Executes the ping and response logic.
     * Triggers reconnection attempt on errors.
     */
    private void pingCycle() {
        if (stopped.get() || Thread.currentThread().isInterrupted()) {
            return;
        }
        if (isReconnecting.get()) {
            // System.out.println("PingManager[" + serverName + "]: Skipping ping cycle, reconnection in progress."); // Potentially noisy
            return;
        }

        // Check if the socket and streams are valid before trying to use them for ping
        Socket currentSocket = this.socket;
        OutputStream currentOut = this.out;
        InputStream currentIn = this.in;

        if (currentSocket == null || currentSocket.isClosed() || !currentSocket.isConnected() ||
                currentIn == null || currentOut == null ||
                currentSocket.isInputShutdown() || currentSocket.isOutputShutdown()) {
            System.out.println("PingManager[" + serverName + "]: Socket or streams are not valid for ping. Triggering reconnect.");
            handleConnectionProblem(); // This will start the reconnection process in a separate thread
            return; // Stop this ping cycle execution
        }

        try {
            sendPing(currentOut); // Use the potentially volatile currentOut
            listenForPingResponse(currentSocket, currentIn); // Use currentSocket and currentIn

            // If ping and response were successful, connection is healthy. Reset reconnect attempts count.
            // isReconnecting is managed by attemptReconnection
            // currentReconnectAttempt is only reset *after* a successful reconnect sequence finishes.
            PingLogger.log("PingManager[" + serverName + "]: Ping cycle successful.");

        } catch (SocketTimeoutException e) {
            PingLogger.log("PingManager[" + serverName + "]: Timeout waiting for echotest response.");
            handleConnectionProblem();
        } catch (SocketException e) {
            PingLogger.log("PingManager[" + serverName + "]: Socket error during ping cycle: " + e.getMessage());
            handleConnectionProblem();
        } catch (IOException | ISOException e) {
            if (stopped.get()) {
                PingLogger.log("PingManager[" + serverName + "]: IO/ISO Error during ping cycle, but manager is stopping.");
                return;
            }
            PingLogger.log("PingManager[" + serverName + "]: IO/ISO Error during ping cycle: " + e.getMessage());
            handleConnectionProblem();
        }
    }

    /**
     * Initiates the reconnection attempt process if one is not already running.
     */
    private void handleConnectionProblem() {
        if (stopped.get()) return;
        // Use compareAndSet to ensure only one reconnection thread starts
        if (isReconnecting.compareAndSet(false, true)) {
            PingLogger.log("PingManager[" + serverName + "]: Triggering reconnect sequence.");
            // Run reconnection in a separate thread to not block the ping scheduler thread
            new Thread(this::attemptReconnectionWrapper, "PingMgr-Reconnect-" + serverName).start();
        } else {
            // System.out.println("PingManager[" + serverName + "]: Reconnect sequence already triggered."); // Potentially noisy
        }
    }

    /**
     * Wrapper for attemptReconnection to ensure isReconnecting flag is reset.
     */
    private void attemptReconnectionWrapper() {
        currentReconnectAttempt = 0; // Reset attempt counter for the start of a *new* sequence
        try {
            // Loop through reconnection attempts until success or max attempts reached
            while (currentReconnectAttempt < maxReconnectAttempts && !stopped.get() && !Thread.currentThread().isInterrupted()) {
                currentReconnectAttempt++;
                PingLogger.log("PingManager[" + serverName + "]: Starting reconnection attempt " + currentReconnectAttempt + "/" + maxReconnectAttempts + " to " + host + ":" + port);

                // Wait before retrying connection to avoid hammering the server
                if (currentReconnectAttempt > 1) { // Don't delay before the very first attempt in a sequence
                    PingLogger.log("PingManager[" + serverName + "]: Waiting " + RECONNECT_DELAY_MS + "ms before attempt " + currentReconnectAttempt);
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException e) {
                        PingLogger.log("PingManager[" + serverName + "]: Reconnection attempt interrupted during sleep.");
                        Thread.currentThread().interrupt(); // Restore flag
                        break; // Exit the while loop
                    }
                    if (stopped.get() || Thread.currentThread().isInterrupted()) {
                        PingLogger.log("PingManager[" + serverName + "]: Reconnection interrupted after sleep.");
                        break; // Exit the while loop
                    }
                }

                // --- Attempt to reconnect and sign on ---
                if (tryConnectAndSignOn()) {
                    PingLogger.log("PingManager[" + serverName + "]: Reconnect and Sign-On successful after " + currentReconnectAttempt + " attempt(s).");
                    currentReconnectAttempt = 0; // Reset counter on success
                    break; // Exit the while loop on success
                } else {
                    PingLogger.log("PingManager[" + serverName + "]: Reconnection attempt " + currentReconnectAttempt + " failed.");
                }
            }

            // After the loop, check if we succeeded or ran out of attempts
            if (!stopped.get() && !Thread.currentThread().isInterrupted() && currentReconnectAttempt >= maxReconnectAttempts) {
                PingLogger.log("PingManager[" + serverName + "]: Max reconnection attempts (" + maxReconnectAttempts + ") reached. Reporting persistent failure.");
                // Notify NetworkManager of persistent failure
                if (networkManagerOwner != null) {
                    networkManagerOwner.handlePersistentConnectionLoss(serverName);
                }
                // The NetworkManager owner is responsible for stopping this PingManager now.
                PingLogger.log("PingManager[" + serverName + "]: PingManager concluding its reconnection sequence after max attempts.");
                // Do NOT stop the PingManager here; let NetworkManager do it during cleanup.
            } else if (stopped.get() || Thread.currentThread().isInterrupted()){
                PingLogger.log("PingManager[" + serverName + "]: Reconnection sequence stopped prematurely.");
            }


        } finally {
            // Ensure the flag is reset regardless of how attemptReconnection finishes (success, failure, interrupt)
            isReconnecting.set(false);
            PingLogger.log("PingManager[" + serverName + "]: Reconnect sequence finished.");
        }
    }

    /**
     * Attempts to create a new socket and perform Sign-On.
     * Updates internal socket/stream references on success.
     * @return true if socket connected AND Sign-On was successful, false otherwise.
     */
    private boolean tryConnectAndSignOn() {
        // Close the old socket before attempting to create a new one
        closeSocketQuietly(); // This nulls out 'socket', 'in', 'out'

        Socket newSocket = null;
        try {
            // 1. Attempt to connect the socket
            PingLogger.log("PingManager[" + serverName + "]: Attempting to connect socket to " + host + ":" + port);
            newSocket = new Socket(this.host, this.port);
            // Set non-blocking or timeout for read operations if needed later, but SO_TIMEOUT is sufficient for the Ping/SignOn logic.
            PingLogger.log("PingManager[" + serverName + "]: Socket connected successfully.");

            // 2. Attempt to get streams from the new socket
            OutputStream newOut = newSocket.getOutputStream();
            InputStream newIn = newSocket.getInputStream();
            PingLogger.log("PingManager[" + serverName + "]: Streams obtained from new socket.");


            // 3. Perform Sign-On using the new socket and its streams
            PingLogger.log("PingManager[" + serverName + "]: Attempting to perform Sign-On on the new socket.");
            NetworkManager.SignOnStatus signOnStatus = NetworkManager.performSignOn(newSocket, serverName); // Use the static method from NetworkManager

            if (signOnStatus == SIGN_ON_SUCCESS) {
                PingLogger.log("PingManager[" + serverName + "]: Sign-On successful on the new socket.");
                // Update instance references with the new socket and streams
                this.socket = newSocket; // Assign the valid, signed-on socket
                this.out = newOut;
                this.in = newIn;
                PingLogger.log("PingManager[" + serverName + "]: Internal socket/stream references updated.");
                return true; // Reconnection AND Sign-On successful
            } else {
                PingLogger.log("PingManager[" + serverName + "]: Sign-On failed on the new socket. Status: " + signOnStatus);
                // Sign-On failed -> close the newly created socket
                try {
                    if (newSocket != null && !newSocket.isClosed()) {
                        PingLogger.log("PingManager[" + serverName + "]: Closing new socket due to Sign-On failure.");
                        newSocket.close();
                    }
                } catch (IOException closeEx) {
                    PingLogger.log("PingManager[" + serverName + "]: Error closing new socket after Sign-On failure: " + closeEx.getMessage());
                }
                newSocket = null; // Null the reference after closing
                return false; // Reconnection attempted, but Sign-On failed
            }

        } catch (ConnectException ce) { // <--- CATCH SPECIFICALLY FOR "CONNECTION REFUSED"
            PingLogger.log("PingManager[" + serverName + "]: Socket connection attempt failed: Connection refused: " + ce.getMessage());
            // No stack trace for this common and expected error when server is down
            // closeSocketQuietly() is called at the start, newSocket is null or closed on failure.
            return false; // Connection failed at socket level
        } catch (IOException e) { // <--- CATCH FOR OTHER IOExceptions (e.g., timeout during connect, broken pipe during stream assignment)
            PingLogger.log("PingManager[" + serverName + "]: Socket connection or stream acquisition failed due to IO error: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for other IOErrors
            // Ensure any partial connection or acquired streams are cleaned up
            try {
                if (newSocket != null && !newSocket.isClosed()) {
                    PingLogger.log("PingManager[" + serverName + "]: Closing new socket due to IO error.");
                    newSocket.close();
                }
            } catch (IOException closeEx) {
                PingLogger.log("PingManager[" + serverName + "]: Error closing new socket after IO error: " + closeEx.getMessage());
            }
            newSocket = null; // Null the reference
            return false; // Connection failed at socket level
        } catch (Exception e) { // <--- GENERIC CATCH FOR ALL OTHER UNEXPECTED EXCEPTIONS (e.g., ISOException from performSignOn)
            PingLogger.log("PingManager[" + serverName + "]: Unexpected exception during reconnect/Sign-On attempt: " + e.getMessage());
            e.printStackTrace(); // Keep stack trace here for unexpected errors
            try {
                if (newSocket != null && !newSocket.isClosed()) {
                    PingLogger.log("PingManager[" + serverName + "]: Closing new socket due to unexpected exception.");
                    newSocket.close();
                }
            } catch (IOException closeEx) {
                PingLogger.log("PingManager[" + serverName + "]: Error closing new socket after unexpected exception: " + closeEx.getMessage());
            }
            newSocket = null; // Null the reference
            return false; // Reconnection failed due to unexpected error
        }
    }


    private void sendPing(OutputStream currentOut) throws IOException, ISOException {
        // The null/closed check is done in pingCycle() before calling this.
        ISOMsg ping = new ISOMsg();
        // Use a new packager for thread safety if concurrent access is possible, though pingCycle is scheduled on a single thread.
        ping.setPackager(new ISO87APackager());
        ping.setMTI("0800");
        ping.set(70, "301"); // Echo Test / Ping network management code
        byte[] data = ping.pack();

        // Ensure the stream is still valid just before writing
        if (currentOut == null || socket == null || socket.isClosed() || socket.isOutputShutdown()) {
            throw new IOException("Output stream or socket became invalid just before sending ping on " + serverName);
        }

        currentOut.write(data);
        currentOut.flush();
        PingLogger.log("PingManager[" + serverName + "]: echotest (Ping 0800/301) sent.");
    }

    private void listenForPingResponse(Socket currentSocket, InputStream currentIn) throws IOException, ISOException, SocketTimeoutException {
        // The null/closed check is done in pingCycle() before calling this.

        int originalTimeout = currentSocket.getSoTimeout();
        try {
            currentSocket.setSoTimeout(PING_RESPONSE_TIMEOUT_MS); // Set a specific timeout for the ping response read

            byte[] buf = new byte[4096]; // Buffer to read the response
            int len = currentIn.read(buf); // This read will block until data or timeout/close

            if (len > 0) {
                ISOMsg resp = new ISOMsg();
                // Use a new packager for thread safety if needed, similar to sendPing
                resp.setPackager(new ISO87APackager());
                byte[] actualData = new byte[len];
                System.arraycopy(buf, 0, actualData, 0, len);
                resp.unpack(actualData);

                String mti = resp.getMTI();
                // Check for field existence before getting string to avoid NPE
                String code = resp.hasField(39) ? resp.getString(39) : null;
                String net = resp.hasField(70) ? resp.getString(70) : null;

                PingLogger.log("PingManager[" + serverName + "]: echotest response received: MTI=" + mti + ", Code=" + code + ", NetCode=" + net);

                // Check if the response is a valid Echo Response (0810/301)
                if (!("0810".equals(mti) && "301".equals(net))) {
                    System.err.println("PingManager[" + serverName + "]: Received unexpected response during Ping cycle. MTI: " + mti + ", NetCode: " + net);
                    // Depending on protocol, unexpected response might indicate a problem,
                    // but we won't treat it as a connection loss immediately unless protocol says so.
                }
                // If we get a valid 0810/301, the link is considered healthy.
            } else if (len == 0) {
                // This can happen if the server performs a graceful shutdown.
                PingLogger.log("PingManager[" + serverName + "]: Received 0 bytes for ping response. Connection might be closing gracefully.");
                throw new IOException("Received 0 bytes - connection potentially closed gracefully."); // Treat as an IO error leading to reconnect
            }
            else { // len == -1 (stream closed by the server)
                PingLogger.log("PingManager[" + serverName + "]: Connection closed by server during ping response wait (read returned -1).");
                throw new IOException("Read returned -1 - connection closed by server."); // Treat as an IO error leading to reconnect
            }
        } finally {
            // Always restore the original socket timeout
            if (currentSocket != null && !currentSocket.isClosed()) {
                try { currentSocket.setSoTimeout(originalTimeout); } catch (SocketException e) { /* ignore if socket already closed concurrently */ }
            }
        }
    }
}