package com.cba.fep.scheme;

import com.cba.fep.auth.CardServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves scheme from PAN BIN and provides the correct {@link SchemeAdapter}.
 *
 * <p>BIN lookup is a two-step process:
 * <ol>
 *   <li>Fast local cache: 8-digit then 6-digit BIN range lookup (in-memory)</li>
 *   <li>Remote fallback: card-service REST call if BIN not in cache</li>
 * </ol>
 *
 * <p>The local cache is pre-populated at startup from card-service and
 * refreshed periodically. This ensures BIN lookup adds < 1ms latency
 * to the authorization path, meeting ATM timeout requirements.
 *
 * <p>Unregistered BINs return {@link SchemeType#UNKNOWN}; the authorization
 * handler will decline with RC=57 (Transaction Not Permitted to Cardholder).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemeAdapterFactory {

    private final CardServiceClient cardServiceClient;
    private final List<SchemeAdapter> adapters;

    private final Map<SchemeType, SchemeAdapter> adapterMap = new EnumMap<>(SchemeType.class);

    // BIN cache: populated from card-service on startup + refresh
    // Key = 6- or 8-digit BIN prefix, Value = resolved SchemeType
    private final Map<String, SchemeType> binCache = new java.util.concurrent.ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    void init() {
        adapters.forEach(a -> adapterMap.put(a.getSchemeType(), a));
        log.info("SchemeAdapterFactory: registered {} adapters: {}",
                adapterMap.size(), adapterMap.keySet());
        refreshBinCache();
    }

    /**
     * Detect card scheme from PAN BIN.
     * Tries 8-digit BIN first (ISO 7812 2017 extension), falls back to 6-digit.
     */
    public SchemeType detectScheme(String pan) {
        if (pan == null || pan.length() < 6) {
            return SchemeType.UNKNOWN;
        }
        // 8-digit BIN lookup (preferred — more specific)
        if (pan.length() >= 8) {
            SchemeType scheme = binCache.get(pan.substring(0, 8));
            if (scheme != null) return scheme;
        }
        // 6-digit BIN lookup (legacy)
        SchemeType scheme = binCache.get(pan.substring(0, 6));
        if (scheme != null) return scheme;

        // Remote fallback for uncached BINs
        try {
            scheme = cardServiceClient.lookupBinScheme(pan.substring(0, 8));
            if (scheme != null && scheme != SchemeType.UNKNOWN) {
                binCache.put(pan.substring(0, 8), scheme);
                return scheme;
            }
        } catch (Exception e) {
            log.warn("BIN lookup failed for PAN prefix {}: {}", pan.substring(0, 6), e.getMessage());
        }
        return SchemeType.UNKNOWN;
    }

    public SchemeAdapter getAdapter(SchemeType scheme) {
        return adapterMap.getOrDefault(scheme, adapterMap.get(SchemeType.UNKNOWN) != null
                ? adapterMap.get(SchemeType.UNKNOWN)
                : new UnknownSchemeAdapter());
    }

    public void refreshBinCache() {
        try {
            Map<String, SchemeType> fresh = cardServiceClient.getAllBinMappings();
            binCache.clear();
            binCache.putAll(fresh);
            log.info("BIN cache refreshed: {} entries", binCache.size());
        } catch (Exception e) {
            log.warn("BIN cache refresh failed (using stale cache): {}", e.getMessage());
        }
    }
}
