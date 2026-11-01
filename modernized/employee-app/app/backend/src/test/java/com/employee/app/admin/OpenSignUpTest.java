package com.employee.app.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employee.app.support.CharacterizationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** SEC-007 switch: with app.security.open-signup=true anyone may sign up, as legacy RULE-009 allowed. */
@TestPropertySource(properties = "app.security.open-signup=true")
@DisplayName("SEC-007: app.security.open-signup=true reopens sign-up")
class OpenSignUpTest extends CharacterizationTestBase {

    // SEC-007 (switched off): an anonymous visitor can sign up although an admin exists
    @Test
    void sec007_openSignUpLetsAnyoneSignUp() throws Exception {
        seedAdmin(1, "Root");

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json("id", "2", "name", "Priya")))
                .andExpect(status().isCreated());

        assertThat(adminRows(2)).hasSize(1);
    }

    // the switch touches sign-up only: employee operations still need a login (SEC-001)
    @Test
    void sec001_employeeOperationsStillNeedALogin() throws Exception {
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content(json("id", "101", "name", "Asha", "department", "Finance", "salary", "15000")))
                .andExpect(status().isUnauthorized());
    }
}
