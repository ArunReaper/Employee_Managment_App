package com.employee.app.employee;

/**
 * One row of employee_data (schema unchanged). The id is chosen by the user, not generated
 * (RULE-019), and salary stays a double to match DOUBLE PRECISION (RULE-001).
 */
public record Employee(int id, String name, String department, double salary) {
}
