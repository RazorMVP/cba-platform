package com.cba.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TaxGroupRepository extends JpaRepository<TaxGroup, UUID> {
    boolean existsByName(String name);
}
