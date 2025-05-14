package org.example.util;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;

public class IsoMessagePrinter {

    public static void printISOMessage(ISOMsg msg, String title) {
        if (msg == null) {
            System.out.println(title + " : Message ISO est null");
            return;
        }

        try {
            System.out.println("=== " + title + " ===");
            System.out.println("MTI              : " + msg.getMTI());

            byte[] data = msg.pack();
            String bitmapHex = extractBitmapFromRawData(data);

            if (bitmapHex != null) {
                System.out.println("Bitmap           : " + bitmapHex);
            } else {
                System.out.println("Bitmap           : [non trouvée]");
            }

            // Boucle sur tous les champs présents
            for (int i = 2; i <= 128; i++) {
                if (msg.hasField(i)) {
                    String fieldName = getFieldName(i);
                    System.out.printf("Champ %-3d (%-20s): %s%n", i, fieldName, msg.getString(i));
                }
            }
            System.out.println("==============================");

        } catch (ISOException e) {
            System.err.println("Erreur lors de l'affichage du message : " + e.getMessage());
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