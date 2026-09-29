package com.cba.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StandingOrderRepository extends JpaRepository<StandingOrder, UUID> {
    List<StandingOrder> findBySourceAccountId(UUID accountId);

    /**
     * IDs of orders due on or before {@code date}. The CoB job snapshots these IDs up
     * front: executing an order moves its next execution date, so a paged read over
     * this same query would shift under the job and skip orders.
     */
    @Query("SELECT s.id FROM StandingOrder s WHERE s.status = 'ACTIVE' AND s.nextExecutionDate <= :date ORDER BY s.id")
    List<UUID> findDueOrderIds(LocalDate date);
}
