package org.example;

import org.example.businessLogic.TransactionProcessor;
import org.example.network.NetworkManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class Main {

    public static void main(String[] args) throws Exception {
        // Démarre l'application Spring
        ApplicationContext context = SpringApplication.run(Main.class, args);

        // Récupère les beans gérés par Spring (TransactionProcessor et NetworkManager)
        TransactionProcessor processor = context.getBean(TransactionProcessor.class);
        NetworkManager manager = context.getBean(NetworkManager.class);

        // Injecte le processor dans le manager
        manager.setTransactionProcessor(processor);

        // Lancement du serveur dans un thread séparé
        Thread serverThread = new Thread(manager);
        serverThread.start();

        // Ajouter un hook pour arrêter le serveur proprement lors de la fermeture du programme
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Arrêt du serveur...");
            manager.exit();  // Demande l'arrêt propre du serveur
        }));
    }
}
