package com.cba.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GlobalConfigurationRepository extends JpaRepository<GlobalConfiguration, UUID> {
    Optional<GlobalConfiguration> findByName(String name);
}
