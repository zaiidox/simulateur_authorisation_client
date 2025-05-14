package org.example.businessLogic;

import org.example.modeles.Autorisations;
import org.example.services.AutorisationsService;
import org.example.util.IsoMessagePrinter;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

@Component
public class IsoMessageHandler {

    private final ISO87APackager packager;
    private final AutorisationsService autorisationsService;

    @Autowired
    public IsoMessageHandler(AutorisationsService autorisationsService) {
        this.packager = new ISO87APackager();
        this.autorisationsService = autorisationsService;
    }

    public ISOMsg handleIsoMessage(byte[] isoMessageBytes, Socket clientSocket) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        msg.unpack(isoMessageBytes);

        // 👇 Afficher le message reçu de manière structurée
        IsoMessagePrinter.printISOMessage(msg, "Message Reçu");

        return handleIsoMessage(msg, clientSocket);
    }

    public ISOMsg handleIsoMessage(ISOMsg isoMsg, Socket clientSocket ) throws Exception {
        isoMsg.setPackager(packager);

        // 👇 Afficher le message en cours de traitement
        IsoMessagePrinter.printISOMessage(isoMsg, "Traitement du Message");

        if ("0100".equals(isoMsg.getMTI())) {
            processAuthRequest(isoMsg, clientSocket);
        }
        return isoMsg;
    }

    private void processAuthRequest(ISOMsg isoMsg, Socket clientSocket) throws ISOException, IOException {
        String pan = isoMsg.getString(2);
        String codeTraitement = isoMsg.getString(3);
        String montant = isoMsg.getString(4);
        String dateHeure = isoMsg.getString(7);
        String stan = isoMsg.getString(11);
        String expiration = isoMsg.getString(14);
        String track2 = isoMsg.getString(35);
        String reference = isoMsg.getString(37);
        String source = "FE1";
        Autorisations autorisations = new Autorisations(stan, pan, codeTraitement, montant, dateHeure, expiration, track2, reference, source);

        autorisationsService.saveAutorisations(autorisations);

        System.out.println("Auth request for PAN=" + pan);
        IsoMessageHandler.sendAuthResponse(isoMsg, clientSocket);
    }

    public static void sendAuthResponse(ISOMsg req, Socket clientSocket) throws ISOException, IOException {
        ISOMsg resp = new ISOMsg();
        resp.setPackager(req.getPackager());
        resp.set(0, "0110");
        resp.set(2, req.getString(2));
        resp.set(3, req.getString(3));
        resp.set(4, req.getString(4));
        resp.set(7, req.getString(7));
        resp.set(11, req.getString(11));
        resp.set(14, req.getString(14));
        resp.set(35, req.getString(35));
        resp.set(37, req.getString(37));
        resp.set(39, "00");

        // 👇 Afficher la réponse avant envoi
        IsoMessagePrinter.printISOMessage(resp, "Réponse Envoyée");

        // Envoi au client
        OutputStream output = clientSocket.getOutputStream();
        byte[] data = resp.pack();
        output.write(data);
        output.flush();
    }}