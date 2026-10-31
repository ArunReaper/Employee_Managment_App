package com.employee.app.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.employee.app.admin.LoginThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Shared setup for the characterization tests that run against the real employee_test database.
 *
 * <p>No assumeTrue / @Disabled anywhere: if the database is unreachable or the tables are missing,
 * {@link #resetDatabase()} throws and every test FAILS. A green run therefore means the cases
 * really executed against PostgreSQL.
 *
 * <p>The request helpers send {@link #adminSession}, a logged-in admin's session: since the security
 * hardening of 2026-10-31 every employee operation needs one (SEC-001). Anonymous behaviour is pinned in
 * {@code EmployeeOperationsWithoutLoginCharacterizationTest} and {@code SecurityHardeningTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(ExecutedCaseCounter.class)
public abstract class CharacterizationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected LoginThrottle loginThrottle;

    /** A logged-in admin's session, sent by every request helper below. */
    protected MockHttpSession adminSession;

    @BeforeEach
    void resetDatabase() {
        // Safety guard: these tests TRUNCATE tables, so refuse to run against anything but a *_test database.
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        assertThat(database)
                .as("characterization tests TRUNCATE tables; the datasource must point at a *_test database")
                .endsWith("_test");
        jdbc.execute("TRUNCATE TABLE employee_data, admin_data");
        loginThrottle.clear();
        adminSession = new MockHttpSession();
        adminSession.setAttribute("adminName", "Test Admin");
    }

    // ---------------------------------------------------------------- seeding (plain SQL)

    protected void seedEmployee(int id, String name, String department, double salary) {
        jdbc.update("INSERT INTO employee_data (id, name, department, salary) VALUES (?, ?, ?, ?)",
                id, name, department, salary);
    }

    /** Seeds a salary given as a PostgreSQL float8 literal, e.g. 'NaN', 'Infinity', '15000.005'. */
    protected void seedEmployeeWithSalaryLiteral(int id, String name, String department, String salaryLiteral) {
        jdbc.update("INSERT INTO employee_data (id, name, department, salary) VALUES (?, ?, ?, CAST(? AS DOUBLE PRECISION))",
                id, name, department, salaryLiteral);
    }

    protected void seedAdmin(int id, String name) {
        jdbc.update("INSERT INTO admin_data (id, name) VALUES (?, ?)", id, name);
    }

    // ---------------------------------------------------------------- DB assertions (plain SQL)

    protected int employeeCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM employee_data", Integer.class);
    }

    protected int adminCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_data", Integer.class);
    }

    /** The stored row as a map with keys id, name, department, salary; fails if absent. */
    protected Map<String, Object> employeeRow(int id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, department, salary FROM employee_data WHERE id = ?", id);
        assertThat(rows).as("employee_data row with id %d", id).hasSize(1);
        return rows.get(0);
    }

    protected double storedSalary(int id) {
        return ((Number) employeeRow(id).get("salary")).doubleValue();
    }

    /** Asserts the stored salary equals expected under Double.compare (so NaN equals NaN). */
    protected void assertStoredSalary(int id, double expected) {
        double actual = storedSalary(id);
        assertThat(Double.compare(actual, expected))
                .as("stored salary of %d: expected %s but was %s", id, expected, actual)
                .isZero();
    }

    protected boolean employeeExists(int id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM employee_data WHERE id = ?", Integer.class, id) == 1;
    }

    protected List<Map<String, Object>> adminRows(int id) {
        return jdbc.queryForList("SELECT id, name FROM admin_data WHERE id = ?", id);
    }

    // ---------------------------------------------------------------- request helpers

    /** Builds a JSON object; values may be null (emitted as JSON null). Odd args are keys. */
    protected String json(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("json() needs key/value pairs");
        }
        Map<String, String> body = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            body.put(keyValues[i], keyValues[i + 1]);
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.session(adminSession));
    }

    protected ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).session(adminSession).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions patchJson(String url, String body) throws Exception {
        return mvc.perform(patch(url).session(adminSession).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions addEmployee(String body) throws Exception {
        return postJson("/api/employees", body);
    }

    protected ResultActions editEmployee(String pathId, String body) throws Exception {
        return patchJson("/api/employees/" + pathId, body);
    }

    protected ResultActions deleteEmployee(String pathId, String name) throws Exception {
        MockHttpServletRequestBuilder request = delete("/api/employees/" + pathId).session(adminSession);
        if (name != null) {
            request = request.queryParam("name", name);
        }
        return mvc.perform(request);
    }

    protected ResultActions listEmployees() throws Exception {
        return mvc.perform(get("/api/employees").session(adminSession));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected static String repeat(char c, int count) {
        return String.valueOf(c).repeat(count);
    }
}
