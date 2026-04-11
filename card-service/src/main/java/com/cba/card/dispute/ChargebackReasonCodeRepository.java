package com.cba.card.dispute;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargebackReasonCodeRepository extends JpaRepository<ChargebackReasonCode, UUID> {

    List<ChargebackReasonCode> findBySchemeOrderByCode(String scheme);

    Optional<ChargebackReasonCode> findBySchemeAndCode(String scheme, String code);
}
