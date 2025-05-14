package org.example.network;

import java.util.logging.*;

public class PingLogger {
    private static final Logger logger = Logger.getLogger("PingLogger");

    static {
        try {
            // Créer un FileHandler pour enregistrer les logs dans un fichier
            FileHandler fh = new FileHandler("ping.log", true);
            // Définir le format des logs
            fh.setFormatter(new SimpleFormatter());
            // Ajouter le handler au logger
            logger.addHandler(fh);
            // Empêcher l'affichage en console
            logger.setUseParentHandlers(false);
        } catch (Exception e) {
            // Si une erreur survient lors de la configuration du logger
            e.printStackTrace();
        }
    }

    // Méthode pour enregistrer un message de log
    public static void log(String message) {
        logger.info(message);  // Utilisation de info, mais vous pouvez changer le niveau de log si nécessaire (WARNING, SEVERE, etc.)
    }
}
