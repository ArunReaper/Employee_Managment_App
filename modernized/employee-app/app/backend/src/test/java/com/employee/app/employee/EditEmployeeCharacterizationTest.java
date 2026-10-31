package com.employee.app.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employee.app.support.CharacterizationTestBase;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PATCH /api/employees/{id}. Legacy: EditEmployeeServlet + EmployeeDAO.updateEmployee.
 * Order: null check (name, field, newValue) -> parseInt(id) -> field -> parseDouble -> length -> row exists.
 * The employee is found by ID only; name is required but never compared (RULE-016, confirmed).
 */
@DisplayName("PATCH /api/employees/{id} (legacy EditEmployeeServlet)")
class EditEmployeeCharacterizationTest extends CharacterizationTestBase {

    private static final String INVALID = "Invalid data! Please try again.";
    private static final String BAD_ID = "Employee ID must be a whole number, e.g. 101.";
    private static final String BAD_FIELD = "Field must be either salary or department.";
    private static final String BAD_SALARY = "Salary must be a number, e.g. 15000.";
    private static final String DEPT_TOO_LONG = "Department must be 255 characters or fewer.";
    private static final String NOT_FOUND = "Employee not found!";
    private static final String UPDATED = "Employee details updated successfully!";

    @BeforeEach
    void seedPriya() {
        seedEmployee(101, "Priya", "Finance", 15000.0);
    }

    private String body(String name, String field, String newValue) {
        return json("name", name, "field", field, "newValue", newValue);
    }

    private void expectOk(String pathId, String requestBody) throws Exception {
        editEmployee(pathId, requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(UPDATED));
    }

