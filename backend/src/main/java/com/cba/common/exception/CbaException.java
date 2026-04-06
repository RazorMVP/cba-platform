package com.cba.common.exception;

import org.springframework.http.HttpStatus;

public class CbaException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public CbaException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }

    // ── Common factory methods ──────────────────────────────────────

    public static CbaException notFound(String entity, Object id) {
        return new CbaException(
            entity.toUpperCase() + "_NOT_FOUND",
            entity + " not found: " + id,
            HttpStatus.NOT_FOUND
        );
    }

    public static CbaException conflict(String code, String message) {
        return new CbaException(code, message, HttpStatus.CONFLICT);
    }

    public static CbaException badRequest(String code, String message) {
        return new CbaException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static CbaException forbidden(String message) {
        return new CbaException("ACCESS_DENIED", message, HttpStatus.FORBIDDEN);
    }
}
