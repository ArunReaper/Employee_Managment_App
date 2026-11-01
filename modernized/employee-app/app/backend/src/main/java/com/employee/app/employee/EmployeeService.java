package com.employee.app.employee;

import static com.employee.app.web.ApiExceptionHandler.INVALID_DATA;

import java.util.List;

import com.employee.app.web.ApiException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Employee business rules, from the specification in BUSINESS_RULES.md rather than the servlet
 * structure. The legacy checks and their order are kept; the deviations approved on 2026-10-31
 * (clear error messages) are marked.
 */
@Service
public class EmployeeService {

    static final int MAX_TEXT = 255; // VARCHAR(255) in schema.sql

    static final String ID_NOT_WHOLE = "Employee ID must be a whole number, e.g. 101.";
    static final String SALARY_NOT_NUMBER = "Salary must be a number, e.g. 15000.";
    static final String NOT_FOUND = "Employee not found!";

    private final EmployeeRepository employees;

    public EmployeeService(EmployeeRepository employees) {
        this.employees = employees;
    }

    // RULE-023, RULE-002 (via EmployeeView); a read failure propagates as 503 (replaces RULE-025)
    public List<EmployeeView> list() {
        return employees.findAll().stream().map(EmployeeView::of).toList();
    }

    // RULE-003/004 (checks and their order), RULE-005 (unique id), RULE-010 (server accepts id <= 0)
    public void add(EmployeeRequests.Add request) {
        if (isBlank(request.id()) || isBlank(request.name()) || isBlank(request.department()) || isBlank(request.salary())) {
            throw ApiException.badRequest("Please fill in all fields.");
        }
        int id = parseId(request.id().trim());
        double salary = parseSalary(request.salary().trim());
        if (salary < 0) { // NaN passes, as in legacy (NaN < 0 is false)
            throw ApiException.badRequest("Salary cannot be negative.");
        }
        String name = request.name().trim();
        String department = request.department().trim();
        if (name.length() > MAX_TEXT || department.length() > MAX_TEXT) { // deviation: legacy gave a generic error
            throw ApiException.badRequest("Name and department must be 255 characters or fewer.");
        }
        try {
            employees.insert(new Employee(id, name, department, salary));
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("An employee with this ID already exists. Please choose another ID.");
        } catch (DataAccessResourceFailureException e) {
            throw e; // 503
        } catch (DataAccessException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error while adding employee. Please try again.", e);
        }
    }

    // RULE-006/007/016 (confirmed): located by id only, the name is required but never compared,
    // and a salary edit has no range check. Distinct error messages are the approved deviation.
    public void edit(String idText, EmployeeRequests.Edit request) {
        if (request.name() == null || request.field() == null || request.newValue() == null) {
            throw ApiException.badRequest(INVALID_DATA);
        }
        int id = parseId(idText);
        int updated;
        if ("salary".equalsIgnoreCase(request.field())) {
            updated = employees.updateSalary(id, parseSalary(request.newValue()));
        } else if ("department".equalsIgnoreCase(request.field())) {
            if (request.newValue().length() > MAX_TEXT) {
                throw ApiException.badRequest("Department must be 255 characters or fewer.");
            }
            updated = employees.updateDepartment(id, request.newValue()); // stored as given, not trimmed
        } else {
            throw ApiException.badRequest("Field must be either salary or department.");
        }
        if (updated == 0) {
            throw ApiException.notFound(NOT_FOUND);
        }
    }

    // RULE-013: exact id + name match; a miss is now reported (replaces RULE-014)
    public void delete(String idText, String name) {
        if (name == null) {
            throw ApiException.badRequest(INVALID_DATA);
        }
        if (employees.deleteByIdAndName(parseId(idText), name) == 0) {
            throw ApiException.notFound(NOT_FOUND);
        }
    }

    private static int parseId(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(ID_NOT_WHOLE);
        }
    }

    private static double parseSalary(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(SALARY_NOT_NUMBER);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
