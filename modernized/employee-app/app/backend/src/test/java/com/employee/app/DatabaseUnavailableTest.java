package com.employee.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employee.app.admin.Admin;
import com.employee.app.admin.AdminRepository;
import com.employee.app.employee.Employee;
import com.employee.app.employee.EmployeeRepository;
import com.employee.app.support.ExecutedCaseCounter;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Failure injection on the data layer. Each test first sends a request that passes every validation step,
 * then the mocked repository throws. The verify(...) calls prove the error came from the repository call
 * (and pin the exact arguments the legacy code would have passed to its DAO).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(ExecutedCaseCounter.class)
@DisplayName("Database failures (data layer mocked)")
class DatabaseUnavailableTest {

    private static final String DB_DOWN = "We couldn't reach the database. Please try again later.";
    private static final String ADD_ERROR = "Error while adding employee. Please try again.";
    private static final String SIGNUP_ERROR = "Error during sign up. Please try again.";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private AdminRepository adminRepository;

    private static CannotGetJdbcConnectionException connectionFailure() {
        return new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection",
                new SQLException("Connection refused", "08001"));
    }

    // every request is sent as a logged-in admin, so it reaches the repository (employee operations need a login)
    private static MockHttpSession admin() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminName", "Test Admin");
        return session;
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).session(admin()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions expectDbDown(ResultActions actions) throws Exception {
        return actions
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(DB_DOWN));
    }

    // ---------------------------------------------------------------- 503 on connection failure

    // RULE-025 marked wrong by the reviewer: a read failure is reported, not shown as an empty list.
    // deviation approved 2026-10-31
    @Test
    void rule025_listReportsUnreachableDatabase() throws Exception {
        when(employeeRepository.findAll()).thenThrow(connectionFailure());

        expectDbDown(mvc.perform(get("/api/employees").session(admin())));
        verify(employeeRepository).findAll();
    }

    // RULE-003 (add path), deviation approved 2026-10-31
    @Test
    void rule003_addReportsUnreachableDatabase() throws Exception {
        doThrow(connectionFailure()).when(employeeRepository).insert(any());

        expectDbDown(postJson("/api/employees",
                "{\"id\":\" 101 \",\"name\":\"  Asha \",\"department\":\" Finance \",\"salary\":\"15000\"}"));
        // RULE-004: the repository receives the parsed id/salary and the trimmed name/department
        verify(employeeRepository).insert(new Employee(101, "Asha", "Finance", 15000.0));
    }

    // RULE-016 (edit salary path), deviation approved 2026-10-31
    @Test
    void rule016_editSalaryReportsUnreachableDatabase() throws Exception {
        when(employeeRepository.updateSalary(anyInt(), anyDouble())).thenThrow(connectionFailure());

        expectDbDown(mvc.perform(patch("/api/employees/101").session(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Anyone\",\"field\":\"SALARY\",\"newValue\":\" 18000 \"}")));
        verify(employeeRepository).updateSalary(101, 18000.0);
    }

    // RULE-016 (edit department path), deviation approved 2026-10-31
    @Test
    void rule016_editDepartmentReportsUnreachableDatabase() throws Exception {
        when(employeeRepository.updateDepartment(anyInt(), anyString())).thenThrow(connectionFailure());

        expectDbDown(mvc.perform(patch("/api/employees/101").session(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Anyone\",\"field\":\"department\",\"newValue\":\"  sales \"}")));
        // RULE-011: department passed untrimmed
        verify(employeeRepository).updateDepartment(101, "  sales ");
    }

    // RULE-013 (delete path), deviation approved 2026-10-31
    @Test
    void rule013_deleteReportsUnreachableDatabase() throws Exception {
        when(employeeRepository.deleteByIdAndName(anyInt(), anyString())).thenThrow(connectionFailure());

        expectDbDown(mvc.perform(delete("/api/employees/101").session(admin()).queryParam("name", " Asha")));
        // RULE-013: name passed untrimmed
        verify(employeeRepository).deleteByIdAndName(101, " Asha");
    }

    // RULE-009 (signup path), deviation approved 2026-10-31
    @Test
    void rule009_signupReportsUnreachableDatabase() throws Exception {
        doThrow(connectionFailure()).when(adminRepository).insert(any());

        expectDbDown(postJson("/api/auth/signup", "{\"id\":\" 101 \",\"name\":\" Ravi \"}"));
        verify(adminRepository).insert(new Admin(101, "Ravi"));
    }

    // RULE-017: legacy validateAdmin swallowed the error and reported a failed login;
    // deviation approved 2026-10-31 (503 on every endpoint)
    @Test
    void rule017_loginReportsUnreachableDatabase() throws Exception {
        when(adminRepository.existsByIdAndName(anyInt(), anyString())).thenThrow(connectionFailure());

        expectDbDown(postJson("/api/auth/login", "{\"id\":\" 101 \",\"name\":\" Ravi\"}"));
        // RULE-008: id trimmed and parsed, name passed exactly as typed
        verify(adminRepository).existsByIdAndName(101, " Ravi");
    }

    // ---------------------------------------------------------------- other database errors

    // RULE-005: any database error other than a duplicate id -> generic add error
    @Test
    void rule005_addReportsOtherDatabaseErrorAsGenericError() throws Exception {
        doThrow(new DataIntegrityViolationException("x")).when(employeeRepository).insert(any());

        postJson("/api/employees", "{\"id\":\"101\",\"name\":\"Asha\",\"department\":\"Finance\",\"salary\":\"15000\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(ADD_ERROR));
    }

    // RULE-005: a non-23505 constraint error (22001 string too long) is the generic error, not "duplicate"
    @Test
    void rule005_addReportsNonUniqueConstraintErrorAsGenericError() throws Exception {
        doThrow(new DataIntegrityViolationException("value too long",
                new SQLException("value too long for type character varying(255)", "22001")))
                .when(employeeRepository).insert(any());

        postJson("/api/employees", "{\"id\":\"101\",\"name\":\"Asha\",\"department\":\"Finance\",\"salary\":\"15000\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(ADD_ERROR));
    }

    // RULE-005: SQLState 23505 surfaced by the repository -> duplicate message
    @Test
    void rule005_addReportsUniqueViolationAsDuplicate() throws Exception {
        doThrow(new DuplicateKeyException("duplicate key",
                new SQLException("duplicate key value violates unique constraint", "23505")))
                .when(employeeRepository).insert(any());

        postJson("/api/employees", "{\"id\":\"101\",\"name\":\"Asha\",\"department\":\"Finance\",\"salary\":\"15000\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("An employee with this ID already exists. Please choose another ID."));
    }

    // RULE-009: any database error other than a duplicate id -> generic sign-up error
    @Test
    void rule009_signupReportsOtherDatabaseErrorAsGenericError() throws Exception {
        doThrow(new DataIntegrityViolationException("x")).when(adminRepository).insert(any());

        postJson("/api/auth/signup", "{\"id\":\"101\",\"name\":\"Ravi\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(SIGNUP_ERROR));
    }

    // RULE-009: a non-23505 constraint error is the generic sign-up error
    @Test
    void rule009_signupReportsNonUniqueConstraintErrorAsGenericError() throws Exception {
        doThrow(new DataIntegrityViolationException("value too long",
                new SQLException("value too long for type character varying(255)", "22001")))
                .when(adminRepository).insert(any());

        postJson("/api/auth/signup", "{\"id\":\"101\",\"name\":\"Ravi\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(SIGNUP_ERROR));
    }

    // RULE-009: SQLState 23505 surfaced by the repository -> duplicate message
    @Test
    void rule009_signupReportsUniqueViolationAsDuplicate() throws Exception {
        doThrow(new DuplicateKeyException("duplicate key",
                new SQLException("duplicate key value violates unique constraint", "23505")))
                .when(adminRepository).insert(any());

        postJson("/api/auth/signup", "{\"id\":\"101\",\"name\":\"Ravi\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("An admin with this ID already exists. Please choose another ID."));
    }
}
