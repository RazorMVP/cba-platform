package com.cba.card.bin;

import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinService {

    private final BinRangeRepository binRangeRepository;

    /**
     * Resolve card scheme from PAN.
     * Tries 8-digit BIN first (ISO 7812:2017 extension), falls back to 6-digit.
     * Result is cached — BIN ranges rarely change.
     */
    @Cacheable("binLookup")
    public SchemeType lookupScheme(String pan) {
        if (pan == null || pan.length() < 6) {
            return SchemeType.UNKNOWN;
        }
        String pan8 = pan.length() >= 8 ? pan.substring(0, 8) : pan.substring(0, 6);
        List<BinRange> matches = binRangeRepository.findByPan8(pan8);
        if (matches.isEmpty()) {
            log.debug("BIN not found for PAN prefix {}", pan8);
            return SchemeType.UNKNOWN;
        }
        return matches.get(0).getScheme();
    }

    /**
     * Export all BIN→scheme mappings for FEP cache pre-population.
     * Returns: key = first 8 digits of binStart, value = scheme name string.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getAllMappings() {
        return binRangeRepository.findAllByActiveTrue().stream()
                .collect(Collectors.toMap(
                        BinRange::getBinStart,
                        b -> b.getScheme().name(),
                        (a, b) -> a));  // keep first on collision
    }

    @Transactional(readOnly = true)
    public List<BinRange> listAll() {
        return binRangeRepository.findAll();
    }

    @Transactional
    public BinRange create(BinRange range) {
        return binRangeRepository.save(range);
    }

    @Transactional
    public BinRange update(UUID id, BinRange req) {
        BinRange existing = binRangeRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("BIN_NOT_FOUND", "BIN range not found: " + id));
        existing.setBinStart(req.getBinStart());
        existing.setBinEnd(req.getBinEnd());
        existing.setScheme(req.getScheme());
        existing.setProductType(req.getProductType());
        existing.setCardType(req.getCardType());
        existing.setCountryCode(req.getCountryCode());
        existing.setActive(req.isActive());
        return binRangeRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        BinRange range = binRangeRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("BIN_NOT_FOUND", "BIN range not found: " + id));
        range.setActive(false);
        binRangeRepository.save(range);
    }
}
