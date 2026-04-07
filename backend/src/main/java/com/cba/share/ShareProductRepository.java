package com.cba.share;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ShareProductRepository extends JpaRepository<ShareProduct, UUID> {
    boolean existsByShortName(String shortName);
}
