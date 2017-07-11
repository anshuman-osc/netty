package io.netty.tcp.client;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadRunner {

    private static final Logger LOG = LoggerFactory.getLogger(LoadRunner.class);
    private static final String JKS_PASSWORD = "changeit";

    private ExecutorService executorService;
    private static String requestXml;
    private static SSLContext sslContext;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar <server_host>");
            System.exit(-1);
        }

        String host = args[0];
        int port = 8000;
        int requestCount = 1000;
        int workerCount = 5000;
        if (args.length >= 4) {
            port = Integer.valueOf(args[1]);
            workerCount = Integer.valueOf(args[2]);
            requestCount = Integer.valueOf(args[3]);
        }

        StringBuilder request = new StringBuilder();
        try {
            Path path = Paths.get(LoadRunner.class.getClassLoader().getResource("request.xml").toURI());
            Stream<String> lines = Files.lines(path);
            lines.forEach(line -> request.append(line).append("\n"));
            lines.close();
            requestXml = request.toString();
            sslContext = getSSLContext();
            LoadRunner runner = new LoadRunner();
            runner.runLoadTest(host, port, workerCount, requestCount);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            System.exit(-1);
        }
    }

    private void runLoadTest(String host, int port, int workerCount, int requestCount) throws Exception {
        executorService = Executors.newFixedThreadPool(workerCount);
        StopWatch watch = new StopWatch();
        watch.start();

        CountDownLatch connectSignal = new CountDownLatch(workerCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < workerCount; i++) {
            results.add(executorService.submit(new Worker(host, port, requestXml, requestCount, sslContext, connectSignal, startSignal)));
        }

        connectSignal.await();
        startSignal.countDown();

        long completed = 0;
        for (Future<?> result : results) {
            result.get();
            completed++;
            System.out.printf("Done: %d to go: %d%n", completed, workerCount - completed);
        }

        watch.stop();
        System.out.println("done, took: " + watch.getTime() / 1000 + "s");
        executorService.shutdownNow();
    }

    public static SSLContext getSSLContext() throws Exception {

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(loadJks(), JKS_PASSWORD.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(loadJks());

        SSLContext sslc = SSLContext.getInstance("TLSv1.2");
        sslc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), SecureRandom.getInstance("SHA1PRNG"));
        return sslc;

    }

    private static KeyStore loadJks() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = LoadRunner.class.getResourceAsStream("/client-keystore.jks")) {
            ks.load(is, JKS_PASSWORD.toCharArray());
        }
        return ks;
    }

}