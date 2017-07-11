package io.netty.tcp.client;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketException;

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

    private DataInputStream inputStream;
    private DataOutputStream outputStream;

    private Object serverInLock = new Object();

    private Object outLock = new Object();
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

        synchronized (serverInLock) {
            inputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        }
        synchronized (outLock) {
            outputStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 2048));
        }
    }

    public void send(String m) throws IOException {
        synchronized (outLock) {
            outputStream.write(m.getBytes());
            outputStream.flush();
        }
    }

    public String receive() throws Exception {
        StringBuilder sb = new StringBuilder();
        synchronized (serverInLock) {
            int i = 0;
            while (reader != null) {
                String s = reader.readLine();
                if (s == null) {
                    throw new EOFException();
                }
                sb.append(s);
                if (s.contains("<root>")) {
                    i++;
                }
                if (s.contains("</root>")) {
                    if (--i <= 0) {
                        break;
                    }
                }
            }
        }
        return sb.toString();
    }

    public void disconnect() {
        try {
            try {
                socket.shutdownOutput();
            } catch (SocketException e) {
                LOG.error(e.getMessage(), e);
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
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
