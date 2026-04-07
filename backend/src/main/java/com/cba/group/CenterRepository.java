package com.cba.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CenterRepository extends JpaRepository<Center, UUID> {
    List<Center> findByOfficeId(UUID officeId);
    List<Center> findByStatus(Center.Status status);
}
