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
 *   <li>SFTP: load private key from secure vault (not filesystem path in env var)</li>
 *   <li>SFTP: verify known_hosts fingerprint instead of StrictHostKeyChecking=no</li>
 *   <li>HTTPS: configure mutual TLS via scheme-provided client certificate</li>
 *   <li>Both: wrap with circuit breaker (Resilience4j) for scheme network outages</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementFileTransmitter {

    private final SettlementExportProperties props;
    private final RestTemplate restTemplate;

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

        log.info("SFTP transmit → {}@{}:{}{}/{} ({} bytes)",
                user, host, port, remDir, fileName, fileBytes.length);

        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            jsch.addIdentity(keyPath);

            session = jsch.getSession(user, host, port);
            Properties sshConfig = new Properties();
            // TODO production: replace no with known_hosts fingerprint verification
            sshConfig.put("StrictHostKeyChecking", "no");
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

    // ── HTTPS ─────────────────────────────────────────────────────────────────

    private void transmitHttps(byte[] fileBytes, String fileName, String scheme) {
        SettlementExportProperties.SchemeExportConfig cfg = props.forScheme(scheme.toLowerCase());
        String endpoint = cfg.getHttpsEndpoint();
        String apiKey   = cfg.getHttpsApiKey();

        if (endpoint == null || endpoint.isBlank()) {
            throw new SettlementTransmissionException(
                    "HTTPS endpoint not configured for scheme: " + scheme);
        }

        log.info("HTTPS transmit → {} ({} bytes) scheme={}", endpoint, fileBytes.length, scheme);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set("X-File-Name", fileName);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.setBearerAuth(apiKey);
            }

            HttpEntity<byte[]> request = new HttpEntity<>(fileBytes, headers);
            restTemplate.postForEntity(endpoint, request, Void.class);

            log.info("HTTPS transmission complete: scheme={} file={}", scheme, fileName);

        } catch (Exception e) {
            throw new SettlementTransmissionException(
                    "HTTPS transmission failed for scheme=" + scheme
                    + " endpoint=" + endpoint + ": " + e.getMessage(), e);
        }
    }
}
