package com.cba.teller;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface CashTransactionRepository extends JpaRepository<CashTransaction, UUID> {
    List<CashTransaction> findBySessionId(UUID sessionId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM CashTransaction t " +
           "WHERE t.session.id = :sessionId AND t.transactionType = :type")
    BigDecimal sumBySessionIdAndType(UUID sessionId, CashTransactionType type);
}
