package com.cba.account;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates sequential, human-readable account numbers in the format:
 * {branch_code}-{type_code}-{7-digit-sequence}
 * e.g. 001-SAV-0001234
 *
 * Uses SELECT FOR UPDATE on account_number_sequences to prevent gaps
 * under concurrent requests.
 */
@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {

    private final EntityManager em;
    private static final String DEFAULT_BRANCH = "001";

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generate(AccountType accountType) {
        String typeCode = accountType.typeCode();

        // Lock the sequence row to prevent concurrent increments
        var query = em.createNativeQuery(
            "SELECT last_sequence FROM account_number_sequences " +
            "WHERE branch_code = :branch AND account_type = :type " +
            "FOR UPDATE");
        query.setParameter("branch", DEFAULT_BRANCH);
        query.setParameter("type", typeCode);

        long lastSeq = ((Number) query.getSingleResult()).longValue();
        long nextSeq = lastSeq + 1;

        em.createNativeQuery(
            "UPDATE account_number_sequences SET last_sequence = :next " +
            "WHERE branch_code = :branch AND account_type = :type")
            .setParameter("next", nextSeq)
            .setParameter("branch", DEFAULT_BRANCH)
            .setParameter("type", typeCode)
            .executeUpdate();

        return String.format("%s-%s-%07d", DEFAULT_BRANCH, typeCode, nextSeq);
    }
}
