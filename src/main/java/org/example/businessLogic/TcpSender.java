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

    public TcpSender(Socket socket) {
        this.socket = socket;
    }

    public void sendMessage(ISOMsg message) {
        try {
            OutputStream out = socket.getOutputStream();
            byte[] data = message.pack();

            System.out.println("Message ISO brut envoyé (hex) : " + HexUtil.bytesToHex(data));
            IsoMessagePrinter.printISOMessage(message, data);

            out.write(data);
            out.flush();

        } catch (IOException | ISOException e) {
            System.err.println("Erreur lors de l'envoi du message : " + e.getMessage());
        }
    }

    private int getBitmapLength(ISOPackager packager) {
        if (packager instanceof ISO87APackager) {
            return 8;
        }
        return 8;
    }


    public ISOMsg receiveMessage() {
        try {
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[4096];
            int bytesRead = in.read(buffer);
            if (bytesRead > 0) {

                // bitmap extraction
                int bitmapLength = getBitmapLength(new ISO87APackager());
                int bitmapStart = 4; // Skip 4 bytes length header

                if (bytesRead >= bitmapStart + bitmapLength) {
                    byte[] bitmapBytes = new byte[bitmapLength];
                    System.arraycopy(buffer, bitmapStart, bitmapBytes, 0, bitmapLength);
                    System.out.println("Bitmap reçue (hex) : " + HexUtil.bytesToHex(bitmapBytes));
                }

                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);

                ISOMsg response = new ISOMsg();
                response.setPackager(new ISO87APackager());
                response.unpack(data);

                IsoMessagePrinter.printISOMessage(response, data);

                return response;

            }
        } catch (IOException | ISOException e) {
            System.err.println("Erreur lors de la réception du message : " + e.getMessage());
        }
        return null;
    }
}