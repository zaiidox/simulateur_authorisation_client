// src/main/java/org/example/businessLogic/IsoMessageBuilder.java
package org.example.businessLogic;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;

public class IsoMessageBuilder {


    public static ISOMsg createAuthRequest(String[] fields) throws Exception {
        ISO87APackager packager = new ISO87APackager();

        ISOMsg isoMsg = new ISOMsg();
        isoMsg.setPackager(packager);

        isoMsg.setMTI("0100");
        isoMsg.set(2,  fields[0]);
        isoMsg.set(3,  fields[1]);
        isoMsg.set(4,  fields[2]);
        isoMsg.set(7,  fields[3]);
        isoMsg.set(11, fields[4]);

        isoMsg.set(14, fields[5]);

        isoMsg.set(35, fields[6]);
        isoMsg.set(37, fields[7]);

        return isoMsg;
    }
}
