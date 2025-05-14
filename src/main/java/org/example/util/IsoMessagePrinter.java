// src/main/java/org/example/util/IsoMessagePrinter.java

package org.example.util;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;

public class IsoMessagePrinter {

    /**
     * Affiche un message ISO avec sa MTI, sa bitmap (si possible),
     * et les champs présents.
     */
    public static void printISOMessage(ISOMsg msg, byte[] rawData) {
        if (msg == null) {
            System.out.println("Message ISO est null");
            return;
        }

        try {
            System.out.println("=== Message ISO ===");
            System.out.println("MTI              : " + msg.getMTI());

            // --- EXTRACTION DE LA BITMAP À PARTIR DES DONNÉES BRUTES ---
            String bitmapHex = extractBitmapFromRawData(rawData);
            if (bitmapHex != null) {
                System.out.println("Bitmap           : " + bitmapHex);
            } else {
                System.out.println("Bitmap           : [non trouvée]");
            }

            // --- AFFICHAGE DES CHAMPS PRÉSENTS ---
            for (int i = 2; i <= 128; i++) {
                if (msg.hasField(i)) {
                    String fieldName = getFieldName(i);
                    System.out.printf("Champ %-3d (%-18s): %s%n", i, fieldName, msg.getString(i));
                }
            }
            System.out.println("==================");

        } catch (ISOException e) {
            System.err.println("Erreur lors de l'affichage du message ISO : " + e.getMessage());
        }
    }

    private static String extractBitmapFromRawData(byte[] data) {
        if (data == null || data.length < 12) return null; // header (4) + bitmap (8)

        byte[] bitmapBytes = new byte[8];
        System.arraycopy(data, 4, bitmapBytes, 0, 8); // skip 4 bytes length header

        return HexUtil.bytesToHex(bitmapBytes);
    }

    private static String getFieldName(int fieldNumber) {
        return switch (fieldNumber) {
            case 2 -> "Numéro de carte";
            case 3 -> "Code traitement";
            case 4 -> "Montant";
            case 7 -> "Date/Heure";
            case 11 -> "STAN";
            case 14 -> "Expiration";
            case 35 -> "Track2";
            case 37 -> "Référence";
            case 39 -> "Code réponse";
            default -> "";
        };
    }
}