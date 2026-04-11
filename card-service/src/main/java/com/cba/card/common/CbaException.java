package com.cba.card.common;

import org.springframework.http.HttpStatus;

public class CbaException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    private CbaException(String code, String message, HttpStatus status) {
        super(message);
        this.code   = code;
        this.status = status;
    }

    public static CbaException notFound(String code, String message) {
        return new CbaException(code, message, HttpStatus.NOT_FOUND);
    }

    public static CbaException badRequest(String code, String message) {
        return new CbaException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static CbaException conflict(String code, String message) {
        return new CbaException(code, message, HttpStatus.CONFLICT);
    }

    public static CbaException forbidden(String code, String message) {
        return new CbaException(code, message, HttpStatus.FORBIDDEN);
    }

    public String getCode()       { return code;   }
    public HttpStatus getStatus() { return status; }
}
