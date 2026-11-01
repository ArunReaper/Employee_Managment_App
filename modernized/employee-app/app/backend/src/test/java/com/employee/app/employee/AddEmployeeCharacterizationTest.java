package com.employee.app.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employee.app.support.CharacterizationTestBase;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * POST /api/employees. Legacy: AddEmployeeServlet. Checks run in this order and the first failure wins:
 * blank -> Integer.parseInt(id.trim()) -> Double.parseDouble(salary.trim()) -> salary < 0 ->
 * (deviation) length > 255 -> insert (23505 -> duplicate, other -> generic error).
 */
@DisplayName("POST /api/employees (legacy AddEmployeeServlet)")
class AddEmployeeCharacterizationTest extends CharacterizationTestBase {

    private static final String FILL_ALL = "Please fill in all fields.";
    private static final String BAD_ID = "Employee ID must be a whole number, e.g. 101.";
    private static final String BAD_SALARY = "Salary must be a number, e.g. 15000.";
    private static final String NEGATIVE = "Salary cannot be negative.";
    private static final String TOO_LONG = "Name and department must be 255 characters or fewer.";
    private static final String DUPLICATE = "An employee with this ID already exists. Please choose another ID.";
    private static final String ADDED = "Employee added successfully!";

    private String body(String id, String name, String department, String salary) {
        return json("id", id, "name", name, "department", department, "salary", salary);
    }

