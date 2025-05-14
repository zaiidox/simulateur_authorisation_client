// src/main/java/org/example/IsoClient.java
package org.example;

import org.example.businessLogic.TransactionProcessor;
import org.example.network.NetworkManager;

public class IsoClient {

    public static void main(String[] args) {
        NetworkManager manager = new NetworkManager();
        TransactionProcessor processor = new TransactionProcessor(manager);
        System.out.println("Démarrage du client ISO 8583...");
        manager.start();
        processor.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Arrêt du client...");
            manager.exit();

            System.out.println("Client arrêté.");
        }, "ShutdownHook"));

        System.out.println("Boucle principale terminée.");
    }
}