    private void expectUnchanged() {
        Map<String, Object> row = employeeRow(101);
        assertThat(row.get("name")).isEqualTo("Priya");
        assertThat(row.get("department")).isEqualTo("Finance");
        assertThat(storedSalary(101)).isEqualTo(15000.0);
        assertThat(employeeCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- success paths

    // RULE-016: salary changes; name, department untouched
    @Test
    void rule016_salaryIsUpdatedById() throws Exception {
        expectOk("101", body("Priya", "salary", "18000"));

        Map<String, Object> row = employeeRow(101);
        assertThat(storedSalary(101)).isEqualTo(18000.0);
        assertThat(row.get("name")).isEqualTo("Priya");
        assertThat(row.get("department")).isEqualTo("Finance");
    }

    // RULE-016: department changes; salary untouched
    @Test
    void rule016_departmentIsUpdatedById() throws Exception {
        expectOk("101", body("Priya", "department", "HR"));

        assertThat(employeeRow(101).get("department")).isEqualTo("HR");
        assertThat(storedSalary(101)).isEqualTo(15000.0);
    }

    // RULE-016, RULE-006: the supplied name is never compared with the stored name
    @Test
    void rule016_nameIsRequiredButNeverCompared() throws Exception {
        expectOk("101", body("Anyone", "salary", "18000"));
        assertThat(storedSalary(101)).isEqualTo(18000.0);
        assertThat(employeeRow(101).get("name")).isEqualTo("Priya");
    }

    // RULE-006: an empty name is not rejected (null-only check) and still not compared
    @Test
    void rule006_emptyNameIsAccepted() throws Exception {
        expectOk("101", body("", "department", "Sales"));
        assertThat(employeeRow(101).get("department")).isEqualTo("Sales");
    }

    // RULE-016: only the targeted row changes
    @Test
    void rule016_otherRowsAreNotTouched() throws Exception {
        seedEmployee(102, "Ravi", "Ops", 9000.0);

        expectOk("101", body("Priya", "salary", "18000"));

        assertThat(storedSalary(102)).isEqualTo(9000.0);
        assertThat(employeeRow(102).get("department")).isEqualTo("Ops");
    }

    // RULE-016: field is compared case-insensitively (equalsIgnoreCase)
    @ParameterizedTest(name = "RULE-016 field \"{0}\" means salary")
    @ValueSource(strings = {"salary", "SALARY", "Salary", "sAlArY"})
    void rule016_salaryFieldIsCaseInsensitive(String field) throws Exception {
        expectOk("101", body("Priya", field, "20000"));
        assertThat(storedSalary(101)).isEqualTo(20000.0);
    }

    // RULE-016
    @ParameterizedTest(name = "RULE-016 field \"{0}\" means department")
    @ValueSource(strings = {"department", "DEPARTMENT", "Department"})
    void rule016_departmentFieldIsCaseInsensitive(String field) throws Exception {
        expectOk("101", body("Priya", field, "Legal"));
        assertThat(employeeRow(101).get("department")).isEqualTo("Legal");
    }

    static Stream<Arguments> editSalaries() {
        return Stream.of(
                Arguments.of("-5000", -5000.0),
                Arguments.of("-500", -500.0),
                Arguments.of("NaN", Double.NaN),
                Arguments.of("Infinity", Double.POSITIVE_INFINITY),
                Arguments.of("-Infinity", Double.NEGATIVE_INFINITY),
                Arguments.of(" 18000 ", 18000.0),
                Arguments.of("1e3", 1000.0),
                Arguments.of("0", 0.0),
                Arguments.of("15000.005", 15000.005));
    }

    // RULE-007, RULE-016 (confirmed): no range check on edit; parseDouble ignores surrounding whitespace
    @ParameterizedTest(name = "RULE-007 newValue \"{0}\" stored as {1}")
    @MethodSource("editSalaries")
    void rule007_anyParseableSalaryIsStoredOnEdit(String newValue, double stored) throws Exception {
        expectOk("101", body("Priya", "salary", newValue));
        assertStoredSalary(101, stored);
    }

    // RULE-007, RULE-002: a NaN stored through edit is listed as "NaN" with salary null
    @Test
    void rule007_nanSavedThroughEditIsListedAsNaN() throws Exception {
        expectOk("101", body("Priya", "salary", "NaN"));

        listEmployees()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].salaryText").value("NaN"))
                .andExpect(jsonPath("$[0].salary").value(nullValue()));
    }

    // RULE-011: department is stored exactly as given (not trimmed, case kept)
    @Test
    void rule011_departmentIsStoredUntrimmed() throws Exception {
        expectOk("101", body("Priya", "department", "  sales "));
        assertThat(employeeRow(101).get("department")).isEqualTo("  sales ");
    }

    // RULE-006, RULE-016: an empty or whitespace department is allowed
    @ParameterizedTest(name = "RULE-006 department \"{0}\" stored as-is")
    @ValueSource(strings = {"", "   "})
    void rule006_emptyOrBlankDepartmentIsAllowed(String newValue) throws Exception {
        expectOk("101", body("Priya", "department", newValue));
        assertThat(employeeRow(101).get("department")).isEqualTo(newValue);
    }

    // RULE-011: with field=department a numeric-looking value is stored as text
    @Test
    void rule011_numericTextCanBeStoredAsDepartment() throws Exception {
        expectOk("101", body("Priya", "department", "18000"));
        assertThat(employeeRow(101).get("department")).isEqualTo("18000");
        assertThat(storedSalary(101)).isEqualTo(15000.0);
    }

    // RULE-010: a negative id that exists can be edited
    @Test
    void rule010_negativeIdCanBeEdited() throws Exception {
        seedEmployee(-5, "Neg", "Ops", 1.0);
        expectOk("-5", body("Neg", "salary", "2"));
        assertThat(storedSalary(-5)).isEqualTo(2.0);
    }

    // RULE-019: 255-character department is accepted
    @Test
    void rule019_departmentOf255CharactersIsAccepted() throws Exception {
        String dept = repeat('d', 255);
        expectOk("101", body("Priya", "department", dept));
        assertThat(employeeRow(101).get("department")).isEqualTo(dept);
    }

    // ---------------------------------------------------------------- 1. null / missing

    // RULE-006: name, field or newValue null -> Invalid data
    @ParameterizedTest(name = "RULE-006 null {0}")
    @ValueSource(strings = {"name", "field", "newValue"})
    void rule006_nullParameterIsRejected(String nulled) throws Exception {
        String requestBody = body(
                "name".equals(nulled) ? null : "Priya",
                "field".equals(nulled) ? null : "salary",
                "newValue".equals(nulled) ? null : "18000");
        editEmployee("101", requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID));
        expectUnchanged();
    }

