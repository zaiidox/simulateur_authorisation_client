package org.example.network;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;
import org.jpos.iso.ISOException;

import java.io.IOException;
import java.io.OutputStream;

public class IsoMessageManager {

    public static void sendSignOn(OutputStream out) throws ISOException, IOException {
        ISOMsg signOn = new ISOMsg();
        signOn.setPackager(new ISO87APackager());
        signOn.setMTI("0800");
        signOn.set(70, "001"); // Code de Sign-On
        out.write(signOn.pack());
        out.flush();
        System.out.println("Sign-On envoyé.");
    }

    public static void sendSignOff(OutputStream out) throws ISOException, IOException {
        ISOMsg signOff = new ISOMsg();
        signOff.setPackager(new ISO87APackager());
        signOff.setMTI("0800");
        signOff.set(70, "002"); // Code de Sign-Off
        out.write(signOff.pack());
        out.flush();
        System.out.println("Sign-Off envoyé.");
    }
}
