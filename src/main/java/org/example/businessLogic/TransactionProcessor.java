package org.example.businessLogic;

import org.jpos.iso.ISOMsg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.Socket;

@Service
public class TransactionProcessor {

    private final IsoMessageHandler handler;

    @Autowired
    public TransactionProcessor(IsoMessageHandler handler) {
        this.handler = handler;
    }

    public void processTransaction(byte[] isoMsgBytes, Socket clientSocket) {
        try {
            handler.handleIsoMessage(isoMsgBytes, clientSocket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
