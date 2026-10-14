package com.cba.common.exception;

import com.cba.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CbaException.class)
    public ResponseEntity<ApiResponse<Void>> handleCbaException(CbaException ex) {
        log.warn("CBA domain exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
            .status(ex.getHttpStatus())
            .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiResponse.ApiError> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> new ApiResponse.ApiError(
                "VALIDATION_ERROR",
                fe.getDefaultMessage(),
                fe.getField()
            ))
            .toList();
        return ResponseEntity.badRequest().body(ApiResponse.errors(errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiResponse.ApiError> errors = ex.getConstraintViolations()
            .stream()
            .map(cv -> new ApiResponse.ApiError(
                "VALIDATION_ERROR",
                cv.getMessage(),
                cv.getPropertyPath().toString()
            ))
            .toList();
        return ResponseEntity.badRequest().body(ApiResponse.errors(errors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("ACCESS_DENIED", "You do not have permission to perform this action"));
    }

    /** A wrong HTTP method is the caller's mistake: 405 with Allow, not a 500 from the catch-all below. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        var response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        var supported = ex.getSupportedHttpMethods();
        if (supported != null) {
            response.allow(supported.toArray(org.springframework.http.HttpMethod[]::new));
        }
        return response.body(ApiResponse.error("METHOD_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
            .internalServerError()
            .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
