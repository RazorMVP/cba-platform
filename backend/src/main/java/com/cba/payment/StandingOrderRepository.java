package com.cba.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StandingOrderRepository extends JpaRepository<StandingOrder, UUID> {
    List<StandingOrder> findBySourceAccountId(UUID accountId);

    @Query("SELECT s FROM StandingOrder s WHERE s.status = 'ACTIVE' AND s.nextExecutionDate <= :date")
    List<StandingOrder> findDueOrders(LocalDate date);
}
