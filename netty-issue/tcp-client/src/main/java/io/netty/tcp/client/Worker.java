/*
 * Copyright (c) 2003 - 2017 Tyro Payments Limited.
 * Lv1, 155 Clarence St, Sydney NSW 2000.
 * All rights reserved.
 */
package io.netty.tcp.client;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Worker implements Callable<Boolean> {

    private static final Logger LOG = LoggerFactory.getLogger(Worker.class);

    private String host;
    private int port;
    private String request;
    private int requestCount;
    private SSLContext sslContext;
    private CountDownLatch connectSignal;
    private CountDownLatch startSignal;

    public Worker(String host, int port, String request, int requestCount, SSLContext sslContext, CountDownLatch connectSignal, CountDownLatch startSignal) {
        this.host = host;
        this.port = port;
        this.request = request;
        this.requestCount = requestCount;
        this.sslContext = sslContext;
        this.connectSignal = connectSignal;
        this.startSignal = startSignal;
    }

    @Override
    public Boolean call() throws Exception {
        LOG.info("Connecting");
        PersistentTcpClient client = new PersistentTcpClient(host, port, sslContext);
        client.connect(5000);

        if (connectSignal != null) {
            LOG.info("Connected");
            connectSignal.countDown();
        }
        if (startSignal != null) {
            boolean ready = startSignal.await(10000, SECONDS);
            if (!ready) {
                LOG.info("Timed out waiting for start startSignal");
                return null;
            }
        }

        try {
            for (int i = 0; i < requestCount; i++) {
                String req = request.replaceAll("REPLACE_DATE", String.valueOf(System.currentTimeMillis()));
                client.send(req);
                String response = client.receive();
                LOG.debug("Received: {}", response);
                TimeUnit.MILLISECONDS.sleep(1000);
            }
        } catch (Exception e) {
            LOG.warn("Request failed", e);
            return false;
        } finally {
            client.disconnect();
        }

        return true;
    }
}
