package com.cooking.app.service;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    public final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException missing(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException denied(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }
}
