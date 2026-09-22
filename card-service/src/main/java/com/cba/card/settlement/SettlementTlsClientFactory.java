package com.cba.card.settlement;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Builds and caches one mutually-authenticated {@link RestTemplate} per scheme for the HTTPS
 * settlement path.
 *
 * <p>Deliberately <em>not</em> a Spring bean and deliberately not a mutation of the shared
 * {@code backendRestTemplate}: that template is also used for balance lookups against the
 * monolith, and attaching a scheme-issued client certificate to it would present the
 * scheme's identity on unrelated internal calls.
 *
 * <p>Uses the JDK's {@link HttpClient} via {@link JdkClientHttpRequestFactory} — Spring's
 * default {@code SimpleClientHttpRequestFactory} cannot take a custom {@link SSLContext},
 * and this avoids adding an Apache HttpClient dependency for one code path.
 */
class SettlementTlsClientFactory {

    private final Map<String, RestTemplate> cache = new ConcurrentHashMap<>();

    /**
     * A {@link RestTemplate} presenting this scheme's client certificate, or {@code null}
     * when the scheme has no keystore configured (caller then uses ordinary one-way TLS —
     * the server certificate is still verified by the JDK default trust manager).
     *
     * @throws SettlementTransmissionException when a keystore IS configured but cannot be
     *         loaded. Failing closed is intentional: a scheme that mandates mTLS would
     *         reject a bearer-only request anyway, and a silent downgrade would hide the
     *         misconfiguration until a settlement window was already missed.
     */
    RestTemplate forScheme(String scheme, SettlementExportProperties.SchemeExportConfig cfg) {
        String keystorePath = cfg.getHttpsKeystorePath();
        if (keystorePath == null || keystorePath.isBlank()) {
            return null;
        }
        return cache.computeIfAbsent(scheme.toLowerCase(), key -> build(key, cfg));
    }

    private RestTemplate build(String scheme, SettlementExportProperties.SchemeExportConfig cfg) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(keyManagers(cfg), trustManagers(cfg), null);

            HttpClient httpClient = HttpClient.newBuilder()
                    .sslContext(ctx)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            return new RestTemplate(new JdkClientHttpRequestFactory(httpClient));

        } catch (SettlementTransmissionException e) {
            throw e;   // already specific — don't double-wrap
        } catch (Exception e) {
            throw new SettlementTransmissionException(
                    "Failed to initialise mutual-TLS client for scheme=" + scheme
                    + ": " + e.getMessage(), e);
        }
    }

    private javax.net.ssl.KeyManager[] keyManagers(
            SettlementExportProperties.SchemeExportConfig cfg) {
        String path = cfg.getHttpsKeystorePath();
        String type = cfg.getHttpsKeystoreType() == null || cfg.getHttpsKeystoreType().isBlank()
                ? "PKCS12" : cfg.getHttpsKeystoreType();
        char[] pass = chars(cfg.getHttpsKeystorePassword());
        try {
            KeyStore store = load(path, type, pass);
            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(store, pass);
            return kmf.getKeyManagers();
        } catch (Exception e) {
            // Names the property and the underlying cause; never echoes the password.
            throw new SettlementTransmissionException(
                    "Unable to load mutual-TLS client keystore '" + path
                    + "' (https-keystore-path): " + e.getMessage(), e);
        }
    }

    /** Null when no truststore is configured — the JDK default trust manager then applies. */
    private javax.net.ssl.TrustManager[] trustManagers(
            SettlementExportProperties.SchemeExportConfig cfg) {
        String path = cfg.getHttpsTruststorePath();
        if (path == null || path.isBlank()) {
            return null;
        }
        char[] pass = chars(cfg.getHttpsTruststorePassword());
        try {
            KeyStore store = load(path, "PKCS12", pass);
            TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(store);
            return tmf.getTrustManagers();
        } catch (Exception e) {
            throw new SettlementTransmissionException(
                    "Unable to load settlement truststore '" + path
                    + "' (https-truststore-path): " + e.getMessage(), e);
        }
    }

    private KeyStore load(String path, String type, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance(type);
        try (InputStream in = new FileInputStream(path)) {
            store.load(in, password);
        }
        return store;
    }

    private char[] chars(String s) {
        return s == null ? new char[0] : s.toCharArray();
    }
}
