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
import java.net.ConnectException;

public class PingManager {
    private final String host;
    private final int    port;
    private final String serverName;
    private final NetworkManager networkManagerOwner;
    private final int maxReconnectAttempts;

    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile Socket       socket;
    private volatile OutputStream out;
    private volatile InputStream  in; // v

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);

    private int currentReconnectAttempt = 0;

    private static final int PING_INTERVAL_SECONDS = 30;
    private static final int PING_RESPONSE_TIMEOUT_MS = 5000;
    private static final int RECONNECT_DELAY_MS = 5000; // Delay between socket reconnection attempts

    // Use NetworkManager's SignOnStatus enum
    private static final NetworkManager.SignOnStatus SIGN_ON_SUCCESS = NetworkManager.SignOnStatus.SUCCESS;
    //private static final NetworkManager.SignOnStatus SIGN_ON_FAILURE = NetworkManager.SignOnStatus.FAILURE;

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
            t.setDaemon(true);
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
            this.socket = null;
            this.out = null;
            this.in = null;
        }
    }

    public boolean isStopped() {
        return stopped.get();
    }


    private void pingCycleWrapper() {
        if (stopped.get() || Thread.currentThread().isInterrupted()) {
            System.out.println("PingManager[" + serverName + "]: Ping cycle interrupted or manager stopped.");
            return;
        }
        try {
            pingCycle();
        } catch (Throwable t) {
            System.err.println("PingManager[" + serverName + "]: CRITICAL ERROR caught in ping cycle: " + t.getMessage());
            t.printStackTrace();
            handleConnectionProblem();
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
            return;
        }
        Socket currentSocket = this.socket;
        OutputStream currentOut = this.out;
        InputStream currentIn = this.in;

        if (currentSocket == null || currentSocket.isClosed() || !currentSocket.isConnected() ||
                currentIn == null || currentOut == null ||
                currentSocket.isInputShutdown() || currentSocket.isOutputShutdown()) {
            System.out.println("PingManager[" + serverName + "]: Socket or streams are not valid for ping. Triggering reconnect.");
            handleConnectionProblem();
            return;
        }

        try {
            sendPing(currentOut);
            listenForPingResponse(currentSocket, currentIn);
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


    private void handleConnectionProblem() {
        if (stopped.get()) return;
        if (isReconnecting.compareAndSet(false, true)) {
            PingLogger.log("PingManager[" + serverName + "]: Triggering reconnect sequence.");
            new Thread(this::attemptReconnectionWrapper, "PingMgr-Reconnect-" + serverName).start();
        } else {
            // System.out.println("PingManager[" + serverName + "]: Reconnect sequence already triggered.");
        }
    }

    /**
     * Wrapper for attemptReconnection to ensure isReconnecting flag is reset.
     */
    private void attemptReconnectionWrapper() {
        currentReconnectAttempt = 0;
        try {
            // Boucle tentative de reconnection
            while (currentReconnectAttempt < maxReconnectAttempts && !stopped.get() && !Thread.currentThread().isInterrupted()) {
                currentReconnectAttempt++;
                PingLogger.log("PingManager[" + serverName + "]: Starting reconnection attempt " + currentReconnectAttempt + "/" + maxReconnectAttempts + " to " + host + ":" + port);

                if (currentReconnectAttempt > 1) {
                    PingLogger.log("PingManager[" + serverName + "]: Waiting " + RECONNECT_DELAY_MS + "ms before attempt " + currentReconnectAttempt);
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException e) {
                        PingLogger.log("PingManager[" + serverName + "]: Reconnection attempt interrupted during sleep.");
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (stopped.get() || Thread.currentThread().isInterrupted()) {
                        PingLogger.log("PingManager[" + serverName + "]: Reconnection interrupted after sleep.");
                        break;
                    }
                }

                // tentative de reconnection
                if (tryConnectAndSignOn()) {
                    PingLogger.log("PingManager[" + serverName + "]: Reconnect and Sign-On successful after " + currentReconnectAttempt + " attempt(s).");
                    currentReconnectAttempt = 0; // Reset counter on success
                    break;
                } else {
                    PingLogger.log("PingManager[" + serverName + "]: Reconnection attempt " + currentReconnectAttempt + " failed.");
                }
            }

            if (!stopped.get() && !Thread.currentThread().isInterrupted() && currentReconnectAttempt >= maxReconnectAttempts) {
                PingLogger.log("PingManager[" + serverName + "]: Max reconnection attempts (" + maxReconnectAttempts + ") reached. Reporting persistent failure.");
                if (networkManagerOwner != null) {
                    networkManagerOwner.handlePersistentConnectionLoss(serverName);
                }
                PingLogger.log("PingManager[" + serverName + "]: PingManager concluding its reconnection sequence after max attempts.");
            } else if (stopped.get() || Thread.currentThread().isInterrupted()){
                PingLogger.log("PingManager[" + serverName + "]: Reconnection sequence stopped prematurely.");
            }


        } finally {
            isReconnecting.set(false);
            PingLogger.log("PingManager[" + serverName + "]: Reconnect sequence finished.");
        }
    }

    private boolean tryConnectAndSignOn() {
        closeSocketQuietly();

        Socket newSocket = null;
        try {
            PingLogger.log("PingManager[" + serverName + "]: Attempting to connect socket to " + host + ":" + port);
            newSocket = new Socket(this.host, this.port);
            PingLogger.log("PingManager[" + serverName + "]: Socket connected successfully.");

            OutputStream newOut = newSocket.getOutputStream();
            InputStream newIn = newSocket.getInputStream();
            PingLogger.log("PingManager[" + serverName + "]: Streams obtained from new socket.");


            PingLogger.log("PingManager[" + serverName + "]: Attempting to perform Sign-On on the new socket.");
            NetworkManager.SignOnStatus signOnStatus = NetworkManager.performSignOn(newSocket, serverName); // Use the static method from NetworkManager

            if (signOnStatus == SIGN_ON_SUCCESS) {
                PingLogger.log("PingManager[" + serverName + "]: Sign-On successful on the new socket.");
                this.socket = newSocket;
                this.out = newOut;
                this.in = newIn;
                PingLogger.log("PingManager[" + serverName + "]: Internal socket/stream references updated.");
                return true;
            } else {
                PingLogger.log("PingManager[" + serverName + "]: Sign-On failed on the new socket. Status: " + signOnStatus);
                try {
                    if (newSocket != null && !newSocket.isClosed()) {
                        PingLogger.log("PingManager[" + serverName + "]: Closing new socket due to Sign-On failure.");
                        newSocket.close();
                    }
                } catch (IOException closeEx) {
                    PingLogger.log("PingManager[" + serverName + "]: Error closing new socket after Sign-On failure: " + closeEx.getMessage());
                }
                newSocket = null;
                return false;
            }

        } catch (ConnectException ce) {
            PingLogger.log("PingManager[" + serverName + "]: Socket connection attempt failed: Connection refused: " + ce.getMessage());

            return false;
        } catch (IOException e) {
            PingLogger.log("PingManager[" + serverName + "]: Socket connection or stream acquisition failed due to IO error: " + e.getMessage());
            e.printStackTrace();
            try {
                if (newSocket != null && !newSocket.isClosed()) {
                    PingLogger.log("PingManager[" + serverName + "]: Closing new socket due to IO error.");
                    newSocket.close();
                }
            } catch (IOException closeEx) {
                PingLogger.log("PingManager[" + serverName + "]: Error closing new socket after IO error: " + closeEx.getMessage());
            }
            newSocket = null;
            return false;
        } catch (Exception e) {
            PingLogger.log("PingManager[" + serverName + "]: Unexpected exception during reconnect/Sign-On attempt: " + e.getMessage());
            e.printStackTrace();
            try {
                if (newSocket != null && !newSocket.isClosed()) {
                    PingLogger.log("PingManager[" + serverName + "]: Closing new socket due to unexpected exception.");
                    newSocket.close();
                }
            } catch (IOException closeEx) {
                PingLogger.log("PingManager[" + serverName + "]: Error closing new socket after unexpected exception: " + closeEx.getMessage());
            }
            newSocket = null;
            return false;
        }
    }


    private void sendPing(OutputStream currentOut) throws IOException, ISOException {
        ISOMsg ping = new ISOMsg();
        ping.setPackager(new ISO87APackager());
        ping.setMTI("0800");
        ping.set(70, "301");
        byte[] data = ping.pack();

        if (currentOut == null || socket == null || socket.isClosed() || socket.isOutputShutdown()) {
            throw new IOException("Output stream or socket became invalid just before sending ping on " + serverName);
        }

        currentOut.write(data);
        currentOut.flush();
        PingLogger.log("PingManager[" + serverName + "]: echotest (Ping 0800/301) sent.");
    }

    private void listenForPingResponse(Socket currentSocket, InputStream currentIn) throws IOException, ISOException, SocketTimeoutException {

        int originalTimeout = currentSocket.getSoTimeout();
        try {
            currentSocket.setSoTimeout(PING_RESPONSE_TIMEOUT_MS); // Set a specific timeout for the ping response read

            byte[] buf = new byte[4096]; // Buffer pour lire la reponse
            int len = currentIn.read(buf);

            if (len > 0) {
                ISOMsg resp = new ISOMsg();
                resp.setPackager(new ISO87APackager());
                byte[] actualData = new byte[len];
                System.arraycopy(buf, 0, actualData, 0, len);
                resp.unpack(actualData);

                String mti = resp.getMTI();
                String code = resp.hasField(39) ? resp.getString(39) : null;
                String net = resp.hasField(70) ? resp.getString(70) : null;

                PingLogger.log("PingManager[" + serverName + "]: echotest response received: MTI=" + mti + ", Code=" + code + ", NetCode=" + net);

                if (!("0810".equals(mti) && "301".equals(net))) {
                    System.err.println("PingManager[" + serverName + "]: Received unexpected response during Ping cycle. MTI: " + mti + ", NetCode: " + net);

                }
            } else if (len == 0) {
                PingLogger.log("PingManager[" + serverName + "]: Received 0 bytes for ping response. Connection might be closing gracefully.");
                throw new IOException("Received 0 bytes - connection potentially closed gracefully.");
            }
            else {
                PingLogger.log("PingManager[" + serverName + "]: Connection closed by server during ping response wait (read returned -1).");
                throw new IOException("Read returned -1 - connection closed by server.");
            }
        } finally {

            if (currentSocket != null && !currentSocket.isClosed()) {
                try { currentSocket.setSoTimeout(originalTimeout); } catch (SocketException ignored) {  }
            }
        }
    }
}