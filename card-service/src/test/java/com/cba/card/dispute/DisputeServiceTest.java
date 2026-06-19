package com.cba.card.dispute;

import com.cba.card.common.CbaException;
import com.cba.card.openbanking.webhook.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DisputeService} — the scheme-compliant chargeback workflow.
 *
 * <p>This also serves as the proof that Mockito can mock concrete classes on this
 * Java 25 host: {@link WebhookService} is a concrete {@code @Service} (not an
 * interface), and {@code @Mock}-ing it previously failed with
 * "Could not modify all classes". With the surefire {@code -javaagent} + Byte Buddy
 * experimental flag in card-service/pom.xml, it now mocks cleanly.
 */
@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock CardDisputeRepository          disputeRepository;
    @Mock ChargebackReasonCodeRepository reasonCodeRepository;
    @Mock RetrievalRequestRepository     retrievalRepository;
    @Mock RepresentmentRepository        representmentRepository;
    @Mock WebhookService                 webhookService; // concrete class — the Java 25 proof

    private DisputeService service;

    @BeforeEach
    void setUp() {
        service = new DisputeService(disputeRepository, reasonCodeRepository,
                retrievalRepository, representmentRepository);
        // webhookService is a @Lazy @Autowired field, not a constructor arg
        ReflectionTestUtils.setField(service, "webhookService", webhookService);
    }

    @Test
    @DisplayName("raiseDispute creates a RAISED dispute and publishes DISPUTE.RAISED")
    void raiseDispute() {
        // Mimic JPA assigning the @GeneratedValue id on save — the service's webhook
        // payload uses saved.getId() inside a null-intolerant Map.of(...).
        when(disputeRepository.save(any(CardDispute.class))).thenAnswer(i -> {
            CardDispute saved = i.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID());
            return saved;
        });

        CardDispute d = service.raiseDispute(UUID.randomUUID(), "RRN0001",
                DisputeReason.UNAUTHORIZED, UUID.randomUUID(), new BigDecimal("100.00"), "840");

        assertThat(d.getStatus()).isEqualTo(DisputeStatus.RAISED);
        verify(webhookService).publishEvent(eq("DISPUTE.RAISED"), anyMap());
    }

    @Test
    @DisplayName("resolve(ISSUER) moves to RESOLVED and publishes DISPUTE.RESOLVED")
    void resolveInFavorOfIssuer() {
        UUID id = UUID.randomUUID();
        CardDispute existing = new CardDispute();
        existing.setStatus(DisputeStatus.RAISED);
        when(disputeRepository.findById(id)).thenReturn(Optional.of(existing));
        when(disputeRepository.save(any(CardDispute.class))).thenAnswer(i -> i.getArgument(0));

        CardDispute resolved = service.resolve(id, UUID.randomUUID(), "issuer", "docs valid");

        assertThat(resolved.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(resolved.getResolutionFavor()).isEqualTo("ISSUER"); // normalised to upper
        verify(webhookService).publishEvent(eq("DISPUTE.RESOLVED"), anyMap());
    }

    @Test
    @DisplayName("resolve rejects an invalid resolutionFavor")
    void resolveInvalidFavor() {
        UUID id = UUID.randomUUID();
        CardDispute existing = new CardDispute();
        existing.setStatus(DisputeStatus.RAISED);
        when(disputeRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.resolve(id, UUID.randomUUID(), "BANK", "x"))
                .isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("withdraw on a terminal dispute is rejected")
    void withdrawTerminalRejected() {
        UUID id = UUID.randomUUID();
        CardDispute existing = new CardDispute();
        existing.setStatus(DisputeStatus.RESOLVED);
        when(disputeRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.withdraw(id))
                .isInstanceOf(CbaException.class);
    }
}
