package com.employee.app.web;

/** The single JSON shape for success and error notices: {"message": "..."}. */
public record ApiMessage(String message) {
}
