package org.example.network;

import java.util.logging.*;

public class PingLogger {
    private static final Logger logger = Logger.getLogger("PingLogger");

    static {
        try {
            FileHandler fh = new FileHandler("ping.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // méthode pour enregistrer un message de log
    public static void log(String message) {
        logger.info(message);
    }
}
