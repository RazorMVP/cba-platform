package com.cba.card.settlement;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-scheme settlement export configuration.
 *
 * <p>Bound from {@code card.settlement.export.*} in {@code application.yml}.
 * Each scheme has its own sub-block. At production, set the credentials and
 * endpoint for each scheme and flip {@code enabled: true}.
 *
 * <h3>Example application.yml block</h3>
 * <pre>
 * card:
 *   settlement:
 *     export:
 *       acquirer-bin: "411111"          # Your bank's issuer/acquirer BIN
 *       member-id: "CBA001"             # Your scheme membership identifier
 *       export-cron: "0 58 23 * * *"    # Nightly 23:58
 *       max-retries: 3
 *       retry-delay-seconds: 300        # 5 minutes between retries
 *       schemes:
 *         visa:
 *           enabled: false
 *           sftp-host: ${VISA_SFTP_HOST:visanet.visa.com}
 *           sftp-port: ${VISA_SFTP_PORT:22}
 *           sftp-user: ${VISA_SFTP_USER:}
 *           sftp-key-path: ${VISA_SFTP_KEY_PATH:}
 *           remote-dir: ${VISA_SFTP_DIR:/incoming}
 *         mastercard:
 *           enabled: false
 *           sftp-host: ${MC_SFTP_HOST:banknet.mastercard.com}
 *           ...
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "card.settlement.export")
@Getter @Setter
public class SettlementExportProperties {

    /** Your bank's BIN — embedded in file headers and filenames by all schemes. */
    private String acquirerBin = "000000";

    /** Scheme membership identifier (varies by scheme — Visa uses BIN, MC uses member ID). */
    private String memberId = "CBA001";

    /** Cron expression for the nightly export job. */
    private String exportCron = "0 58 23 * * *";

    /** Maximum transmission retry attempts before marking FAILED and alerting ops. */
    private int maxRetries = 3;

    /** Seconds to wait between retry attempts. */
    private long retryDelaySeconds = 300;

    /** Per-scheme configuration map. Keys are lowercase scheme names: visa, mastercard, etc. */
    private Map<String, SchemeExportConfig> schemes = new HashMap<>();

    @Getter @Setter
    public static class SchemeExportConfig {

        /** Whether this scheme exporter is active. False = skip silently. */
        private boolean enabled = false;

        /** SFTP hostname of the scheme clearinghouse server. */
        private String sftpHost;

        /** SFTP port (default 22). */
        private int sftpPort = 22;

        /** SFTP username (provided by scheme on membership registration). */
        private String sftpUser;

        /**
         * Path to the private key file for SFTP public-key authentication.
         * Scheme networks require key-based auth — password auth is not accepted.
         */
        private String sftpKeyPath;

        /** Remote directory on the scheme server to drop files into. */
        private String remoteDir = "/incoming";

        /**
         * Path to an OpenSSH {@code known_hosts} file pinning this scheme's SFTP host key.
         * Preferred supply mechanism: supports key rotation and multiple hosts per scheme.
         *
         * <p>At least one of this or {@link #sftpKnownHostsEntry} must be set for an
         * {@code enabled} scheme — transmission fails closed otherwise, because an
         * unverified host key means any host answering on that address can impersonate
         * the scheme, receive settlement files, and harvest the authentication attempt.
         */
        private String sftpKnownHostsPath;

        /**
         * A literal {@code known_hosts} entry, as an alternative to {@link #sftpKnownHostsPath}
         * for secret-manager/container deployments where mounting a file is awkward.
         *
         * <p>Standard OpenSSH line format — {@code host keytype base64blob}, or
         * {@code [host]:port keytype base64blob} for a non-standard port. Multiple
         * entries may be separated by newlines.
         */
        private String sftpKnownHostsEntry;

        /**
         * HTTPS endpoint for schemes that use REST-based clearinghouse APIs
         * (e.g. newer PAPSS or NIBSS integrations). Ignored when SFTP is used.
         */
        private String httpsEndpoint;

        /** API key / bearer token for HTTPS-based scheme endpoints. */
        private String httpsApiKey;

        /**
         * Path to a keystore holding the scheme-issued <em>client</em> certificate, enabling
         * mutual TLS on the HTTPS settlement path. Optional: when unset, the connection uses
         * ordinary one-way TLS (the server certificate is still verified) with bearer auth.
         *
         * <p>When set but unloadable, transmission fails closed rather than silently
         * falling back to bearer-only — a scheme that mandates mTLS would reject the
         * request anyway, and a silent downgrade hides the misconfiguration.
         */
        private String httpsKeystorePath;

        /** Password for {@link #httpsKeystorePath}. Supply via env/secret manager, never YAML. */
        private String httpsKeystorePassword;

        /** Keystore type for {@link #httpsKeystorePath}. Schemes normally issue PKCS12. */
        private String httpsKeystoreType = "PKCS12";

        /**
         * Optional truststore for verifying the scheme's server certificate. When unset,
         * the JDK default truststore is used — correct for schemes with a publicly-rooted
         * certificate; set this when the scheme uses a private CA.
         */
        private String httpsTruststorePath;

        /** Password for {@link #httpsTruststorePath}. Supply via env/secret manager, never YAML. */
        private String httpsTruststorePassword;

        /**
         * Scheme-assigned member/participant identifier for file naming and headers.
         * Overrides the global {@code memberId} if set.
         */
        private String participantId;
    }

    /** Convenience accessor — returns config for a scheme key, or a default empty config. */
    public SchemeExportConfig forScheme(String schemeKey) {
        return schemes.getOrDefault(schemeKey.toLowerCase(), new SchemeExportConfig());
    }
}
