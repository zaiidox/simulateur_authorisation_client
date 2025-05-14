package org.example.network;

import lombok.Setter;
import org.example.businessLogic.TransactionProcessor;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NetworkManager implements Runnable {
    private static final int PORT = 5000;
    private static final ConcurrentHashMap<Socket, Boolean> clientsSignOnStatus = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Socket, Long> lastActivityMap = new ConcurrentHashMap<>();
    @Setter
    private TransactionProcessor transactionProcessor;
    private boolean running = true;

    public void exit() {
        running = false;
        System.out.println("NetworkManager shutdown triggered.");
    }

    public void sendResponse(Socket clientSocket, byte[] data) throws IOException {
        OutputStream out = clientSocket.getOutputStream();
        out.write(data);
        out.flush();
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Network Management Server started on port " + PORT);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (Exception e) {
            System.err.println("Erreur dans NetworkManager: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (InputStream in = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {

            byte[] buffer = new byte[4096];

            while (running && !clientSocket.isClosed()) {
                if (in.available() > 0) {
                    int bytesRead = in.read(buffer);
                    if (bytesRead == -1) break;

                    byte[] actualData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, actualData, 0, bytesRead);

                    ISOMsg isoMsg = new ISOMsg();
                    isoMsg.setPackager(new ISO87APackager());
                    isoMsg.unpack(actualData);

                    String mti = isoMsg.getMTI();
                    String networkCode = isoMsg.hasField(70) ? isoMsg.getString(70) : "";

                    // Mettre à jour l'activité du client
                    lastActivityMap.put(clientSocket, System.currentTimeMillis());

                    if ("0800".equals(mti)) {
                        ISOMsg response = new ISOMsg();
                        response.setPackager(new ISO87APackager());
                        response.setMTI("0810");
                        response.set(39, "00");
                        response.set(70, networkCode);

                        switch (networkCode) {
                            case "001": // Sign-On
                                clientsSignOnStatus.put(clientSocket, true);
                                System.out.println("Sign-On reçu de : " + clientSocket.getRemoteSocketAddress());
                                break;
                            case "002": // Sign-Off
                                clientsSignOnStatus.put(clientSocket, false);
                                System.out.println("Sign-Off reçu de : " + clientSocket.getRemoteSocketAddress());
                                break;
                            case "301": // Ping
                                System.out.println("EchoTest reçu de : " + clientSocket.getRemoteSocketAddress());
                                break;
                        }

                        sendResponse(clientSocket, response.pack());
                        System.out.println("Réponse 0810 envoyée.");

                        if ("002".equals(networkCode)) break;

                    } else {
                        // Déléguer le message transactionnel au processor
                        if (transactionProcessor != null) {
                            transactionProcessor.processTransaction(actualData, clientSocket);  // Passer le clientSocket ici
                        } else {
                            System.out.println("Aucun TransactionProcessor défini.");
                        }
                    }
                }

                Thread.sleep(200);  // Attend un peu avant de vérifier à nouveau
            }

        } catch (Exception e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } finally {
            // Nettoyage lorsque le client est déconnecté
            clientsSignOnStatus.remove(clientSocket);
            lastActivityMap.remove(clientSocket);
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }
}
