package org.example.util;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import java.io.InputStream;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.InputSource;
import java.io.StringWriter;
import java.io.StringReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class XmlUtil {
    public static InputStream getNonValidatingXmlStream(InputStream original) throws Exception {
        // Lire le contenu original
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(original))) {
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line);
        }

        // Parser sans validation
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(sb.toString())));

        // Convertir Document en InputStream
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return new java.io.ByteArrayInputStream(writer.toString().getBytes());
    }
}
