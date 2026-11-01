package com.employee.app.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employee.app.support.CharacterizationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/**
 * SEC-001, SEC-004, SEC-005, SEC-006, SEC-010 (security hardening, 2026-10-31, at the user's request):
 * every employee operation needs a logged-in admin. This replaces RULE-018 (no operation checked the
 * session), which the legacy app also no longer has after security_remediation.patch (AuthFilter).
 * Every request here is sent with no session, or with a fresh session that has no adminName, and must
 * be refused with 401 without touching the database.
 */
@DisplayName("SEC-001: employee operations need a login (replaces RULE-018)")
class EmployeeOperationsWithoutLoginCharacterizationTest extends CharacterizationTestBase {

    private static final String LOGIN_REQUIRED = "Please log in to continue.";

    private static void expectLoginRequired(ResultActions actions) throws Exception {
        actions.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(LOGIN_REQUIRED));
    }

    // SEC-006, SEC-010: no anonymous listing of names, departments and salaries
    @Test
    void sec006_listNeedsLogin() throws Exception {
        seedEmployee(101, "Asha", "Finance", 15000.0);

        expectLoginRequired(mvc.perform(get("/api/employees")));
    }

    // SEC-005
    @Test
    void sec005_addNeedsLogin() throws Exception {
        expectLoginRequired(mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                .content(json("id", "101", "name", "Asha", "department", "Finance", "salary", "15000"))));

        assertThat(employeeExists(101)).isFalse();
    }

    // SEC-004: includes salary changes
    @Test
    void sec004_editNeedsLogin() throws Exception {
        seedEmployee(101, "Asha", "Finance", 15000.0);

        expectLoginRequired(mvc.perform(patch("/api/employees/101").contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "Asha", "field", "salary", "newValue", "99999"))));

        assertStoredSalary(101, 15000.0);
    }

    // SEC-001: includes deletion
    @Test
    void sec001_deleteNeedsLogin() throws Exception {
        seedEmployee(101, "Asha", "Finance", 15000.0);

        expectLoginRequired(mvc.perform(delete("/api/employees/101").queryParam("name", "Asha")));

        assertThat(employeeExists(101)).isTrue();
    }

    // SEC-001: a session with no adminName is treated the same as no session
    @Test
    void sec001_anonymousSessionIsRefusedEverywhere() throws Exception {
        seedEmployee(101, "Asha", "Finance", 15000.0);
        MockHttpSession anonymous = new MockHttpSession();

        expectLoginRequired(mvc.perform(post("/api/employees").session(anonymous).contentType(MediaType.APPLICATION_JSON)
                .content(json("id", "102", "name", "Ghost", "department", "X", "salary", "1"))));
        expectLoginRequired(mvc.perform(get("/api/employees").session(anonymous)));
        expectLoginRequired(mvc.perform(patch("/api/employees/101").session(anonymous).contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "x", "field", "department", "newValue", "HR"))));
        expectLoginRequired(mvc.perform(delete("/api/employees/101").session(anonymous).queryParam("name", "Asha")));

        assertThat(employeeCount()).isEqualTo(1);
        assertThat(employeeRow(101).get("department")).isEqualTo("Finance");
    }

    // SEC-001: the logged-in admin's session (the helpers' default) still reaches every operation
    @Test
    void sec001_loggedInAdminReachesEveryOperation() throws Exception {
        addEmployee(json("id", "101", "name", "Asha", "department", "Finance", "salary", "15000"))
                .andExpect(status().isCreated());
        listEmployees().andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(101));
        editEmployee("101", json("name", "x", "field", "department", "newValue", "HR")).andExpect(status().isOk());
        deleteEmployee("101", "Asha").andExpect(status().isOk());

        assertThat(employeeCount()).isZero();
    }
}
