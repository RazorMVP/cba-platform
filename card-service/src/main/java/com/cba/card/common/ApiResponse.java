package com.cba.card.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard CBA response envelope: { "data": ..., "meta": ..., "errors": [] }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, Meta meta, List<ApiError> errors) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, null);
    }

    public static <T> ApiResponse<T> paged(T data, int page, int size, long total) {
        return new ApiResponse<>(data, new Meta(page, size, total), null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(null, null, List.of(new ApiError(code, message, null)));
    }

    public static <T> ApiResponse<T> errors(List<ApiError> errs) {
        return new ApiResponse<>(null, null, errs);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(int page, int size, long total) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(String code, String message, String field) {}
}
