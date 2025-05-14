// iso/IsoMessageBuilder.java
package org.example.iso;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;

import java.util.HashMap;
import java.util.Map;

public class IsoMessageBuilder {
    public static ISOMsg createAuthorizationRequest(Map<Integer, String> fields) throws Exception {
        ISOMsg isoMessage = new ISOMsg();
        isoMessage.setPackager(new ISO87APackager());
        isoMessage.setMTI("0200");

        for (Map.Entry<Integer, String> entry : fields.entrySet()) {
            isoMessage.set(entry.getKey(), entry.getValue());
        }

        return isoMessage;
    }

    public static ISOMsg createAuthRequest(String[] champs) throws Exception {
        Map<Integer, String> fields = new HashMap<>();
        fields.put(2, champs[0]);
        fields.put(3, champs[1]);
        fields.put(4, champs[2]);
        fields.put(7, champs[3]);
        fields.put(11, champs[4]);
        fields.put(14, champs[5]);
        fields.put(35, champs[6]);
        fields.put(37, champs[7]);
        return createAuthorizationRequest(fields);
    }
    // Fonction pour afficher le message ISO 8583 sous format buffer
    public static void dumpBuffer(byte[] message) {
        System.out.println("==== Message en format Buffer ====");
        StringBuilder sb = new StringBuilder();
        int lineLength = 16; // Limite de caractères par ligne pour plus de lisibilité

        // Affichage du message en buffer
        for (int i = 0; i < message.length; i++) {
            if (i > 0 && i % lineLength == 0) {
                sb.append("\n");  // Nouvelle ligne après chaque 16 octets pour une meilleure lisibilité
            }
            sb.append(String.format("%02X ", message[i]));  // Affichage de chaque byte en hexadécimal
        }
        System.out.println(sb.toString());}

}
