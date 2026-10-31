package com.employee.app.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employee.app.support.CharacterizationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * DELETE /api/employees/{id}?name=. Legacy: DeleteEmployeeServlet + EmployeeDAO.deleteEmployee
 * ("DELETE FROM employee_data WHERE id = ? AND name = ?": exact, case-sensitive, untrimmed).
 * Order: name missing -> parseInt(id) -> delete.
 */
@DisplayName("DELETE /api/employees/{id} (legacy DeleteEmployeeServlet)")
class DeleteEmployeeCharacterizationTest extends CharacterizationTestBase {

    private static final String INVALID = "Invalid data! Please try again.";
    private static final String BAD_ID = "Employee ID must be a whole number, e.g. 101.";
    private static final String NOT_FOUND = "Employee not found!";
    private static final String DELETED = "Employee deleted successfully!";

    @BeforeEach
    void seedAsha() {
        seedEmployee(101, "Asha", "Finance", 15000.0);
    }

    private void expectNotFoundAndAshaKept(String pathId, String name) throws Exception {
        // RULE-014 marked wrong by the reviewer; deviation approved 2026-10-31
        deleteEmployee(pathId, name)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_FOUND));
        assertThat(employeeExists(101)).as("Asha is not deleted").isTrue();
        assertThat(employeeCount()).isEqualTo(1);
    }

    // RULE-013: exact id + name match is hard-deleted
    @Test
    void rule013_exactIdAndNameDeletesTheRow() throws Exception {
        deleteEmployee("101", "Asha")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(DELETED));
        assertThat(employeeExists(101)).isFalse();
        assertThat(employeeCount()).isZero();
    }

    // RULE-013: only the matching row is deleted
    @Test
    void rule013_otherRowsWithTheSameNameAreKept() throws Exception {
        seedEmployee(102, "Asha", "HR", 1.0);

        deleteEmployee("101", "Asha").andExpect(status().isOk());

        assertThat(employeeExists(101)).isFalse();
        assertThat(employeeExists(102)).isTrue();
    }

    // RULE-013: the name match is case-sensitive; RULE-014 deviation approved 2026-10-31
    @ParameterizedTest(name = "RULE-013 name \"{0}\" does not match \"Asha\"")
    @ValueSource(strings = {"asha", "ASHA", "AshA"})
    void rule013_nameMatchIsCaseSensitive(String name) throws Exception {
        expectNotFoundAndAshaKept("101", name);
    }

    // RULE-013: the name is not trimmed, so surrounding spaces do not match; RULE-014 deviation approved 2026-10-31
    @ParameterizedTest(name = "RULE-013 name \"{0}\" does not match \"Asha\"")
    @ValueSource(strings = {" Asha", "Asha ", " Asha "})
    void rule013_untrimmedNameDoesNotMatch(String name) throws Exception {
        expectNotFoundAndAshaKept("101", name);
    }

    // RULE-013: a stored name with spaces must be given with the same spaces
    @Test
    void rule013_storedSpacesMustBeGivenExactly() throws Exception {
        seedEmployee(102, " Ravi ", "Ops", 1.0);

        deleteEmployee("102", "Ravi").andExpect(status().isNotFound());
        assertThat(employeeExists(102)).isTrue();

        deleteEmployee("102", " Ravi ").andExpect(status().isOk());
        assertThat(employeeExists(102)).isFalse();
    }

    // RULE-014 edge: an empty name passes the presence check and simply matches nothing; deviation approved 2026-10-31
    @Test
    void rule014_emptyNameMatchesNothing() throws Exception {
        expectNotFoundAndAshaKept("101", "");
    }

    // RULE-014: unknown id; deviation approved 2026-10-31 (legacy redirected as if it worked)
    @Test
    void rule014_unknownIdGives404() throws Exception {
        expectNotFoundAndAshaKept("999", "Asha");
    }

    // RULE-013 / RULE-019: a NULL stored name never matches (SQL name = ? is never true for NULL)
    @Test
    void rule013_nullStoredNameCanNeverBeDeleted() throws Exception {
        seedEmployee(102, null, "Ops", 1.0);

        deleteEmployee("102", "null")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_FOUND));
        deleteEmployee("102", "")
                .andExpect(status().isNotFound());
        assertThat(employeeExists(102)).isTrue();
    }

    // RULE-010: a negative id that exists is deleted
    @Test
    void rule010_negativeIdCanBeDeleted() throws Exception {
        seedEmployee(-5, "Neg", "Ops", 1.0);
        deleteEmployee("-5", "Neg").andExpect(status().isOk());
        assertThat(employeeExists(-5)).isFalse();
    }

    // RULE-013: name query parameter missing -> Invalid data
    @Test
    void rule013_missingNameIsRejected() throws Exception {
        perform(delete("/api/employees/101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID));
        assertThat(employeeExists(101)).isTrue();
    }

    // RULE-013: the missing-name check runs before the ID check
    @Test
    void rule013_missingNameWinsOverNonNumericId() throws Exception {
        perform(delete("/api/employees/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID));
    }

    // RULE-013; deviation approved 2026-10-31 (legacy: HTTP 500 from an uncaught NumberFormatException)
    @ParameterizedTest(name = "RULE-013 path id \"{0}\" rejected")
    @ValueSource(strings = {"abc", "10.5", "2147483648"})
    void rule013_nonNumericIdIsRejected(String pathId) throws Exception {
        deleteEmployee(pathId, "Asha")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(BAD_ID));
        assertThat(employeeExists(101)).isTrue();
    }

    // RULE-013: deleting twice; the second time nothing matches. Deviation approved 2026-10-31 (404 text)
    @Test
    void rule013_secondDeleteOfSameRowGives404() throws Exception {
        deleteEmployee("101", "Asha").andExpect(status().isOk());
        deleteEmployee("101", "Asha")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NOT_FOUND));
    }
}
