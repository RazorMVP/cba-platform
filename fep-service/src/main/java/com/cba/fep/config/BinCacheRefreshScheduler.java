package com.cba.fep.config;

import com.cba.fep.scheme.SchemeAdapterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that refreshes the in-memory BIN cache every 5 minutes.
 *
 * <p>The BIN cache is pre-populated at startup in
 * {@link SchemeAdapterFactory#init()}. New BIN registrations in card-service
 * are picked up by this scheduler without a restart.
 *
 * <p>The initial 5-minute delay ensures the first refresh is not attempted
 * before card-service is ready (startup ordering in Docker Compose).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BinCacheRefreshScheduler {

    private final SchemeAdapterFactory schemeAdapterFactory;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public void refreshBinCache() {
        log.debug("Scheduled BIN cache refresh starting...");
        schemeAdapterFactory.refreshBinCache();
    }
}
