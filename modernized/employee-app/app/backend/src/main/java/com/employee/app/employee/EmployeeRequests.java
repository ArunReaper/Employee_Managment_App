package com.employee.app.employee;

/**
 * Request bodies. Every field is a string, as the legacy forms posted it, so that parsing and
 * validation follow the legacy rules exactly (Integer.parseInt and Double.parseDouble).
 */
public final class EmployeeRequests {

    private EmployeeRequests() {
    }

    public record Add(String id, String name, String department, String salary) {
    }

    public record Edit(String name, String field, String newValue) {
    }
}
