package com.cba.card.config;

import com.cba.card.card.Card;
import com.cba.card.card.CardService;
import com.cba.card.integration.AbstractCardIntegrationTest;
import com.cba.card.wallet.PrepaidWallet;
import com.cba.card.wallet.PrepaidWalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Approvals reserve funds, over real HTTP and PostgreSQL with a real prepaid card: a second
 * authorization can't spend what the first one took, and a reversal gives it back. Before
 * this, approvals reserved nothing — any number could pass against the same balance.
 */
@DisplayName("Card reservation — approvals reserve, reversals give back (prepaid, real HTTP)")
class CardReservationIntegrationTest extends AbstractCardIntegrationTest {

    static final UUID PREPAID_PRODUCT = UUID.fromString("11111111-0001-0001-0001-000000000003");

    @Autowired TestRestTemplate rest;
    @Autowired CardService cardService;
    @Autowired PrepaidWalletRepository walletRepository;

    @Test
    @DisplayName("30.00 + 30.00 against a 50.00 wallet: first approved, second 51; reversal restores 50.00")
    void secondAuthorizationCantSpendTheFirstOnesFunds() {
        String pan = "4300" + String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
        UUID customer = UUID.randomUUID();
        Card card = cardService.issueCard(PREPAID_PRODUCT, customer, null, true, pan, "2912", "123", "USD");
        PrepaidWallet wallet = new PrepaidWallet();
        wallet.setCard(card);
        wallet.setCustomerId(customer);
        wallet.setBalance(new BigDecimal("50.00"));
        wallet.setCurrencyCode("USD");
        walletRepository.save(wallet);

        assertThat(authorize(pan, "800001", "M1", 3000)).isEqualTo("00");
        assertThat(balance(card)).isEqualByComparingTo("20.00");
        assertThat(authorize(pan, "800002", "M2", 3000)).as("only 20.00 left").isEqualTo("51");

        assertThat(post("/api/v1/internal/reverse", Map.of("pan", pan, "amount", 3000, "stan", "800001",
                "originalDataElements", "0100800001")).get("responseCode")).isEqualTo("00");
        assertThat(balance(card)).isEqualByComparingTo("50.00");
        // A duplicate reversal gives nothing back twice.
        post("/api/v1/internal/reverse", Map.of("pan", pan, "amount", 3000, "stan", "800001",
                "originalDataElements", "0100800001"));
        assertThat(balance(card)).isEqualByComparingTo("50.00");

        assertThat(authorize(pan, "800003", "M3", 3000)).isEqualTo("00");
    }

    private BigDecimal balance(Card card) {
        return walletRepository.findByCardId(card.getId()).orElseThrow().getBalance();
    }

    private String authorize(String pan, String stan, String merchant, long amountMinor) {
        Map<String, Object> body = Map.ofEntries(
                Map.entry("pan", pan), Map.entry("processingCode", "000000"), Map.entry("amount", amountMinor),
                Map.entry("currencyCode", "840"), Map.entry("stan", stan), Map.entry("terminalId", "TERM0001"),
                Map.entry("merchantId", merchant), Map.entry("merchantName", "Shop " + merchant),
                Map.entry("mcc", "5411"), Map.entry("posEntryMode", "051"), Map.entry("rrn", "6123" + stan + "00"),
                Map.entry("scheme", "VISA"), Map.entry("pinVerified", true), Map.entry("arqcValid", true),
                Map.entry("isFinancial", false));
        return (String) post("/api/v1/internal/authorize", body).get("responseCode");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(ROLES_TOKEN + ".FEP_SERVICE");
        var r = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);
        return r.getBody();
    }
}
