package org.example.businessLogic;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.packager.ISO87APackager;
import org.example.util.HexUtil;
import org.example.util.IsoMessagePrinter; // ✅ Import ajouté

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class TcpSender {
    private final Socket socket;

    // Constructeur qui prend un Socket
    public TcpSender(Socket socket) {
        this.socket = socket;
    }

    // Envoie un message
    public void sendMessage(ISOMsg message) {
        try {
            OutputStream out = socket.getOutputStream();

            // Empaquette le message
            byte[] data = message.pack();

            // Affiche le message complet en hexa
            System.out.println("Message ISO brut envoyé (hex) : " + HexUtil.bytesToHex(data));

            // Affiche le message avec bitmap réelle via IsoMessagePrinter
            IsoMessagePrinter.printISOMessage(message, data); // ✅ On passe aussi 'data'

            // Envoi des données via TCP
            out.write(data);
            out.flush();

        } catch (IOException | ISOException e) {
            System.err.println("Erreur lors de l'envoi du message : " + e.getMessage());
        }
    }

    private int getBitmapLength(ISOPackager packager) {
        if (packager instanceof ISO87APackager) {
            return 8; // Bitmap sur 8 octets (64 bits)
        }
        return 8;
    }

    // Reçoit un message
// Reçoit un message
    public ISOMsg receiveMessage() {
        try {
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[4096];
            int bytesRead = in.read(buffer);
            if (bytesRead > 0) {

                // --- EXTRACTION DE LA BITMAP ---
                int bitmapLength = getBitmapLength(new ISO87APackager());
                int bitmapStart = 4; // Skip 4 bytes length header

                if (bytesRead >= bitmapStart + bitmapLength) {
                    byte[] bitmapBytes = new byte[bitmapLength];
                    System.arraycopy(buffer, bitmapStart, bitmapBytes, 0, bitmapLength);
                    System.out.println("Bitmap reçue (hex) : " + HexUtil.bytesToHex(bitmapBytes));
                }

                // --- COPIE DES DONNÉES UTILES POUR UNPACK ---
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);

                // --- UNPACK ET AFFICHAGE DU MESSAGE ---
                ISOMsg response = new ISOMsg();
                response.setPackager(new ISO87APackager());
                response.unpack(data); // ✅ Appel valide

                // --- AFFICHAGE AVEC IsoMessagePrinter ---
                IsoMessagePrinter.printISOMessage(response, data); // ✅ Affiche aussi la bitmap

                return response;

            }
        } catch (IOException | ISOException e) {
            System.err.println("Erreur lors de la réception du message : " + e.getMessage());
        }
        return null;
    }
}