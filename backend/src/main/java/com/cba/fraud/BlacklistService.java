package com.cba.fraud;

import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlacklistService {

    private final BlacklistEntryRepository blacklistRepository;

    @Transactional(readOnly = true)
    public Page<BlacklistEntry> listEntries(String entityType, Boolean active, Pageable pageable) {
        return blacklistRepository.findFiltered(entityType, active, pageable);
    }

    @Transactional(readOnly = true)
    public BlacklistEntry getEntry(UUID id) {
        return blacklistRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("BlacklistEntry", id));
    }

    @Transactional(readOnly = true)
    public List<BlacklistEntry> search(String query) {
        return blacklistRepository.searchActive(query, Instant.now());
    }

    @Transactional
    public BlacklistEntry addEntry(String entityType, String entityValue, String reason,
                                   String source, Instant expiresAt, String addedBy) {
        BlacklistEntry entry = new BlacklistEntry();
        entry.setEntityType(entityType);
        entry.setEntityValue(entityValue);
        entry.setReason(reason);
        entry.setSource(source != null ? source : "INTERNAL");
        entry.setActive(true);
        entry.setAddedBy(addedBy);
        entry.setExpiresAt(expiresAt);
        return blacklistRepository.save(entry);
    }

    @Transactional
    public BlacklistEntry deactivateEntry(UUID id) {
        BlacklistEntry entry = getEntry(id);
        entry.setActive(false);
        return blacklistRepository.save(entry);
    }

    @Transactional
    public BlacklistEntry updateEntry(UUID id, String reason, Instant expiresAt) {
        BlacklistEntry entry = getEntry(id);
        if (reason != null) entry.setReason(reason);
        if (expiresAt != null) entry.setExpiresAt(expiresAt);
        return blacklistRepository.save(entry);
    }
}
