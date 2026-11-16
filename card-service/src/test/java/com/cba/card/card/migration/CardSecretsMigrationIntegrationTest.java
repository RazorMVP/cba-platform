package com.cba.card.card.migration;

import com.cba.card.card.CardSecrets;
import com.cba.card.integration.AbstractCardIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link V24__EncryptCardSecrets} against real PostgreSQL: it ran at startup as a Spring bean (Spring Boot hands
 * {@code JavaMigration} beans to Flyway), clear and V2-placeholder card numbers become ENC1 ciphertext, a rerun
 * changes nothing, and an unknown value fails without its contents in the message.
 */
@DisplayName("V24 — card numbers stored in clear are encrypted (backlog 2 PR B)")
class CardSecretsMigrationIntegrationTest extends AbstractCardIntegrationTest {

    static final UUID PRODUCT = UUID.fromString("11111111-0001-0001-0001-000000000001");

    @Autowired V24__EncryptCardSecrets migration;
    @Autowired CardSecrets secrets;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    @DisplayName("ran at startup: version 24 applied, the V2 demo cards are ENC1 and no stored card number is clear")
    void ranAtStartup() {
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '24'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM cards WHERE id::text LIKE '22222222-%' AND pan_encrypted LIKE 'ENC1:%'",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM cards WHERE pan_encrypted ~ '^[0-9]{12,19}$' OR pan_encrypted LIKE 'ENC(%'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'cards' AND column_name = 'cvv_encrypted'",
                Integer.class)).as("V23 dropped the CVV column").isZero();
    }

    @Test
    @DisplayName("clear digits and ENC(digits) become ENC1 ciphertext that decrypts back; a rerun changes nothing")
    void encryptsAndIsIdempotent() throws Exception {
        String clearPan = randomPan("55");
        String placeholderPan = randomPan("51");
        UUID clearCard = insertCard(clearPan);
        UUID placeholderCard = insertCard("ENC(" + placeholderPan + ")");
        try (Connection c = dataSource.getConnection()) {
            assertThat(migration.encryptColumn(c, "cards", "pan_encrypted")).isEqualTo(2);

            String a = stored(clearCard);
            String b = stored(placeholderCard);
            assertThat(a).startsWith("ENC1:").doesNotContain(clearPan);
            assertThat(b).startsWith("ENC1:").doesNotContain(placeholderPan);
            assertThat(secrets.decrypt(a)).isEqualTo(clearPan);
            assertThat(secrets.decrypt(b)).isEqualTo(placeholderPan);

            assertThat(migration.encryptColumn(c, "cards", "pan_encrypted")).as("rerun").isZero();
            assertThat(stored(clearCard)).isEqualTo(a);
        } finally {
            jdbc.update("DELETE FROM cards WHERE id IN (?, ?)", clearCard, placeholderCard);
        }
    }

    @Test
    @DisplayName("a value that is neither digits nor ciphertext fails the migration, naming the row but not the value")
    void unknownValueFails() throws Exception {
        UUID bad = insertCard("4111-1111-1111-1111");
        try (Connection c = dataSource.getConnection()) {
            assertThatThrownBy(() -> migration.encryptColumn(c, "cards", "pan_encrypted"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(bad.toString())
                    .hasMessageNotContaining("4111");
        } finally {
            jdbc.update("DELETE FROM cards WHERE id = ?", bad);
        }
    }

    private UUID insertCard(String panStored) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cards (id, pan_encrypted, pan_hash, pan_prefix, pan_suffix, expiry_date,
                                   card_type, status, customer_id, product_id)
                VALUES (?, ?, ?, '55000000', '0004', '2912', 'DEBIT', 'ACTIVE', ?, ?)
                """, id, panStored, "migration-test-" + id, UUID.randomUUID(), PRODUCT);
        return id;
    }

    private String stored(UUID cardId) {
        List<String> v = jdbc.queryForList("SELECT pan_encrypted FROM cards WHERE id = ?", String.class, cardId);
        return v.get(0);
    }

    private static String randomPan(String prefix) {
        return prefix + String.format("%014d", ThreadLocalRandom.current().nextLong(100_000_000_000_000L));
    }
}
