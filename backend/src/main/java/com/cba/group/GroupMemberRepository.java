package com.cba.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {
    List<GroupMember> findByGroupIdAndActiveTrue(UUID groupId);
    List<GroupMember> findByCustomerIdAndActiveTrue(UUID customerId);
    boolean existsByGroupIdAndCustomerId(UUID groupId, UUID customerId);
}
