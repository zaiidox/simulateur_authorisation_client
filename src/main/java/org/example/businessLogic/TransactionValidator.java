package org.example.businessLogic;

import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionValidator {
    private static final Logger logger = LoggerFactory.getLogger(TransactionValidator.class);

    public boolean validateTransaction(ISOMsg transactionRequest) {
        return validateCardNumber(transactionRequest.getString(2)) &&
                validateProcessingCode(transactionRequest.getString(3)) &&
                validateAmount(transactionRequest.getString(4)) &&
                validateDateTime(transactionRequest.getString(7)) &&
                validateSTAN(transactionRequest.getString(11)) &&
                validateExpiryDate(transactionRequest.getString(14)) &&
                validateTrack2(transactionRequest.getString(35)) &&
                validateReferenceNumber(transactionRequest.getString(37));
    }

    private boolean validateCardNumber(String cardNumber) {
        if (cardNumber == null || !cardNumber.matches("\\d{13,19}")) {
            logger.error("Numéro de carte invalide (doit contenir 13 à 19 chiffres) : {}", cardNumber);
            return false;
        }
        logger.debug("Numéro de carte valide");
        return true;
    }

    private boolean validateProcessingCode(String code) {
        if (code == null || !code.matches("\\d{6}")) {
            logger.error("Code de traitement invalide (doit contenir 6 chiffres) : {}", code);
            return false;
        }
        logger.debug("Code de traitement valide");
        return true;
    }

    private boolean validateAmount(String amount) {
        if (amount == null || !amount.matches("\\d{1,12}")) {
            logger.error("Montant invalide (jusqu'à 12 chiffres, sans décimales) : {}", amount);
            return false;
        }
        logger.debug("Montant valide");
        return true;
    }

    private boolean validateDateTime(String dateTime) {
        if (dateTime == null || !dateTime.matches("\\d{10}")) {
            logger.error("Date/Heure invalide (doit être au format MMDDhhmmss) : {}", dateTime);
            return false;
        }
        logger.debug("Date/Heure valide");
        return true;
    }

    private boolean validateSTAN(String stan) {
        if (stan == null || !stan.matches("\\d{1,6}")) {
            logger.error("STAN invalide (jusqu'à 6 chiffres) : {}", stan);
            return false;
        }
        logger.debug("STAN valide");
        return true;
    }

    private boolean validateExpiryDate(String expiry) {
        if (expiry == null || !expiry.matches("\\d{4}")) {
            logger.error("Date d'expiration invalide (doit être au format MMYY) : {}", expiry);
            return false;
        }
        logger.debug("Date d'expiration valide");
        return true;
    }

    private boolean validateTrack2(String track2) {
        if (track2 == null || !track2.matches("[\\d=]+")) {
            logger.error("Données Track2 invalides (doivent contenir uniquement chiffres et symbole '=') : {}", track2);
            return false;
        }
        logger.debug("Track2 valide");
        return true;
    }

    private boolean validateReferenceNumber(String ref) {
        if (ref == null || ref.isEmpty() || ref.length() > 12) {
            logger.error("Référence invalide (ne doit pas dépasser 12 caractères) : {}", ref);
            return false;
        }
        logger.debug("Référence valide");
        return true;
    }
}