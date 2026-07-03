package com.cba.card.settlement;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for {@link SettlementFileTransmitter}'s SFTP path against a
 * real {@code atmoz/sftp} container using genuine public-key authentication — the exact
 * mechanism the Visa/Mastercard/Verve/UnionPay settlement transmitters use in production.
 *
 * <p>Exercises the real JSch stack: {@code addIdentity} with a runtime-generated RSA key,
 * session connect, {@code cd} into the remote drop directory, and a binary {@code put}.
 * The uploaded file is then downloaded over a second SFTP session and byte-compared —
 * proving the transmission actually landed, not merely that {@code transmit()} returned.
 *
 * <p><b>SSH library note:</b> card-service uses the maintained {@code com.github.mwiede:jsch}
 * fork (same {@code com.jcraft.jsch} package as the original, unmaintained
 * {@code com.jcraft:jsch:0.1.55}). The fork ships modern KEX/host-key/cipher algorithms, so
 * it negotiates with the container's default modern OpenSSH out of the box — no legacy-algo
 * server tweak is needed. (The original 0.1.55 failed here with "Algorithm negotiation fail".)
 */
@Testcontainers
@DisplayName("SettlementFileTransmitter (SFTP) — end-to-end against an atmoz/sftp container")
class SettlementFileTransmitterSftpIntegrationTest {

    private static final String USER = "cba";
    private static final String REMOTE_DIR = "upload";

    // Generate the key pair BEFORE the container is defined — the public key must be
    // copied in so atmoz appends it to authorized_keys during startup.
    private static final String PRIVATE_KEY_PATH;
    private static final byte[] PUBLIC_KEY;
    static {
        try {
            JSch jsch = new JSch();
            KeyPair kp = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
            File priv = File.createTempFile("sftp_itest_", ".pem");
            priv.deleteOnExit();
            kp.writePrivateKey(priv.getAbsolutePath());
            ByteArrayOutputStream pub = new ByteArrayOutputStream();
            kp.writePublicKey(pub, "cba-itest");
            kp.dispose();
            PRIVATE_KEY_PATH = priv.getAbsolutePath();
            PUBLIC_KEY = pub.toByteArray();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Container
    @SuppressWarnings("resource") // Testcontainers manages the @Container lifecycle
    static final GenericContainer<?> SFTP =
            new GenericContainer<>(DockerImageName.parse("atmoz/sftp:alpine"))
                    .withCopyToContainer(Transferable.of(PUBLIC_KEY), "/home/" + USER + "/.ssh/keys/id_rsa.pub")
                    // user:pass:e?:uid:gid:dir  → key auth (pass present but JSch uses the key)
                    .withCommand(USER + ":pass:1001:100:" + REMOTE_DIR)
                    .withExposedPorts(22)
                    .waitingFor(Wait.forLogMessage(".*Server listening on.*", 1));

    private SettlementExportProperties propsForVisa() {
        SettlementExportProperties.SchemeExportConfig cfg = new SettlementExportProperties.SchemeExportConfig();
        cfg.setEnabled(true);
        cfg.setSftpHost(SFTP.getHost());
        cfg.setSftpPort(SFTP.getMappedPort(22));
        cfg.setSftpUser(USER);
        cfg.setSftpKeyPath(PRIVATE_KEY_PATH);
        cfg.setRemoteDir(REMOTE_DIR);

        SettlementExportProperties props = new SettlementExportProperties();
        props.getSchemes().put("visa", cfg);
        return props;
    }

    @Test
    @DisplayName("transmit() uploads a settlement file over SFTP; it is retrievable and byte-identical")
    void transmitsOverSftp() throws Exception {
        SettlementFileTransmitter transmitter =
                new SettlementFileTransmitter(propsForVisa(), new RestTemplate());

        byte[] fileBytes = "H|VISA|BASE2|SETTLEMENT|20260703\nD|4111********1111|100.00|USD\nT|1|100.00\n"
                .getBytes(StandardCharsets.UTF_8);
        String fileName = "cba_visa_settlement_20260703.dat";

        transmitter.transmit(fileBytes, fileName, "visa", "SFTP");

        byte[] downloaded = downloadViaSftp(fileName);
        assertThat(downloaded).isEqualTo(fileBytes);
    }

    /** Independently pull the uploaded file back down to prove it actually landed. */
    private byte[] downloadViaSftp(String fileName) throws Exception {
        JSch jsch = new JSch();
        jsch.addIdentity(PRIVATE_KEY_PATH);
        Session session = jsch.getSession(USER, SFTP.getHost(), SFTP.getMappedPort(22));
        Properties cfg = new Properties();
        cfg.put("StrictHostKeyChecking", "no");
        session.setConfig(cfg);
        session.connect(15_000);

        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect(10_000);
        try {
            channel.cd(REMOTE_DIR);
            try (InputStream in = channel.get(fileName)) {
                return in.readAllBytes();
            }
        } finally {
            channel.disconnect();
            session.disconnect();
        }
    }
}
