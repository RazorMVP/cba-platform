package com.cba.card.settlement;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies mutual TLS on the HTTPS settlement path (Afrigo/PAPSS) against an in-process
 * {@link HttpsServer} configured with {@code setNeedClientAuth(true)} — i.e. a server that
 * <em>rejects the handshake</em> unless the client presents a certificate.
 *
 * <p>This is deliberately a real handshake rather than an assertion about an {@code SSLContext}
 * object: the only way to know mTLS is wired is for a server to demand a client certificate
 * and for the transmission to succeed anyway.
 *
 * <p>Test PKI is generated at runtime with the JDK's {@code keytool} (card-service ships
 * {@code bcprov} but not {@code bcpkix}, so there is no in-process X.509 builder available).
 * One self-signed certificate plays three roles — server identity, client identity, and the
 * trust anchor for both sides — which keeps the fixture to a single keystore.
 */
@DisplayName("SettlementFileTransmitter (HTTPS) — mutual TLS against a client-auth-requiring server")
class SettlementFileTransmitterMtlsTest {

    private static final String STORE_PASS = "changeit";
    private static String keystorePath;
    private static HttpsServer server;
    private static final AtomicReference<byte[]> RECEIVED = new AtomicReference<>();

    @BeforeAll
    static void startMtlsServer() throws Exception {
        File ks = File.createTempFile("cba_mtls_", ".p12");
        //noinspection ResultOfMethodCallIgnored
        ks.delete();                 // keytool must create it
        ks.deleteOnExit();
        keystorePath = ks.getAbsolutePath();

        Process p = new ProcessBuilder(
                "keytool", "-genkeypair",
                "-alias", "cba", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
                "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-keystore", keystorePath, "-storetype", "PKCS12",
                "-storepass", STORE_PASS, "-keypass", STORE_PASS)
                .redirectErrorStream(true).start();
        if (p.waitFor() != 0) {
            throw new IllegalStateException("keytool failed: "
                    + new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }

        SSLContext ctx = contextFrom(keystorePath);
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ctx) {
            @Override public void configure(HttpsParameters params) {
                SSLParameters sp = ctx.getDefaultSSLParameters();
                sp.setNeedClientAuth(true);      // ← the whole point: client cert mandatory
                params.setSSLParameters(sp);
            }
        });
        server.createContext("/settle", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                RECEIVED.set(in.readAllBytes());
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    /** KeyManagers + TrustManagers both from the single self-signed keystore. */
    private static SSLContext contextFrom(String path) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = new java.io.FileInputStream(path)) {
            store.load(in, STORE_PASS.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(store, STORE_PASS.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

    private SettlementExportProperties propsForAfrigo(boolean withClientCert, String keystoreOverride) {
        SettlementExportProperties.SchemeExportConfig cfg = new SettlementExportProperties.SchemeExportConfig();
        cfg.setEnabled(true);
        cfg.setHttpsEndpoint("https://localhost:" + server.getAddress().getPort() + "/settle");
        cfg.setHttpsApiKey("test-bearer");
        cfg.setHttpsTruststorePath(keystorePath);
        cfg.setHttpsTruststorePassword(STORE_PASS);
        if (withClientCert) {
            cfg.setHttpsKeystorePath(keystoreOverride != null ? keystoreOverride : keystorePath);
            cfg.setHttpsKeystorePassword(STORE_PASS);
        }
        SettlementExportProperties props = new SettlementExportProperties();
        props.getSchemes().put("afrigo", cfg);
        return props;
    }

    @Test
    @DisplayName("transmit() completes against an mTLS-requiring endpoint when a client keystore is configured")
    void transmitsWithMutualTls() {
        RECEIVED.set(null);
        SettlementFileTransmitter transmitter =
                new SettlementFileTransmitter(propsForAfrigo(true, null), new RestTemplate());

        byte[] payload = "{\"batch\":\"PAPSS-20260918\",\"count\":1}".getBytes(StandardCharsets.UTF_8);
        transmitter.transmit(payload, "papss_20260918.json", "afrigo", "HTTPS");

        assertThat(RECEIVED.get())
                .as("server must have received the settlement payload over a mutually-authenticated channel")
                .isEqualTo(payload);
    }

    @Test
    @DisplayName("transmit() fails closed when a client keystore is configured but cannot be loaded")
    void failsClosedOnUnloadableKeystore() {
        SettlementFileTransmitter transmitter = new SettlementFileTransmitter(
                propsForAfrigo(true, "/nonexistent/path/to/client.p12"), new RestTemplate());

        assertThatThrownBy(() -> transmitter.transmit(
                    "x".getBytes(StandardCharsets.UTF_8), "f.json", "afrigo", "HTTPS"))
                .isInstanceOf(SettlementTransmissionException.class)
                // Must name the misconfiguration rather than surfacing a bare SSL error,
                // and must NOT silently downgrade to bearer-only.
                .hasMessageContaining("keystore");
    }

    @Test
    @DisplayName("an mTLS-requiring endpoint rejects a client with no certificate (guards the test's own premise)")
    void rejectsClientWithoutCertificate() {
        SettlementFileTransmitter transmitter =
                new SettlementFileTransmitter(propsForAfrigo(false, null), new RestTemplate());

        assertThatThrownBy(() -> transmitter.transmit(
                    "x".getBytes(StandardCharsets.UTF_8), "f.json", "afrigo", "HTTPS"))
                .as("if this passes, the server is not actually requiring client auth and "
                    + "transmitsWithMutualTls() would prove nothing")
                .isInstanceOf(SettlementTransmissionException.class);
    }
}
