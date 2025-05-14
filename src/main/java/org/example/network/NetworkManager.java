package org.example.network;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;
import org.jpos.iso.packager.ISO87APackager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkManager {
    private static final String SERVER_HOST_FE1 = "localhost";
    private static final int SERVER_PORT_FE1 = 5000;

    private static final String SERVER_HOST_FE2 = "localhost";
    private static final int SERVER_PORT_FE2 = 6000;

    private static final int RECONNECTION_DELAY_MS = 5000;
    private static final int SIGN_ON_RESPONSE_TIMEOUT_MS = 10000;
    private static final int MAX_PING_RECONNECT_ATTEMPTS = 5;

    private Socket socketFE1;
    private Socket socketFE2;
    private PingManager pingManagerFE1;
    private PingManager pingManagerFE2;

    private volatile boolean fe1SignedOn = false;
    private volatile boolean fe2SignedOn = false;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final Object fe1Lock = new Object();
    private final Object fe2Lock = new Object();


    public void start() {
        new Thread(() -> {
            while (running.get()) {
                try {
                    boolean fe1NeedsConnect;
                    boolean fe2NeedsConnect;

                    synchronized (fe1Lock) { fe1NeedsConnect = !fe1SignedOn; }
                    synchronized (fe2Lock) { fe2NeedsConnect = !fe2SignedOn; }

                    if (fe1NeedsConnect) {

                        connectAndSignOn("FE1", SERVER_HOST_FE1, SERVER_PORT_FE1);
                    }
                    synchronized (fe2Lock) { fe2NeedsConnect = !fe2SignedOn; }
                    if (fe2NeedsConnect) {

                        connectAndSignOn("FE2", SERVER_HOST_FE2, SERVER_PORT_FE2);
                    }

                    Thread.sleep(RECONNECTION_DELAY_MS);
                } catch (InterruptedException e) {
                    System.out.println("NetworkManager client main loop interrupted.");
                    running.set(false);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("NetworkManager: Unhandled error in connection loop: " + e.getMessage());
                    e.printStackTrace();
                    try { Thread.sleep(RECONNECTION_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); running.set(false); }
                }
            }
            System.out.println("NetworkManager client main loop finished.");
        }, "NetworkManager-Client-MainLoop").start();
    }

    /**
     * Callback pour PingManager pour signaler une perte de connexion persistante.
     * @param serverName Nom du serveur ("FE1" ou "FE2").
     */
    public void handlePersistentConnectionLoss(String serverName) {
        Object lock = "FE1".equals(serverName) ? fe1Lock : fe2Lock;
        synchronized (lock) {
            System.out.println("NetworkManager: Received persistent connection failure report for " + serverName);
            cleanupConnectionResource(serverName, false);
        }
    }

    private Socket attemptConnection(String host, int port, int maxAttempts, String serverLabel) throws IOException {
        int attempt = 0;
        while (attempt < maxAttempts) {
            attempt++;
            try {
                System.out.println("NetworkManager: Attempting connection to " + serverLabel + " (" + host + ":" + port + ") - Attempt " + attempt + "/" + maxAttempts);
                Socket socket = new Socket(host, port);
                System.out.println("NetworkManager: Successfully connected to " + serverLabel);
                return socket;
            } catch (IOException e) {
                System.err.println("NetworkManager: Connection attempt " + attempt + " to " + serverLabel + " failed: " + e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(RECONNECTION_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Connection interrupted", e);
                    }
                }
            }
        }
        throw new IOException("Failed to connect to " + serverLabel + " after " + maxAttempts + " attempts");
    }
    private void updateConnectionResources(String serverLabel, Socket socket, SignOnStatus status) {
        if (status == SignOnStatus.SUCCESS) {
            if ("FE1".equals(serverLabel)) {
                synchronized (fe1Lock) {
                    if (socketFE1 != null && socketFE1 != socket) {
                        try { socketFE1.close(); } catch (IOException e) {}
                    }
                    socketFE1 = socket;
                    fe1SignedOn = true;
                    restartPingManager("FE1", socket);
                }
            } else {
                synchronized (fe2Lock) {
                    if (socketFE2 != null && socketFE2 != socket) {
                        try { socketFE2.close(); } catch (IOException e) {}
                    }
                    socketFE2 = socket;
                    fe2SignedOn = true;
                    restartPingManager("FE2", socket);
                }
            }
        } else {
            closeSocket(socket);
            if ("FE1".equals(serverLabel)) {
                synchronized (fe1Lock) {
                    fe1SignedOn = false;
                }
            } else {
                synchronized (fe2Lock) {
                    fe2SignedOn = false;
                }
            }
        }
    }

    private void restartPingManager(String serverLabel, Socket socket) {
        try {
            if ("FE1".equals(serverLabel)) {
                if (pingManagerFE1 != null) pingManagerFE1.stopSendingPing();
                pingManagerFE1 = new PingManager(socket, serverLabel, this, MAX_PING_RECONNECT_ATTEMPTS);
                pingManagerFE1.startSendingPing();
            } else {
                if (pingManagerFE2 != null) pingManagerFE2.stopSendingPing();
                pingManagerFE2 = new PingManager(socket, serverLabel, this, MAX_PING_RECONNECT_ATTEMPTS);
                pingManagerFE2.startSendingPing();
            }
        } catch (IOException e) {
            System.err.println("Failed to restart PingManager for " + serverLabel + ": " + e.getMessage());
            cleanupConnectionResource(serverLabel, true);
        }
    }
    private void connectAndSignOn(String preferredServerLabel, String host, int port) {
        String otherServerLabel = "FE1".equals(preferredServerLabel) ? "FE2" : "FE1";
        String host1 = preferredServerLabel.equals("FE1") ? SERVER_HOST_FE1 : SERVER_HOST_FE2;
        int port1 = preferredServerLabel.equals("FE1") ? SERVER_PORT_FE1 : SERVER_PORT_FE2;
        String host2 = otherServerLabel.equals("FE1") ? SERVER_HOST_FE1 : SERVER_HOST_FE2;
        int port2 = otherServerLabel.equals("FE1") ? SERVER_PORT_FE1 : SERVER_PORT_FE2;

        Socket connectedSocket = null;
        String actualConnectedServerLabel = preferredServerLabel;
        int maxAttempts = 5;

        try {
            connectedSocket = attemptConnection(host1, port1, maxAttempts, preferredServerLabel);
        } catch (IOException e) {
            System.err.println("NetworkManager: Primary server " + preferredServerLabel + " unreachable. Switching to backup " + otherServerLabel);
            try {
                connectedSocket = attemptConnection(host2, port2, maxAttempts, otherServerLabel);
                actualConnectedServerLabel = otherServerLabel;
            } catch (IOException ex) {
                System.err.println("NetworkManager: Failed to connect to both servers: " + e.getMessage() + " | " + ex.getMessage());
                return;
            }
        }

        // Gestion du Sign-On
        SignOnStatus signOnStatus;
        try {
            signOnStatus = performSignOn(connectedSocket, actualConnectedServerLabel);
        } catch (IOException | ISOException e) {
            System.err.println("NetworkManager: Exception during Sign-On for " + actualConnectedServerLabel + ": " + e.getMessage());
            closeSocket(connectedSocket);
            return;
        }

        // Mise à jour des ressources
        updateConnectionResources(actualConnectedServerLabel, connectedSocket, signOnStatus);
    }

    private void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }


    public enum SignOnStatus {
        SUCCESS,
        FAILURE,
        SIGN_OFF_ADVICE_RECEIVED
    }

    public static SignOnStatus performSignOn(Socket socket, String serverName) throws IOException, ISOException {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        ISOMsg signOnMsg = new ISOMsg();
        signOnMsg.setPackager(new ISO87APackager());
        signOnMsg.setMTI("0800");
        signOnMsg.set(70, "001");
        byte[] data = signOnMsg.pack();
        out.write(data);
        out.flush();
        System.out.println("Sign-On (0800/001) sent to " + serverName);

        byte[] buffer = new byte[4096];
        int bytesRead = -1;
        try {
            socket.setSoTimeout(SIGN_ON_RESPONSE_TIMEOUT_MS);
            bytesRead = in.read(buffer);
        } catch (SocketTimeoutException e) {
            System.err.println("Timeout (" + SIGN_ON_RESPONSE_TIMEOUT_MS + "ms) waiting for Sign-On response from " + serverName);
            return SignOnStatus.FAILURE;
        } finally {
            if (socket != null && !socket.isClosed()) {
                try { socket.setSoTimeout(0); } catch (SocketException e) { /* ignore if already closed */ }
            }
        }

        if (bytesRead > 0) {
            ISOMsg response = new ISOMsg();
            response.setPackager(new ISO87APackager());
            byte[] actualData = new byte[bytesRead];
            System.arraycopy(buffer, 0, actualData, 0, bytesRead);
            response.unpack(actualData);

            String mti = response.getMTI();
            String code = response.getString(39);
            String netCode = response.getString(70);

            System.out.println("Response from " + serverName + " to Sign-On: MTI=" + mti + ", Code=" + code + ", NetworkCode=" + netCode);

            if ("0810".equals(mti) && "002".equals(netCode)) {
                System.out.println("Sign-Off ADVICE (002) received from " + serverName);
                return SignOnStatus.SIGN_OFF_ADVICE_RECEIVED;
            }
            if ("0810".equals(mti) && "00".equals(code) && "001".equals(netCode)) {
                System.out.println("Sign-On successful for " + serverName + " (0810/001, code 00)");
                return SignOnStatus.SUCCESS;
            } else {
                System.err.println("Sign-On failed for " + serverName + " (unexpected response): MTI=" + mti + ", Code=" + code + ", NetCode=" + netCode);
                return SignOnStatus.FAILURE;
            }
        } else {
            System.err.println("No response (or stream closed) received from " + serverName + " for Sign-On (bytesRead=" + bytesRead + ").");
            return SignOnStatus.FAILURE;
        }
    }

    private void cleanupConnectionResource(String serverName, boolean sendSignOffMessage) {
        // Assumes called within appropriate synchronized(lock) block
        System.out.println("NetworkManager: Cleaning up resources for " + serverName + (sendSignOffMessage ? " (with Sign-Off message)" : ""));

        if ("FE1".equals(serverName)) {
            if (pingManagerFE1 != null) {
                System.out.println("NetworkManager: Stopping PingManager for " + serverName + " during cleanup.");
                pingManagerFE1.stopSendingPing();
                pingManagerFE1 = null;
            }
            if (socketFE1 != null) {
                System.out.println("NetworkManager: Closing socket for " + serverName + " during cleanup.");
                try {
                    if (sendSignOffMessage && !socketFE1.isClosed() && socketFE1.isConnected()) {
                        IsoMessageManager.sendSignOff(socketFE1.getOutputStream());
                        System.out.println("Sign-Off (0800/002) sent to " + serverName + " before close.");
                    }
                    if (!socketFE1.isClosed()) {
                        socketFE1.close();
                        System.out.println("Socket closed for " + serverName + ".");
                    }
                } catch (Exception e) {
                    System.err.println("Error closing/Sign-Off FE1: " + e.getMessage());
                }
                socketFE1 = null;
            }
            fe1SignedOn = false;
            System.out.println("NetworkManager: State for " + serverName + " reset (signedOn=false).");

        } else if ("FE2".equals(serverName)) {
            if (pingManagerFE2 != null) {
                System.out.println("NetworkManager: Stopping PingManager for " + serverName + " during cleanup.");
                pingManagerFE2.stopSendingPing();
                pingManagerFE2 = null;
            }
            if (socketFE2 != null) {
                System.out.println("NetworkManager: Closing socket for " + serverName + " during cleanup.");
                try {
                    if (sendSignOffMessage && !socketFE2.isClosed() && socketFE2.isConnected()) {
                        IsoMessageManager.sendSignOff(socketFE2.getOutputStream());
                        System.out.println("Sign-Off (0800/002) sent to " + serverName + " before close.");
                    }
                    if (!socketFE2.isClosed()) {
                        socketFE2.close();
                        System.out.println("Socket closed for " + serverName + ".");
                    }
                } catch (Exception e) {
                    System.err.println("Error closing/Sign-Off FE2: " + e.getMessage());
                }
                socketFE2 = null;
            }
            fe2SignedOn = false;
            System.out.println("NetworkManager: State for " + serverName + " reset (signedOn=false).");
        }
        System.out.println("NetworkManager: Cleanup complete for " + serverName + ".");
    }


    public void signOff(String serverName) {
        Object lock = "FE1".equals(serverName) ? fe1Lock : fe2Lock;
        synchronized (lock) {
            System.out.println("NetworkManager: Initiating user/external Sign-Off for " + serverName);
            cleanupConnectionResource(serverName, true);
        }
    }

    public Socket getSocketForServer(String serverName) {
        if ("FE1".equals(serverName)) {
            synchronized (fe1Lock) {
                return (socketFE1 != null && !socketFE1.isClosed() && fe1SignedOn) ? socketFE1 : null;
            }
        } else if ("FE2".equals(serverName)) {
            synchronized (fe2Lock) {
                return (socketFE2 != null && !socketFE2.isClosed() && fe2SignedOn) ? socketFE2 : null;
            }
        }
        return null;
    }

    public void exit() {
        System.out.println("NetworkManager client initiating shutdown...");
        running.set(false);

        synchronized (fe1Lock) { cleanupConnectionResource("FE1", true); }
        synchronized (fe2Lock) { cleanupConnectionResource("FE2", true); }

        System.out.println("NetworkManager client shutdown complete.");
    }}