    private void expectRejected(String requestBody, String message) throws Exception {
        addEmployee(requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));
        assertThat(employeeCount()).as("nothing stored").isZero();
    }

    // ---------------------------------------------------------------- success

    // RULE-003, RULE-004
    @Test
    void rule003_validEmployeeIsStoredAndReported() throws Exception {
        addEmployee(body("101", "Asha", "Finance", "15000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(ADDED));

        Map<String, Object> row = employeeRow(101);
        assertThat(row.get("name")).isEqualTo("Asha");
        assertThat(row.get("department")).isEqualTo("Finance");
        assertThat(storedSalary(101)).isEqualTo(15000.0);
        assertThat(employeeCount()).isEqualTo(1);
    }

    // RULE-003, RULE-004: id and salary are trimmed before parsing; name and department are stored trimmed
    @Test
    void rule004_allFieldsAreTrimmedBeforeParsingAndStoring() throws Exception {
        addEmployee(body(" 205 ", "  Ravi ", " Finance  ", " 15000.50 "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(ADDED));

        Map<String, Object> row = employeeRow(205);
        assertThat(row.get("name")).isEqualTo("Ravi");
        assertThat(row.get("department")).isEqualTo("Finance");
        assertThat(storedSalary(205)).isEqualTo(15000.5);
    }

    // RULE-003: inner whitespace is kept, only the ends are trimmed
    @Test
    void rule003_innerWhitespaceIsKept() throws Exception {
        addEmployee(body("101", " Asha  Rao ", " Human  Resources ", "1"))
                .andExpect(status().isCreated());

        Map<String, Object> row = employeeRow(101);
        assertThat(row.get("name")).isEqualTo("Asha  Rao");
        assertThat(row.get("department")).isEqualTo("Human  Resources");
    }

    // ---------------------------------------------------------------- 1. blank / missing

    // RULE-004: any field missing -> fill-in message
    @ParameterizedTest(name = "RULE-004 missing {0}")
    @ValueSource(strings = {"id", "name", "department", "salary"})
    void rule004_missingFieldIsRejected(String missing) throws Exception {
        String requestBody = json(
                "id".equals(missing) ? "x-id" : "id", "101",
                "name".equals(missing) ? "x-name" : "name", "Asha",
                "department".equals(missing) ? "x-department" : "department", "Finance",
                "salary".equals(missing) ? "x-salary" : "salary", "15000");
        expectRejected(requestBody, FILL_ALL);
    }

    // RULE-004: JSON null counts as missing
    @ParameterizedTest(name = "RULE-004 null {0}")
    @ValueSource(strings = {"id", "name", "department", "salary"})
    void rule004_nullFieldIsRejected(String nulled) throws Exception {
        String requestBody = body(
                "id".equals(nulled) ? null : "101",
                "name".equals(nulled) ? null : "Asha",
                "department".equals(nulled) ? null : "Finance",
                "salary".equals(nulled) ? null : "15000");
        expectRejected(requestBody, FILL_ALL);
    }

    static Stream<Arguments> blankFields() {
        return Stream.of(
                Arguments.of("id", ""), Arguments.of("id", "   "),
                Arguments.of("name", ""), Arguments.of("name", "   "), Arguments.of("name", "\t"),
                Arguments.of("department", ""), Arguments.of("department", "  "),
                Arguments.of("salary", ""), Arguments.of("salary", " "));
    }

    // RULE-004: blank or whitespace-only after trim
    @ParameterizedTest(name = "RULE-004 {0}=\"{1}\"")
    @MethodSource("blankFields")
    void rule004_blankOrWhitespaceFieldIsRejected(String field, String value) throws Exception {
        String requestBody = body(
                "id".equals(field) ? value : "101",
                "name".equals(field) ? value : "Asha",
                "department".equals(field) ? value : "Finance",
                "salary".equals(field) ? value : "15000");
        expectRejected(requestBody, FILL_ALL);
    }

    // RULE-004: the blank check runs before the ID check
    @Test
    void rule004_blankCheckWinsOverInvalidId() throws Exception {
        expectRejected(body("abc", "  ", "Finance", "15000"), FILL_ALL);
    }

    // ---------------------------------------------------------------- 2. ID parse

    // RULE-004, RULE-003: Integer.parseInt(id.trim()) rejects these
    @ParameterizedTest(name = "RULE-004 id \"{0}\" rejected")
    @ValueSource(strings = {"10.5", "abc", "1e3", "101abc", "2147483648", "-2147483649", "0x10", "1 01", "101L"})
    void rule004_idThatIsNotAWholeNumberIsRejected(String id) throws Exception {
        expectRejected(body(id, "Asha", "Finance", "15000"), BAD_ID);
    }

    // RULE-004: ID check runs before the salary check
    @Test
    void rule004_invalidIdWinsOverInvalidSalary() throws Exception {
        expectRejected(body("abc", "Asha", "Finance", "xyz"), BAD_ID);
    }

    static Stream<Arguments> acceptedIds() {
        return Stream.of(
                Arguments.of("0", 0),
                Arguments.of("-5", -5),
                Arguments.of("+7", 7),
                Arguments.of("007", 7),
                Arguments.of("2147483647", 2147483647),
                Arguments.of("-2147483648", -2147483648));
    }

    // RULE-010: the server accepts any Integer.parseInt value, including 0, negative and "+7"
    @ParameterizedTest(name = "RULE-010 id \"{0}\" stored as {1}")
    @MethodSource("acceptedIds")
    void rule010_serverAcceptsAnyParseableInteger(String id, int storedId) throws Exception {
        addEmployee(body(id, "Asha", "Finance", "15000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(ADDED));
        assertThat(employeeExists(storedId)).isTrue();
        assertThat(employeeCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 3. salary parse

    // RULE-004: Double.parseDouble(salary.trim()) rejects these
    @ParameterizedTest(name = "RULE-004 salary \"{0}\" rejected")
    @ValueSource(strings = {"abc", "15,000", "$15000", "15000abc", "nan", "infinity", "1e", "--5"})
    void rule004_salaryThatIsNotANumberIsRejected(String salary) throws Exception {
        expectRejected(body("101", "Asha", "Finance", salary), BAD_SALARY);
    }

    // RULE-004: the format check runs before the negative check
    @Test
    void rule004_salaryFormatCheckWinsOverNegativeCheck() throws Exception {
        expectRejected(body("101", "Asha", "Finance", "-abc"), BAD_SALARY);
    }

    // ---------------------------------------------------------------- 4. negative

    // RULE-003, RULE-004: salary < 0 rejected
    @ParameterizedTest(name = "RULE-004 salary \"{0}\" is negative")
    @ValueSource(strings = {"-500", "-0.01", "-1e3", "-Infinity", "-4.9E-324"})
    void rule004_negativeSalaryIsRejected(String salary) throws Exception {
        expectRejected(body("101", "Asha", "Finance", salary), NEGATIVE);
    }

    static Stream<Arguments> acceptedSalaries() {
        return Stream.of(
                Arguments.of("0", 0.0),
                Arguments.of("0.00", 0.0),
                Arguments.of("1e3", 1000.0),
                Arguments.of("15000.005", 15000.005),
                Arguments.of("1000.123", 1000.123),
                Arguments.of("10000000", 1.0E7),
                Arguments.of("+250", 250.0),
                Arguments.of(".5", 0.5),
                Arguments.of("1000d", 1000.0),
                Arguments.of("1000f", 1000.0),
                Arguments.of("0x1p3", 8.0),
                Arguments.of("Infinity", Double.POSITIVE_INFINITY),
                Arguments.of("NaN", Double.NaN));
    }

    // RULE-003, RULE-004, RULE-001, RULE-010: what Double.parseDouble accepts and is not < 0 is stored unrounded.
    // "NaN" passes because NaN < 0 is false; "1000d" / "0x1p3" are valid Java double literals.
    @ParameterizedTest(name = "RULE-004 salary \"{0}\" stored as {1}")
    @MethodSource("acceptedSalaries")
    void rule004_parseableNonNegativeSalaryIsStoredUnrounded(String salary, double stored) throws Exception {
        addEmployee(body("101", "Asha", "Finance", salary))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(ADDED));
        assertStoredSalary(101, stored);
    }

    // RULE-001, RULE-002: what was added is listed with Double.toString text
    @Test
    void rule001_addedSalaryIsListedWithRawDoubleText() throws Exception {
        addEmployee(body("101", "Asha", "Finance", "15000.005")).andExpect(status().isCreated());
        addEmployee(body("102", "Big", "Finance", "10000000")).andExpect(status().isCreated());

        listEmployees()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 101)].salaryText").value("15000.005"))
                .andExpect(jsonPath("$[?(@.id == 102)].salaryText").value("1.0E7"));
    }

    // ---------------------------------------------------------------- 5. length (deviation)

    // RULE-019 (VARCHAR(255)); deviation approved 2026-10-31
    @Test
    void rule019_nameLongerThan255IsRejected() throws Exception {
        expectRejected(body("101", repeat('n', 256), "Finance", "15000"), TOO_LONG);
    }

    // RULE-019; deviation approved 2026-10-31
    @Test
    void rule019_departmentLongerThan255IsRejected() throws Exception {
        expectRejected(body("101", "Asha", repeat('d', 256), "15000"), TOO_LONG);
    }

    // RULE-019: exactly 255 characters is accepted, and the limit applies after trimming
    @Test
    void rule019_exactly255CharactersAfterTrimIsAccepted() throws Exception {
        String name255 = repeat('n', 255);
        String dept255 = repeat('d', 255);
        addEmployee(body("101", "  " + name255 + "  ", " " + dept255 + " ", "15000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(ADDED));

        Map<String, Object> row = employeeRow(101);
        assertThat(row.get("name")).isEqualTo(name255);
        assertThat(row.get("department")).isEqualTo(dept255);
    }

    // RULE-004 order, RULE-019: the negative check runs before the length check; deviation approved 2026-10-31
    @Test
    void rule004_negativeSalaryWinsOverTooLongName() throws Exception {
        expectRejected(body("101", repeat('n', 256), "Finance", "-1"), NEGATIVE);
    }

    // ---------------------------------------------------------------- 6. duplicate

    // RULE-005: duplicate id -> 409, existing row untouched
    @Test
    void rule005_duplicateIdIsRejectedWithConflictAndExistingRowIsUnchanged() throws Exception {
        seedEmployee(101, "Priya", "Sales", 50000.0);

        addEmployee(body("101", "Asha", "Finance", "15000"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(DUPLICATE));

        assertThat(employeeCount()).isEqualTo(1);
        Map<String, Object> row = employeeRow(101);
        assertThat(row.get("name")).isEqualTo("Priya");
        assertThat(row.get("department")).isEqualTo("Sales");
        assertThat(storedSalary(101)).isEqualTo(50000.0);
    }

    // RULE-005: the duplicate is detected after trimming / parsing the id (" 0101 " == 101)
    @Test
    void rule005_duplicateDetectedOnParsedId() throws Exception {
        addEmployee(body("101", "Asha", "Finance", "15000")).andExpect(status().isCreated());

        addEmployee(body(" 0101 ", "Other", "HR", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(DUPLICATE));
        assertThat(employeeCount()).isEqualTo(1);
        assertThat(employeeRow(101).get("name")).isEqualTo("Asha");
    }

    // RULE-005: validation failures are reported before the duplicate check
    @Test
    void rule005_validationRunsBeforeDuplicateCheck() throws Exception {
        seedEmployee(101, "Priya", "Sales", 50000.0);

        addEmployee(body("101", "Asha", "Finance", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(NEGATIVE));
        assertThat(employeeRow(101).get("name")).isEqualTo("Priya");
    }

    // RULE-012 edge applies to employees too: names need not be unique
    @Test
    void rule005_sameNameDifferentIdIsAllowed() throws Exception {
        addEmployee(body("101", "Asha", "Finance", "1")).andExpect(status().isCreated());
        addEmployee(body("102", "Asha", "Finance", "1")).andExpect(status().isCreated());
        assertThat(employeeCount()).isEqualTo(2);
    }
}
