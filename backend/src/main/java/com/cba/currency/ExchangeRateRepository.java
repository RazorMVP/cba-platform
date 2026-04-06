package com.cba.currency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    Optional<ExchangeRate> findByFromCurrencyAndToCurrencyAndActiveTrue(
        String fromCurrency, String toCurrency);

    List<ExchangeRate> findByActiveTrue();

    @Query("SELECT e FROM ExchangeRate e WHERE e.active = true " +
           "AND (e.fromCurrency = :currency OR e.toCurrency = :currency)")
    List<ExchangeRate> findAllActivePairsForCurrency(String currency);

    Optional<ExchangeRate> findByFromCurrencyAndToCurrency(
        String fromCurrency, String toCurrency);
}
