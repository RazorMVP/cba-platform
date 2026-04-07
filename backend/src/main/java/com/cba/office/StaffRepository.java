package com.cba.office;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<Staff, UUID> {
    List<Staff> findByOfficeIdAndActiveTrue(UUID officeId);
    List<Staff> findByLoanOfficerTrueAndActiveTrue();
    List<Staff> findByActiveTrue();
}
