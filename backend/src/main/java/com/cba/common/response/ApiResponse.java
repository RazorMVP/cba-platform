package com.cba.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard API response envelope for all CBA endpoints.
 * <pre>
 * {
 *   "data": { ... },
 *   "meta": { "page": 0, "size": 20, "total": 150 },
 *   "errors": []
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        T data,
        PageMeta meta,
        List<ApiError> errors
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, List.of());
    }

    public static <T> ApiResponse<T> ok(T data, PageMeta meta) {
        return new ApiResponse<>(data, meta, List.of());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(null, null, List.of(new ApiError(code, message, null)));
    }

    public static <T> ApiResponse<T> errors(List<ApiError> errors) {
        return new ApiResponse<>(null, null, errors);
    }

    public record ApiError(String code, String message, String field) {}

    public record PageMeta(long page, long size, long total, long totalPages) {
        public static PageMeta of(long page, long size, long total) {
            long totalPages = size > 0 ? (long) Math.ceil((double) total / size) : 0;
            return new PageMeta(page, size, total, totalPages);
        }
    }
}
