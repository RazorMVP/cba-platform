package com.cba.batch.dto;

import java.util.List;
import java.util.Map;

public record BatchResponse(
        int requestId,
        int statusCode,
        List<Map<String, String>> headers,
        String body
) {}
