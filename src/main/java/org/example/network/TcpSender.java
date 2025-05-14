// client/TcpSender.java
package org.example.network;

import java.io.OutputStream;
import java.net.Socket;

public class TcpSender {
    private final String host;
    private final int port;

    public TcpSender(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void send(byte[] message) throws Exception {
        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream()) {
            out.write(message);
            out.flush();
        }
    }
}
