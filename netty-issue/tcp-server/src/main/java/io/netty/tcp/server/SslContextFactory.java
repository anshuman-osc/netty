/*
 * Copyright (c) 2003 - 2017 Tyro Payments Limited.
 * Lv1, 155 Clarence St, Sydney NSW 2000.
 * All rights reserved.
 */
package io.netty.tcp.server;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.*;

public class SslContextFactory {

    private static String keyPassword = "changeit";
    private static String keyStorePassword = "changeit";
    private static String trustStorePassword = "changeit";

    public static SSLContext createJdkSSLContext(String trustStore, String keyStore) throws Exception {
        KeyManagerFactory kmf = initKeyManagerFactory(trustStore);
        TrustManagerFactory tmf = initTrustManagerFactory(keyStore);
        SSLContext sslc = SSLContext.getInstance("TLSv1.2");
        sslc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), SecureRandom.getInstance("SHA1PRNG"));
        return sslc;

    }

    public static SSLEngine createSslEngine(SSLContext sslContext) throws IOException, GeneralSecurityException {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);

        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setNeedClientAuth(true);
        sslParameters.setCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"});
        sslParameters.setProtocols(new String[]{"TLSv1.2"});
        sslEngine.setSSLParameters(sslParameters);
        return sslEngine;
    }

    private static TrustManagerFactory initTrustManagerFactory(String trustStore) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(loadTrustStore(trustStore));
        return tmf;
    }

    private static KeyManagerFactory initKeyManagerFactory(String keyStore) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(loadKeyStore(keyStore), keyPassword.toCharArray());
        return kmf;
    }

    private static KeyStore loadTrustStore(String file) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream fis = SslContextFactory.class.getResourceAsStream(file)) {
            ks.load(fis, trustStorePassword.toCharArray());
        }
        return ks;
    }

    private static KeyStore loadKeyStore(String file) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = SslContextFactory.class.getResourceAsStream(file)) {
            ks.load(is, keyStorePassword.toCharArray());
        }
        return ks;
    }


}
