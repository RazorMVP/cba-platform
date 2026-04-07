package com.cba.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {
    List<Group> findByOfficeId(UUID officeId);
    List<Group> findByCenterId(UUID centerId);
    List<Group> findByStatus(Group.Status status);
}
