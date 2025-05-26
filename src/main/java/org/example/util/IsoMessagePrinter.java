package org.example.util;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;

public class IsoMessagePrinter {

    /**
     * Affiche un message ISO avec sa MTI, sa bitmap (si possible),
     * et les champs présents avec leurs significations.
     */
    public static void printISOMessage(ISOMsg msg, byte[] rawData) {
        if (msg == null) {
            System.out.println("Message ISO est null");
            return;
        }

        try {
            System.out.println("=== Message ISO ===");
            System.out.println("MTI              : " + msg.getMTI());

            // Extraction de la bitmap à partir des données brutes
            String bitmapHex = extractBitmapFromRawData(rawData);
            if (bitmapHex != null) {
                System.out.println("Bitmap           : " + bitmapHex);
            } else {
                System.out.println("Bitmap           : [non trouvée]");
            }

            // Affichage des champs présents avec leur signification
            for (int i = 2; i <= 128; i++) {
                if (msg.hasField(i)) {
                    String fieldName = getFieldName(i);
                    System.out.printf("Champ %-3d (%-22s): %s%n", i, fieldName, msg.getString(i));
                }
            }

            System.out.println("==================");

        } catch (ISOException e) {
            System.err.println("Erreur lors de l'affichage du message ISO : " + e.getMessage());
        }
    }

    private static String extractBitmapFromRawData(byte[] data) {
        if (data == null || data.length < 12) return null;
        byte[] bitmapBytes = new byte[8];
        System.arraycopy(data, 4, bitmapBytes, 0, 8); // skip 4 bytes length header
        return HexUtil.bytesToHex(bitmapBytes);
    }

    private static String getFieldName(int fieldNumber) {
        return switch (fieldNumber) {
            case 2 -> "Numéro de carte (PAN)";
            case 3 -> "Code traitement";
            case 4 -> "Montant";
            case 7 -> "Date/Heure";
            case 11 -> "STAN";
            case 12 -> "Heure locale";
            case 13 -> "Date locale";
            case 14 -> "Expiration";
            case 17 -> "Date capture";
            case 18 -> "Code MCC";
            case 22 -> "Mode d'entrée carte";
            case 24 -> "NII (Network Intl. ID)";
            case 25 -> "Code réponse";
            case 27 -> "Longueur code approbation";
            case 32 -> "ID acquéreur";
            case 35 -> "Piste 2";
            case  39 -> "Code Réponse";
            case 37 -> "Référence";
            case 41 -> "ID terminal";
            case 42 -> "ID commerçant";
            case 43 -> "Nom commerçant";
            case 48 -> "Données additionnelles";
            case 49 -> "Code devise";
            case 60 -> "Type terminal";
            case 61 -> "Émetteur carte";
            case 63 -> "Données libres";
            case 121 -> "Indicateur autorisation (AuthCharInd)";
            case 123 -> "Code données POS";
            case 126 -> "Champ privé";
            default -> "Champ inconnu";
        };
    }
}
