package com.employee.app.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employee.app.support.CharacterizationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * GET /api/employees. Legacy: ShowEmployeeServlet + showEmployee.jsp, which printed
 * {@code <%= emp.getSalary() %>}, i.e. Double.toString(salary).
 */
@DisplayName("GET /api/employees (legacy ShowEmployeeServlet + showEmployee.jsp)")
class EmployeeListCharacterizationTest extends CharacterizationTestBase {

    private Map<Integer, JsonNode> fetchById() throws Exception {
        MvcResult result = listEmployees()
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        JsonNode array = body(result);
        assertThat(array.isArray()).as("response is a JSON array").isTrue();
        Map<Integer, JsonNode> byId = new HashMap<>();
        array.forEach(node -> byId.put(node.get("id").intValue(), node));
        assertThat(byId).as("ids are unique in the response").hasSize(array.size());
        return byId;
    }

    // RULE-023
    @Test
    void rule023_emptyTableGivesEmptyArray() throws Exception {
        listEmployees()
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
    }

    // RULE-023: every row, no filter, no guaranteed order
    @Test
    void rule023_returnsAllRowsUnfilteredWithTheirStoredValues() throws Exception {
        seedEmployee(101, "Asha", "Finance", 15000.0);
        seedEmployee(102, "Ravi", "HR", 1234.5);
        seedEmployee(-5, "Neg", "Ops", 0.0);

        Map<Integer, JsonNode> byId = fetchById();

        assertThat(byId.keySet()).containsExactlyInAnyOrder(101, 102, -5);
        JsonNode asha = byId.get(101);
        assertThat(asha.get("id").isInt()).isTrue();
        assertThat(asha.get("name").textValue()).isEqualTo("Asha");
        assertThat(asha.get("department").textValue()).isEqualTo("Finance");
        assertThat(asha.get("salary").isNumber()).isTrue();
        assertThat(asha.get("salary").doubleValue()).isEqualTo(15000.0);
        assertThat(asha.get("salaryText").textValue()).isEqualTo("15000.0");
        assertThat(byId.get(102).get("salaryText").textValue()).isEqualTo("1234.5");
        assertThat(byId.get(-5).get("salaryText").textValue()).isEqualTo("0.0");
        // the list does not modify the table
        assertThat(employeeCount()).isEqualTo(3);
    }

    // RULE-023: each element carries exactly the five contract fields
    @Test
    void rule023_elementHasExactlyIdNameDepartmentSalaryAndSalaryText() throws Exception {
        seedEmployee(101, "Asha", "Finance", 15000.0);

        JsonNode asha = fetchById().get(101);

        Set<String> fields = new HashSet<>();
        asha.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "name", "department", "salary", "salaryText");
    }

    /**
     * salaryText = Double.toString(salary), exactly as the JSP printed it. Seeded through a PostgreSQL
     * float8 literal so the stored double is the correctly rounded value of the literal.
     */
    static Stream<Arguments> salaryTextCases() {
        return Stream.of(
                Arguments.of("15000", "15000.0", 15000.0),
                Arguments.of("10000000", "1.0E7", 1.0E7),
                Arguments.of("12000000", "1.2E7", 1.2E7),
                Arguments.of("9999999", "9999999.0", 9999999.0),
                Arguments.of("15000.005", "15000.005", 15000.005),
                Arguments.of("15000.5", "15000.5", 15000.5),
                Arguments.of("-500", "-500.0", -500.0),
                Arguments.of("0", "0.0", 0.0),
                Arguments.of("0.001", "0.001", 0.001),
                Arguments.of("0.0001", "1.0E-4", 1.0E-4),
                Arguments.of("0.30000000000000004", "0.30000000000000004", 0.1 + 0.2),
                Arguments.of("1000.123", "1000.123", 1000.123));
    }

    // RULE-002, RULE-001
    @ParameterizedTest(name = "RULE-002 salary {0} -> salaryText \"{1}\"")
    @MethodSource("salaryTextCases")
    void rule002_salaryTextIsJavaDoubleToString(String storedLiteral, String expectedText, double expectedSalary)
            throws Exception {
        seedEmployeeWithSalaryLiteral(101, "Asha", "Finance", storedLiteral);

        JsonNode row = fetchById().get(101);

        assertThat(row.get("salaryText").textValue()).isEqualTo(expectedText);
        assertThat(Double.toString(expectedSalary)).as("oracle: Double.toString").isEqualTo(expectedText);
        assertThat(row.get("salary").isNumber()).isTrue();
        assertThat(row.get("salary").doubleValue()).isEqualTo(expectedSalary);
    }

    static Stream<Arguments> nonFiniteCases() {
        return Stream.of(
                Arguments.of("NaN", "NaN"),
                Arguments.of("Infinity", "Infinity"),
                Arguments.of("-Infinity", "-Infinity"));
    }

    // RULE-002, RULE-025 edge (NaN / Infinity saved through edit display as-is); salary null in JSON
    @ParameterizedTest(name = "RULE-002 salary {0} -> salaryText \"{1}\", salary null")
    @MethodSource("nonFiniteCases")
    void rule002_nonFiniteSalaryHasTextAndNullNumber(String storedLiteral, String expectedText) throws Exception {
        seedEmployeeWithSalaryLiteral(101, "Asha", "Finance", storedLiteral);

        JsonNode row = fetchById().get(101);

        assertThat(row.get("salaryText").textValue()).isEqualTo(expectedText);
        assertThat(row.has("salary")).as("salary key present").isTrue();
        assertThat(row.get("salary").isNull()).as("salary is JSON null for %s", storedLiteral).isTrue();
    }

    // RULE-019: name and department columns are nullable; the JSP printed the text "null"
    @Test
    void rule019_nullNameAndDepartmentAreReturnedAsJsonNull() throws Exception {
        seedEmployee(101, null, null, 15000.0);

        JsonNode row = fetchById().get(101);

        assertThat(row.has("name")).isTrue();
        assertThat(row.get("name").isNull()).isTrue();
        assertThat(row.has("department")).isTrue();
        assertThat(row.get("department").isNull()).isTrue();
        assertThat(row.get("salaryText").textValue()).isEqualTo("15000.0");
    }

    // RULE-002 / RULE-023: values are returned raw (no trimming, no escaping) as stored
    @Test
    void rule023_textIsReturnedVerbatimIncludingSpacesAndMarkup() throws Exception {
        seedEmployee(101, "<b>Asha</b>", "  sales ", 15000.0);
        seedEmployee(102, "", "", 1.0);

        Map<Integer, JsonNode> byId = fetchById();

        assertThat(byId.get(101).get("name").textValue()).isEqualTo("<b>Asha</b>");
        assertThat(byId.get(101).get("department").textValue()).isEqualTo("  sales ");
        assertThat(byId.get(102).get("name").textValue()).isEmpty();
        assertThat(byId.get(102).get("department").textValue()).isEmpty();
    }
}
