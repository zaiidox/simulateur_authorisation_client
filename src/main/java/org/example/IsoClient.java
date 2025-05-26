package org.example;

import org.example.businessLogic.TransactionProcessor;
import org.example.network.NetworkManager;

public class IsoClient {

    public static void main(String[] args) {
        try {
            String templateFilePath = "src/main/resources/auth_template.txt"; // chemin vers le fichier de template
            int startingStan = 1; // STAN initial

            NetworkManager manager = new NetworkManager();
            TransactionProcessor processor = new TransactionProcessor(manager, templateFilePath, startingStan);

            System.out.println("Démarrage du client ISO 8583...");
            manager.start();
            Thread.sleep(2000);
            // Envoi automatique et continu à partir du template
            processor.startContinuousSend();

            // Hook d'arrêt propre
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Arrêt du client...");
                manager.exit();
                System.out.println("Client arrêté.");
            }, "ShutdownHook"));

        } catch (Exception e) {
            System.err.println("Erreur au démarrage du client : " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Boucle principale terminée.");
    }
}
