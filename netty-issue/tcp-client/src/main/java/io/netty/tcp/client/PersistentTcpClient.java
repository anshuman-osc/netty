package io.netty.tcp.client;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistentTcpClient {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentTcpClient.class);

    private static final int DEFAULT_TIMEOUT = 100000;

    private SSLSocket socket;

    private final String host;
    private final int port;
    private final SSLContext sslContext;

    private InputStream inputStream;
    private DataOutputStream outputStream;

    private SSLSocketFactory sslSocketFactory = null;

    private BufferedReader reader = null;

    public PersistentTcpClient(String host, int port, SSLContext sslContext) {
        this.host = host;
        this.port = port;
        this.sslContext = sslContext;
    }

    public void connect(int connectTimeout) throws IOException {
        sslSocketFactory = sslContext.getSocketFactory();
        socket = (SSLSocket) sslSocketFactory.createSocket();
        socket.setKeepAlive(Boolean.getBoolean("enableKeepAlive"));
        socket.setSoTimeout(DEFAULT_TIMEOUT);
        socket.setEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"});
        socket.setEnabledProtocols(new String[]{"TLSv1.2"});

        socket.connect(new InetSocketAddress(host, port), connectTimeout);

        inputStream = socket.getInputStream();
        outputStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 65 * 1024));

        reader = new BufferedReader(new InputStreamReader(inputStream));
    }

    public void send(String m) throws IOException {
        outputStream.write(m.getBytes());
        outputStream.flush();
    }

    public String receive() throws Exception {
        StringBuilder sb = new StringBuilder();
        while (true) {
            byte[] buffer = new byte[500];
            inputStream.read(buffer, 0, buffer.length);
            String data = new String(buffer);
            sb.append(data);
            if (sb.toString().contains("</root>")) {
                break;
            }
        }
        return sb.toString();
    }

    public void disconnect() {
        closeQuietly(socket);
        closeQuietly(inputStream);
        closeQuietly(outputStream);
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        }
    }
}
