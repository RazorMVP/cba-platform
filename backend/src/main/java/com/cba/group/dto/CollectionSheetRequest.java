package com.cba.group.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CollectionSheetRequest(
        @NotNull UUID groupId,
        @NotNull LocalDate meetingDate
) {}
