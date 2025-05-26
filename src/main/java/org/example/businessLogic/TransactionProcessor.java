package org.example.businessLogic;

import org.example.network.NetworkManager;
import org.jpos.iso.ISOMsg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.Socket;

public class TransactionProcessor {
    private final NetworkManager networkManager;
    private final TransactionValidator validator = new TransactionValidator();
    private final String[] templateFields;
    private int referenceCounter;  // pour incrémenter la référence
    private boolean sendToFE1Next = true;


    public TransactionProcessor(NetworkManager networkManager, String csvTemplatePath, int startingReference) throws Exception {
        this.networkManager = networkManager;
        this.templateFields = loadTemplate(csvTemplatePath);
        if (templateFields.length < 28) {
            throw new IllegalArgumentException("Le template CSV doit contenir au moins 28 champs");
        }
        this.referenceCounter = startingReference;
    }

    public void startContinuousSend() {
        while (true) {
            try {
                // Générer la référence incrémentée, formatée sur 12 chiffres par exemple
                String currentReference = String.format("%012d", referenceCounter++);

                // Copier les champs du template
                String[] champs = new String[28];
                System.arraycopy(templateFields, 0, champs, 0, 28);

                // Remplacer la référence (champ 37 ISO, index 7)
                champs[16] = currentReference;

                // Optionnel : garder STAN fixe, ou à gérer ici si besoin

                ISOMsg msg = IsoMessageBuilder.createAuthRequest(champs);

                if (!validator.validateTransaction(msg)) {
                    System.err.println("Validation échouée. Message non envoyé.");
                    continue;
                }

                boolean ok = sendToFE1Next ? trySend(msg, "FE1") || trySend(msg, "FE2")
                        : trySend(msg, "FE2") || trySend(msg, "FE1");

                if (ok) {
                    sendToFE1Next = !sendToFE1Next;
                } else {
                    System.err.println("Échec de l'envoi à FE1 et FE2.");
                }

                Thread.sleep(2000);

            } catch (Exception e) {
                System.err.println("Erreur : " + e.getMessage());
            }
        }
    }

    private String[] loadTemplate(String filePath) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            reader.readLine(); // ignore header
            String line = reader.readLine();
            if (line == null) throw new IllegalArgumentException("Le fichier template est vide.");
            String[] fields = line.split(",");
            if (fields.length < 28) {
                throw new IllegalArgumentException("Le fichier template doit contenir au moins 28 champs.");
            }
            return fields;
        }
    }

    private boolean trySend(ISOMsg msg, String serverName) {
        try {
            Socket socket = networkManager.getSocketForServer(serverName);
            if (socket == null) {
                System.err.println("Socket pour " + serverName + " non disponible.");
                return false;
            }

            TcpSender sender = new TcpSender(socket);
            sender.sendMessage(msg);
            ISOMsg response = sender.receiveMessage();

            System.out.println(">> Réponse reçue de " + serverName + " pour Référence " + msg.getString(37));
            return response != null;
        } catch (Exception e) {
            System.err.println("Erreur envoi à " + serverName + ": " + e.getMessage());
            return false;
        }
    }
}
