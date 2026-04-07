package com.cba.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionSheetRepository extends JpaRepository<CollectionSheet, UUID> {
    List<CollectionSheet> findByGroupId(UUID groupId);
    Optional<CollectionSheet> findByGroupIdAndMeetingDate(UUID groupId, LocalDate meetingDate);
}
