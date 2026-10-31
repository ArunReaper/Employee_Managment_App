package com.employee.app.web;

import org.springframework.http.HttpStatus;

/**
 * A request the user can correct, or an outcome the user must be told about, with the exact
 * message to show. Replaces the legacy {@code <script>alert('...')</script>} responses.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
