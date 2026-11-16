package com.cba.card.card.migration;

import com.cba.card.card.CardSecrets;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Backlog 2 PR B: encrypts the card numbers card-service stored in clear — {@code cards.pan_encrypted} and
 * {@code token_vault.dpan_encrypted} never had a converter, so they held the digits (PCI DSS 4.0 §3.5.1).
 *
 * <p>A Spring bean, not a class in {@code db/migration}: it needs {@link CardSecrets} (the Jasypt encryptor and the
 * key check), and Spring Boot hands {@code JavaMigration} beans to Flyway. Flyway runs it before the app serves
 * anything, inside its migration transaction, so no request ever reads a half-migrated table.
 *
 * <p>Each value not already {@code ENC1:} must be 12–19 digits, or the V2 demo placeholder {@code ENC(digits)};
 * anything else fails the migration — an unknown state is not guessed at. Rows are rewritten in batches;
 * only counts are logged, never a value.
 *
 * <p>PostgreSQL keeps the old row versions (clear values) until vacuumed, and backups taken earlier still hold
 * them: see {@code docs/integration-runbook.md}.
 */
@Slf4j
@Component
public class V24__EncryptCardSecrets extends BaseJavaMigration {

    static final int BATCH = 500;
    private static final Pattern DIGITS = Pattern.compile("\\d{12,19}");
    private static final Pattern DEMO_PLACEHOLDER = Pattern.compile("ENC\\((\\d{12,19})\\)");

    private final CardSecrets secrets;

    public V24__EncryptCardSecrets(CardSecrets secrets) {
        this.secrets = secrets;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        int cards = encryptColumn(c, "cards", "pan_encrypted");
        int tokens = encryptColumn(c, "token_vault", "dpan_encrypted");
        log.info("Card secrets encrypted at rest: {} card numbers, {} token numbers", cards, tokens);
    }

    /** Table and column names are constants of this class, never input. */
    int encryptColumn(Connection c, String table, String column) throws Exception {
        String select = "SELECT id, " + column + " FROM " + table
                + " WHERE " + column + " NOT LIKE 'ENC1:%' ORDER BY id LIMIT " + BATCH;
        String update = "UPDATE " + table + " SET " + column + " = ? WHERE id = ?";
        int total = 0;
        while (true) {
            List<UUID> ids = new ArrayList<>();
            List<String> values = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(select); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getObject(1, UUID.class));
                    values.add(rs.getString(2));
                }
            }
            if (ids.isEmpty()) {
                return total;
            }
            try (PreparedStatement ps = c.prepareStatement(update)) {
                for (int i = 0; i < ids.size(); i++) {
                    ps.setString(1, secrets.encrypt(clearValue(table, ids.get(i), values.get(i))));
                    ps.setObject(2, ids.get(i));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            total += ids.size();
        }
    }

    static String clearValue(String table, UUID id, String stored) {
        if (stored != null) {
            if (DIGITS.matcher(stored).matches()) {
                return stored;
            }
            var demo = DEMO_PLACEHOLDER.matcher(stored);
            if (demo.matches()) {
                return demo.group(1);
            }
        }
        // Never put the value in the message: it may be a card number in some other shape.
        throw new IllegalStateException("Can't encrypt " + table + " row " + id
                + ": the stored value is neither 12–19 digits nor ENC1 ciphertext");
    }
}
