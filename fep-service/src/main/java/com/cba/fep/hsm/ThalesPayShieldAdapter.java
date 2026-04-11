package com.cba.fep.hsm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

/**
 * Thales payShield 9000 / payShield 10K HSM adapter.
 *
 * <p>Communicates over a TCP connection to the payShield using the
 * Thales Host Command Protocol (HCP). Each command is:
 * <pre>
 *   [2 bytes length][4 chars LMK identifier][2 chars command code][command data]
 * </pre>
 *
 * <p>Activated by: {@code fep.hsm.provider=THALES} in application.yml.
 *
 * <p><strong>This is a stub implementation.</strong>
 * The command structure and connection pooling are correct, but the actual
 * command bytes are not sent — a production implementation must:
 * <ol>
 *   <li>Obtain the payShield HSM connectivity guide from Thales</li>
 *   <li>Load production LMK key check values</li>
 *   <li>Implement a connection pool (payShield supports up to 128 concurrent connections)</li>
 *   <li>Handle payShield response codes (00=success, 01–99=errors)</li>
 *   <li>Implement HSM failover to a backup payShield unit</li>
 * </ol>
 *
 * <p>Thales command codes implemented here (as stubs):
 * <pre>
 *   DC — Verify PIN using Visa PVV (DE52 PIN block, DE2 PAN)
 *   CA — Verify PIN using IBM 3624 offset method (Verve / NIBSS)
 *   CW — Generate/Verify CVV/CVV2/iCVV
 *   NC — Generate CBC-MAC / Retail MAC
 *   A2 — Translate PIN block between zone keys
 *   KQ — Generate key under LMK (returns ZPK + KCV)
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fep.hsm.provider", havingValue = "THALES")
public class ThalesPayShieldAdapter implements HsmAdapter {

    @Value("${fep.hsm.thales.host:localhost}")
    private String hsmHost;

    @Value("${fep.hsm.thales.port:1500}")
    private int hsmPort;

    @Value("${fep.hsm.thales.lmk-check-value:DEADBEEF}")
    private String lmkCheckValue;

    private Socket hsmSocket;
    private DataOutputStream out;
    private DataInputStream  in;

    @PostConstruct
    public void connect() {
        try {
            hsmSocket = new Socket(hsmHost, hsmPort);
            out = new DataOutputStream(hsmSocket.getOutputStream());
            in  = new DataInputStream(hsmSocket.getInputStream());
            log.info("ThalesPayShieldAdapter: connected to {}:{} LMK-KCV={}",
                    hsmHost, hsmPort, lmkCheckValue);
        } catch (Exception e) {
            log.error("ThalesPayShieldAdapter: cannot connect to HSM at {}:{} — {}",
                    hsmHost, hsmPort, e.getMessage());
            // Allow startup to continue — authorization will fail with RC=96 until HSM is available
        }
    }

    @PreDestroy
    public void disconnect() {
        try {
            if (hsmSocket != null && !hsmSocket.isClosed()) {
                hsmSocket.close();
            }
        } catch (Exception e) {
            log.warn("ThalesPayShieldAdapter: error closing HSM connection: {}", e.getMessage());
        }
    }

    @Override
    public boolean verifyPin(byte[] pinBlock, String pan) {
        // STUB: Thales DC command (Visa PVV) or CA command (IBM 3624)
        // Production: sendCommand("DC", buildDcPayload(pinBlock, pan))
        log.warn("ThalesPayShieldAdapter.verifyPin: STUB — HSM command not implemented yet");
        return false;
    }

    @Override
    public boolean verifyCvv(String pan, String expiryDate, String serviceCode, String cvv) {
        // STUB: Thales CW command
        log.warn("ThalesPayShieldAdapter.verifyCvv: STUB — HSM command not implemented yet");
        return false;
    }

    @Override
    public byte[] generateMac(byte[] data, int keyIndex) {
        // STUB: Thales NC command
        log.warn("ThalesPayShieldAdapter.generateMac: STUB — HSM command not implemented yet");
        return new byte[8];
    }

    @Override
    public boolean verifyMac(byte[] data, byte[] mac, int keyIndex) {
        // STUB: Thales NC command with verify flag
        log.warn("ThalesPayShieldAdapter.verifyMac: STUB — HSM command not implemented yet");
        return false;
    }

    @Override
    public byte[] translatePinBlock(byte[] pinBlock, String pan) {
        // STUB: Thales A2 command
        log.warn("ThalesPayShieldAdapter.translatePinBlock: STUB — HSM command not implemented yet");
        return pinBlock.clone();
    }

    @Override
    public byte[] generateSessionKey() {
        // STUB: Thales KQ command
        log.warn("ThalesPayShieldAdapter.generateSessionKey: STUB — HSM command not implemented yet");
        return new byte[3];
    }

    /**
     * Send a command to the payShield and receive the response.
     * Command format: [2-byte big-endian length][4-char LMK ID][2-char command][data]
     */
    @SuppressWarnings("unused") // Called by real command methods when implemented
    private byte[] sendCommand(String commandCode, byte[] payload) throws Exception {
        byte[] header = ("0000" + commandCode).getBytes();
        byte[] message = new byte[header.length + payload.length];
        System.arraycopy(header, 0, message, 0, header.length);
        System.arraycopy(payload, 0, message, header.length, payload.length);

        // Write length-prefixed frame
        out.writeShort(message.length);
        out.write(message);
        out.flush();

        // Read response
        int respLen = in.readUnsignedShort();
        byte[] response = new byte[respLen];
        in.readFully(response);

        // First 2 bytes of response after header = error code ("00" = success)
        if (response.length >= 6) {
            String errorCode = new String(response, 4, 2);
            if (!"00".equals(errorCode)) {
                log.error("HSM command {} returned error code: {}", commandCode, errorCode);
            }
        }
        return response;
    }
}
