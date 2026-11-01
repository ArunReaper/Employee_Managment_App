package com.employee.app.employee;

/**
 * An employee as the list shows it. {@code salaryText} is Java's Double.toString, which is what
 * showEmployee.jsp printed (RULE-002): 15000.0, 1.0E7, NaN, Infinity. {@code salary} is null when
 * the value is not finite, because JSON cannot carry NaN or Infinity.
 */
public record EmployeeView(int id, String name, String department, Double salary, String salaryText) {

    static EmployeeView of(Employee e) {
        Double salary = Double.isFinite(e.salary()) ? e.salary() : null;
        return new EmployeeView(e.id(), e.name(), e.department(), salary, Double.toString(e.salary()));
    }
}
