package com.cba.card.settlement;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.util.Properties;

/**
 * Infrastructure layer: transmits settlement files to scheme clearinghouse networks.
 *
 * <p>Supports two transmission protocols:
 * <ul>
 *   <li><b>SFTP</b> — JSch-based public-key authenticated SFTP for Visa, Mastercard,
 *       Verve, and UnionPay. All scheme SFTP endpoints require key-based authentication;
 *       password auth is explicitly refused by scheme network firewalls.</li>
 *   <li><b>HTTPS</b> — Spring RestTemplate POST for REST-based clearinghouses (Afrigo/PAPSS).
 *       Bearer token authentication; mutual TLS should be added at production via
 *       a custom {@code SSLContext} loaded from a scheme-provided PKCS12 keystore.</li>
 * </ul>
 *
 * <h3>Production hardening checklist</h3>
 * <ul>
 *   <li>✅ SFTP: host key pinned via {@code sftp-known-hosts-path} / {@code -entry} with
 *       {@code StrictHostKeyChecking=yes}. Fails closed when unpinned.</li>
 *   <li>✅ HTTPS: mutual TLS via {@code https-keystore-path} (see
 *       {@link SettlementTlsClientFactory}). Optional per scheme; fails closed when the
 *       keystore is configured but unloadable.</li>
 *   <li>☐ SFTP: load private key from a secure vault (currently a filesystem path)</li>
 *   <li>☐ Both: wrap with a circuit breaker (Resilience4j) for scheme network outages</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementFileTransmitter {

    private final SettlementExportProperties props;

    /** Shared client used for schemes that do not configure a client certificate. */
    private final RestTemplate restTemplate;

    /**
     * Per-scheme mutual-TLS clients. A plain field rather than an injected bean so the
     * scheme client certificate can never leak onto {@code backendRestTemplate}, which
     * also serves balance lookups against the monolith.
     */
    private final SettlementTlsClientFactory tlsClients = new SettlementTlsClientFactory();

    /**
     * Transmit a settlement file using the method appropriate for the scheme.
     *
     * @param fileBytes  raw file bytes from {@link SettlementFileExporter#export}
     * @param fileName   scheme-mandated filename
     * @param scheme     target scheme (used to load per-scheme config)
     * @param method     "SFTP" or "HTTPS"
     * @throws SettlementTransmissionException on any I/O or protocol failure (retryable)
     */
    public void transmit(byte[] fileBytes, String fileName, String scheme, String method) {
        if ("HTTPS".equalsIgnoreCase(method)) {
            transmitHttps(fileBytes, fileName, scheme);
        } else {
            transmitSftp(fileBytes, fileName, scheme);
        }
    }

    // ── SFTP ─────────────────────────────────────────────────────────────────

    private void transmitSftp(byte[] fileBytes, String fileName, String scheme) {
        SettlementExportProperties.SchemeExportConfig cfg = props.forScheme(scheme.toLowerCase());
        String host    = cfg.getSftpHost();
        int    port    = cfg.getSftpPort();
        String user    = cfg.getSftpUser();
        String keyPath = cfg.getSftpKeyPath();
        String remDir  = cfg.getRemoteDir();

        if (host == null || host.isBlank()) {
            throw new SettlementTransmissionException(
                    "SFTP host not configured for scheme: " + scheme);
        }
        if (keyPath == null || keyPath.isBlank()) {
            throw new SettlementTransmissionException(
                    "SFTP private key path not configured for scheme: " + scheme);
        }
        // Fail closed BEFORE opening a connection: an unpinned host key means any host
        // answering on this address could impersonate the scheme, receive settlement
        // files, and harvest our authentication attempt. Validating here means we never
        // even contact an unverified host.
        boolean hasKnownHostsPath  = cfg.getSftpKnownHostsPath()  != null
                                     && !cfg.getSftpKnownHostsPath().isBlank();
        boolean hasKnownHostsEntry = cfg.getSftpKnownHostsEntry() != null
                                     && !cfg.getSftpKnownHostsEntry().isBlank();
        if (!hasKnownHostsPath && !hasKnownHostsEntry) {
            throw new SettlementTransmissionException(
                    "SFTP host key not pinned for scheme: " + scheme
                    + " — set card.settlement.export.schemes." + scheme.toLowerCase()
                    + ".sftp-known-hosts-path or .sftp-known-hosts-entry. Refusing to "
                    + "transmit settlement data to an unverified host.");
        }

        log.info("SFTP transmit → {}@{}:{}{}/{} ({} bytes)",
                user, host, port, remDir, fileName, fileBytes.length);

        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            jsch.addIdentity(keyPath);
            pinHostKey(jsch, cfg);

            session = jsch.getSession(user, host, port);
            Properties sshConfig = new Properties();
            // Host key is pinned above; refuse to connect to an unrecognised server.
            sshConfig.put("StrictHostKeyChecking", "yes");
            session.setConfig(sshConfig);
            session.connect(30_000);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(10_000);
            channel.cd(remDir);
            channel.put(new ByteArrayInputStream(fileBytes), fileName,
                    ChannelSftp.OVERWRITE);

            log.info("SFTP transmission complete: scheme={} file={}", scheme, fileName);

        } catch (Exception e) {
            throw new SettlementTransmissionException(
                    "SFTP transmission failed for scheme=" + scheme
                    + " file=" + fileName + ": " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    /**
     * Pin the scheme's SSH host key so {@code StrictHostKeyChecking=yes} has something to
     * verify against. Without this, JSch would accept any host key and settlement files
     * could be delivered to an impostor that answers on the scheme's address.
     *
     * @param cfg scheme config supplying either a known_hosts path or a literal entry
     */
    private void pinHostKey(JSch jsch, SettlementExportProperties.SchemeExportConfig cfg)
            throws com.jcraft.jsch.JSchException {
        String path  = cfg.getSftpKnownHostsPath();
        String entry = cfg.getSftpKnownHostsEntry();

        if (path != null && !path.isBlank()) {
            jsch.setKnownHosts(path);
        } else if (entry != null && !entry.isBlank()) {
            jsch.setKnownHosts(new ByteArrayInputStream(
                    entry.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
    }

    // ── HTTPS ─────────────────────────────────────────────────────────────────

    private void transmitHttps(byte[] fileBytes, String fileName, String scheme) {
        SettlementExportProperties.SchemeExportConfig cfg = props.forScheme(scheme.toLowerCase());
        String endpoint = cfg.getHttpsEndpoint();
        String apiKey   = cfg.getHttpsApiKey();

        if (endpoint == null || endpoint.isBlank()) {
            throw new SettlementTransmissionException(
                    "HTTPS endpoint not configured for scheme: " + scheme);
        }

        // A scheme-specific mutually-authenticated client when a client keystore is
        // configured; otherwise the shared template (one-way TLS + bearer). Built outside
        // the try below so a keystore misconfiguration surfaces as itself rather than
        // being re-wrapped as a generic transmission failure.
        RestTemplate client = tlsClients.forScheme(scheme, cfg);
        boolean mutualTls = client != null;
        if (!mutualTls) {
            client = restTemplate;
        }

        log.info("HTTPS transmit → {} ({} bytes) scheme={} mutualTls={}",
                endpoint, fileBytes.length, scheme, mutualTls);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set("X-File-Name", fileName);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.setBearerAuth(apiKey);
            }

            HttpEntity<byte[]> request = new HttpEntity<>(fileBytes, headers);
            client.postForEntity(endpoint, request, Void.class);

            log.info("HTTPS transmission complete: scheme={} file={}", scheme, fileName);

        } catch (Exception e) {
            throw new SettlementTransmissionException(
                    "HTTPS transmission failed for scheme=" + scheme
                    + " endpoint=" + endpoint + ": " + e.getMessage(), e);
        }
    }
}
