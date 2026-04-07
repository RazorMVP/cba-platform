package com.cba.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

public record BatchRequest(
        @NotNull @Positive int requestId,
        @NotBlank String method,
        @NotBlank String relativeUrl,
        Integer reference,
        List<Map<String, String>> headers,
        String body
) {}
