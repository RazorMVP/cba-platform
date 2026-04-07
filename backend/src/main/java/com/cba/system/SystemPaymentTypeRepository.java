package com.cba.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SystemPaymentTypeRepository extends JpaRepository<SystemPaymentType, UUID> {
    boolean existsByName(String name);
}
