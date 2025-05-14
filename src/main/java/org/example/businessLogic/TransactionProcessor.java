// src/main/java/org/example/businessLogic/TransactionProcessor.java
package org.example.businessLogic;

import org.example.network.NetworkManager;
import org.jpos.iso.ISOMsg;
import org.example.businessLogic.TransactionValidator;
import java.net.Socket;
import java.util.Scanner;

public class TransactionProcessor {
    private final NetworkManager networkManager;
    private boolean sendToFE1Next = true;
    private final TransactionValidator validator = new TransactionValidator();

    public TransactionProcessor(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    /** Démarre la boucle interactive de saisie et d'envoi des transactions */
    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("==== CLIENT DE TRANSACTIONS ISO 8583 ====");
        while (true) {
            System.out.print("Tapez 'auth' pour envoyer une demande, ou 'exit' pour quitter: ");
            String cmd = scanner.nextLine();
            if ("exit".equalsIgnoreCase(cmd)) break;
            if (!"auth".equalsIgnoreCase(cmd)) continue;

            // Lecture des champs
            String[] champs = new String[8];
            System.out.print("Numéro de carte (champ 2): ");    champs[0] = scanner.nextLine();
            System.out.print("Code traitement (champ 3): ");   champs[1] = scanner.nextLine();
            System.out.print("Montant (champ 4, ex: 000000010000): "); champs[2] = scanner.nextLine();
            System.out.print("Date/Heure (champ 7, MMDDhhmmss): ");   champs[3] = scanner.nextLine();
            System.out.print("STAN (champ 11): ");              champs[4] = scanner.nextLine();
            System.out.print("Expiration (champ 14, MMYY): ");  champs[5] = scanner.nextLine();
            System.out.print("Track2 (champ 35): ");            champs[6] = scanner.nextLine();
            System.out.print("Référence (champ 37): ");         champs[7] = scanner.nextLine();

            try {
                ISOMsg msg = IsoMessageBuilder.createAuthRequest(champs);

                if (!validator.validateTransaction(msg)) {
                    System.err.println("Validation échouée. Message non envoyé.");
                    continue;
                }

                boolean ok;
                if (sendToFE1Next) {
                    ok = trySend(msg, "FE1");
                    if (!ok) ok = trySend(msg, "FE2");
                } else {
                    ok = trySend(msg, "FE2");
                    if (!ok) ok = trySend(msg, "FE1");
                }

                if (ok) {
                    sendToFE1Next = !sendToFE1Next;
                } else {
                    System.err.println("Échec de l'envoi à FE1 et FE2.");
                }

            } catch (Exception e) {
                System.err.println("Erreur: " + e.getMessage());
            }
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

            // Envoi du message ISO
            sender.sendMessage(msg);
            System.out.println(">> Réponse reçue de " + serverName + ":");
            // Réception de la réponse ISO
            ISOMsg response = sender.receiveMessage();

            // ✅ La méthode s'arrête ici
            return response != null;

        } catch (Exception e) {
            System.err.println("Erreur envoi à " + serverName + ": " + e.getMessage());
            return false;
        }
    }}