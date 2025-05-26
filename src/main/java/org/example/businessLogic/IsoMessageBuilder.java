package org.example.businessLogic;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;

public class IsoMessageBuilder {


    public static ISOMsg createAuthRequest(String[] fields) throws Exception {
        if (fields == null || fields.length < 28) {
            throw new IllegalArgumentException("Le tableau de champs doit contenir au moins 28 éléments");
        }

        ISO87APackager packager = new ISO87APackager();

        ISOMsg isoMsg = new ISOMsg();
        isoMsg.setPackager(packager);

        isoMsg.setMTI("0100");

        isoMsg.set(2,  fields[0]);  // PAN
        isoMsg.set(3,  fields[1]);  // Code traitement
        isoMsg.set(4,  fields[2]);  // Montant
        isoMsg.set(7,  fields[3]);  // Date/heure
        isoMsg.set(11, fields[4]);  // STAN
        isoMsg.set(12, fields[5]);  // Heure locale
        isoMsg.set(13, fields[6]);  // Date locale
        isoMsg.set(14, fields[7]);  // Expiration
        isoMsg.set(17, fields[8]);  // Date capture
        isoMsg.set(18, fields[9]);  // MCC
        isoMsg.set(22, fields[10]); // Mode entrée
        isoMsg.set(24, fields[11]); // NII
        isoMsg.set(25, fields[12]); // Code réponse
        isoMsg.set(27, fields[13]); // Longueur code approbation
        isoMsg.set(32, fields[14]); // ID acquéreur
        isoMsg.set(35, fields[15]); // Track 2
        isoMsg.set(37, fields[16]); // Référence
        isoMsg.set(41, fields[17]); // ID terminal
        isoMsg.set(42, fields[18]); // ID commerçant
        isoMsg.set(43, fields[19]); // Nom commerçant
        isoMsg.set(48, fields[20]); // Données additionnelles
        isoMsg.set(49, fields[21]); // Code devise
        isoMsg.set(60, fields[22]); // Type terminal
        isoMsg.set(61, fields[23]); // Émetteur carte
        isoMsg.set(63, fields[24]); // Données libres
        isoMsg.set(121, fields[25]); // Champ 121
        isoMsg.set(123, fields[26]); // Champ 123
        isoMsg.set(126, fields[27]); // Champ 126

        return isoMsg;
    }
}
