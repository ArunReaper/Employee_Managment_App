package com.employee.app.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns every failure into the {"message": ...} shape of API_CONTRACT.md. The legacy app printed
 * stack traces, swallowed database errors as "not found", or showed Tomcat's error page; the user
 * asked for appropriate messages instead (deviation approved 2026-10-31). Every 5xx is logged with
 * its cause.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public static final String DATABASE_UNAVAILABLE = "We couldn't reach the database. Please try again later.";
    public static final String INVALID_DATA = "Invalid data! Please try again.";
    private static final String UNEXPECTED = "Something went wrong. Please try again.";
    private static final String NOT_FOUND = "That page or action does not exist.";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiMessage> handle(ApiException e) {
        if (e.status().is5xxServerError()) {
            log.error("{} -> {}", e.getMessage(), e.status(), e.getCause() != null ? e.getCause() : e);
        }
        return reply(e.status(), e.getMessage());
    }

    // Replaces RULE-025 (an outage looked like an empty list) and the "not found" disguise in the DAOs
    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<ApiMessage> databaseUnavailable(DataAccessResourceFailureException e) {
        log.warn("Database unavailable: {}", e.getMostSpecificCause().getMessage());
        return reply(HttpStatus.SERVICE_UNAVAILABLE, DATABASE_UNAVAILABLE);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiMessage> databaseError(DataAccessException e) {
        log.error("Database error", e);
        return reply(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiMessage> unreadableBody(HttpMessageNotReadableException e) {
        return reply(HttpStatus.BAD_REQUEST, INVALID_DATA);
    }

    // Spring MVC's own errors (unknown /api path, wrong method or content type) and anything unexpected
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiMessage> other(Exception e) {
        if (e instanceof ErrorResponse error && !error.getStatusCode().is5xxServerError()) {
            HttpStatusCode status = error.getStatusCode();
            return reply(status, status.value() == 404 ? NOT_FOUND : INVALID_DATA);
        }
        log.error("Unexpected error", e);
        return reply(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED);
    }

    private static ResponseEntity<ApiMessage> reply(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(new ApiMessage(message));
    }
}
