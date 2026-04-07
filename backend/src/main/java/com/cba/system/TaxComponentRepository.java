package com.cba.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TaxComponentRepository extends JpaRepository<TaxComponent, UUID> {
    boolean existsByName(String name);
}
