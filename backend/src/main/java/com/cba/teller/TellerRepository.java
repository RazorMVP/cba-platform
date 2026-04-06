package com.cba.teller;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TellerRepository extends JpaRepository<Teller, UUID> {
    List<Teller> findByStatus(TellerStatus status);
    List<Teller> findByBranchCode(String branchCode);
}
