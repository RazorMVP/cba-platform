package com.cba.openbanking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Nightly-ish sweep that expires Open Banking consents past their {@code expiryDate}.
 *
 * <p>Runs hourly (at :07) so an authorised consent is marked {@link ConsentStatus#EXPIRED}
 * within an hour of lapsing and the {@code CONSENT.EXPIRED} partner webhook fires — closing
 * the gap where consents only failed <em>at use time</em> (PISP/CBPII throwing
 * {@code CONSENT_EXPIRED}) but never transitioned status or notified the partner.
 *
 * <p>The scheduling concern is kept out of {@link ConsentService} so the expiry logic stays
 * unit-testable without a scheduler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConsentExpiryJob {

    private final ConsentService consentService;

    @Scheduled(cron = "${app.openbanking.consent-expiry-cron:0 7 * * * *}")
    public void expireConsents() {
        int expired = consentService.expireDueConsents(Instant.now());
        if (expired > 0) {
            log.info("Consent expiry sweep: {} consent(s) transitioned to EXPIRED", expired);
        }
    }
}
