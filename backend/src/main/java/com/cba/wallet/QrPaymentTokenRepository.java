package com.cba.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QrPaymentTokenRepository extends JpaRepository<QrPaymentToken, UUID> {

    Optional<QrPaymentToken> findByToken(String token);
}