    // RULE-006: missing key behaves like null
    @ParameterizedTest(name = "RULE-006 missing {0}")
    @ValueSource(strings = {"name", "field", "newValue"})
    void rule006_missingParameterIsRejected(String missing) throws Exception {
        String requestBody = json(
                "name".equals(missing) ? "x-name" : "name", "Priya",
                "field".equals(missing) ? "x-field" : "field", "salary",
                "newValue".equals(missing) ? "x-newValue" : "newValue", "18000");
        editEmployee("101", requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID));
        expectUnchanged();
    }

    // RULE-006: the null check runs before the ID check
    @Test
    void rule006_nullCheckWinsOverNonNumericId() throws Exception {
        editEmployee("abc", body(null, "salary", "18000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID));
        expectUnchanged();
    }

    // ---------------------------------------------------------------- 2. path id

    // RULE-006; deviation approved 2026-10-31 (legacy: HTTP 500 from an uncaught NumberFormatException)
    @ParameterizedTest(name = "RULE-006 path id \"{0}\" rejected")
    @ValueSource(strings = {"abc", "10.5", "2147483648"})
    void rule006_nonNumericIdIsRejected(String pathId) throws Exception {
        editEmployee(pathId, body("Priya", "salary", "18000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(BAD_ID));
        expectUnchanged();
    }

    // RULE-006: the ID check runs before the field check; deviation approved 2026-10-31
    @Test
    void rule006_nonNumericIdWinsOverUnknownField() throws Exception {
        editEmployee("abc", body("Priya", "name", "X"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(BAD_ID));
    }

    // ---------------------------------------------------------------- 3. field

    // RULE-016: only salary and department can change; deviation approved 2026-10-31 (message text)
    @ParameterizedTest(name = "RULE-016 field \"{0}\" rejected")
    @ValueSource(strings = {"name", "id", "", " salary", "salary ", "dept", "salaries"})
    void rule016_unknownFieldIsRejectedAndNothingChanges(String field) throws Exception {
        editEmployee("101", body("Priya", field, "Mallory"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(BAD_FIELD));
        expectUnchanged();
    }

    // RULE-016: the field check runs before the row lookup; deviation approved 2026-10-31
    @Test
    void rule016_unknownFieldWinsOverUnknownId() throws Exception {
        editEmployee("999", body("Priya", "name", "X"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(BAD_FIELD));
    }

    // ---------------------------------------------------------------- 4. salary parse

    // RULE-007, RULE-016; deviation approved 2026-10-31 (legacy text "Employee not found or invalid data!")
    @ParameterizedTest(name = "RULE-007 salary newValue \"{0}\" rejected")
    @ValueSource(strings = {"abc", "", "  ", "15,000", "nan"})
    void rule007_nonNumericSalaryIsRejected(String newValue) throws Exception {
        editEmployee("101", body("Priya", "salary", newValue))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(BAD_SALARY));
        expectUnchanged();
    }

    // RULE-007: the salary parse runs before the row lookup; deviation approved 2026-10-31
    @Test
    void rule007_nonNumericSalaryWinsOverUnknownId() throws Exception {
        editEmployee("999", body("Priya", "salary", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(BAD_SALARY));
    }

    // ---------------------------------------------------------------- 5. length (deviation)

    // RULE-019; deviation approved 2026-10-31
    @Test
    void rule019_departmentLongerThan255IsRejected() throws Exception {
        editEmployee("101", body("Priya", "department", repeat('d', 256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(DEPT_TOO_LONG));
        expectUnchanged();
    }

    // RULE-019: length is checked on the untrimmed value; deviation approved 2026-10-31
    @Test
    void rule019_departmentLengthCountsSurroundingSpaces() throws Exception {
        editEmployee("101", body("Priya", "department", " " + repeat('d', 255)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(DEPT_TOO_LONG));
        expectUnchanged();
    }

    // RULE-019: the length check runs before the row lookup; deviation approved 2026-10-31
    @Test
    void rule019_tooLongDepartmentWinsOverUnknownId() throws Exception {
        editEmployee("999", body("Priya", "department", repeat('d', 256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(DEPT_TOO_LONG));
    }

    // ---------------------------------------------------------------- 6. not found

    // RULE-016; deviation approved 2026-10-31 (legacy text "Employee not found or invalid data!")
    @ParameterizedTest(name = "RULE-016 unknown id with field {0}")
    @ValueSource(strings = {"salary", "department"})
    void rule016_unknownIdGives404(String field) throws Exception {
        editEmployee("999", body("Priya", field, "18000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_FOUND));
        expectUnchanged();
        assertThat(employeeExists(999)).as("edit never inserts").isFalse();
    }
}
