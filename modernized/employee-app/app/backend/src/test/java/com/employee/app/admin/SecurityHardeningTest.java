package com.employee.app.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * The security hardening of 2026-10-31 (analysis/employee-app/SECURITY_FINDINGS.md), ported from
 * security_remediation.patch at the user's request: sign-up bootstrap (SEC-007), login lockout (SEC-009)
 * and logout (SEC-020). Session rotation (SEC-012) is in LoginSessionCharacterizationTest, the login
 * requirement (SEC-001) in EmployeeOperationsWithoutLoginCharacterizationTest.
 */
@DisplayName("Security hardening: sign-up gate, login lockout, logout")
class SecurityHardeningTest extends CharacterizationTestBase {

    private static final String LOCKED = "Too many failed attempts. Please try again in 15 minutes.";
    private static final String INVALID = "Invalid ID or Name. Please try again.";

    private ResultActions anonymousSignUp(String id, String name) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json("id", id, "name", name)));
    }

    private ResultActions login(String fromAddress, String id, String name, MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/auth/login").session(session)
                .with(request -> {
                    request.setRemoteAddr(fromAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON).content(json("id", id, "name", name)));
    }

    private ResultActions login(String fromAddress, String id, String name) throws Exception {
        return login(fromAddress, id, name, new MockHttpSession());
    }

    // ---------------------------------------------------------------- SEC-007: sign-up gate

    // SEC-007: the first admin can sign up without a login (bootstrap)
    @Test
    void sec007_firstAdminCanSignUpAnonymously() throws Exception {
        anonymousSignUp("1", "Root").andExpect(status().isCreated());

        assertThat(adminCount()).isEqualTo(1);
    }

    // SEC-007 (replaces RULE-009): once an admin exists, anonymous sign-up is closed
    @Test
    void sec007_anonymousSignUpIsClosedOnceAnAdminExists() throws Exception {
        seedAdmin(1, "Root");

        anonymousSignUp("2", "Mallory")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Sign-up is closed. Ask an existing admin to create your account."));

        assertThat(adminRows(2)).isEmpty();
    }

    // SEC-007: a logged-in admin can still create another admin
    @Test
    void sec007_loggedInAdminCanAddAnAdmin() throws Exception {
        seedAdmin(1, "Root");

        postJson("/api/auth/signup", json("id", "2", "name", "Priya")).andExpect(status().isCreated());

        assertThat(adminRows(2)).hasSize(1);
    }

    // ---------------------------------------------------------------- SEC-009: lockout

    // SEC-009: after 5 failed attempts the 6th is refused, even with the right name, and no session is set
    @Test
    void sec009_sixthAttemptIsLockedOutEvenWithTheRightName() throws Exception {
        seedAdmin(1, "Root");
        for (int i = 0; i < 5; i++) {
            login("10.0.0.1", "1", "wrong" + i).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(INVALID));
        }
        MockHttpSession session = new MockHttpSession();

        login("10.0.0.1", "1", "Root", session)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(LOCKED));

        assertThat(session.getAttribute("adminName")).isNull();
    }

    // SEC-009: a successful login resets the count
    @Test
    void sec009_successResetsTheCount() throws Exception {
        seedAdmin(1, "Root");
        for (int i = 0; i < 4; i++) {
            login("10.0.0.1", "1", "wrong" + i).andExpect(status().isUnauthorized());
        }
        login("10.0.0.1", "1", "Root").andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            login("10.0.0.1", "1", "wrong" + i).andExpect(status().isUnauthorized());
        }
        login("10.0.0.1", "1", "Root").andExpect(status().isOk());
    }

    // SEC-009: guesses spread over many addresses still lock the admin id
    @Test
    void sec009_adminIdIsLockedAcrossAddresses() throws Exception {
        seedAdmin(1, "Root");
        for (int i = 0; i < 5; i++) {
            login("10.0.1." + i, "1", "wrong").andExpect(status().isUnauthorized());
        }

        login("10.0.2.1", "1", "Root").andExpect(status().isTooManyRequests());
        login("10.0.2.1", " 01 ", "Root").andExpect(status().isTooManyRequests()); // same id, written differently
    }

    // SEC-009 (review round 2): a locked address must not add to another admin id's count
    @Test
    void sec009_lockedAddressDoesNotCountAgainstOtherIds() throws Exception {
        seedAdmin(2, "Second");
        for (int i = 0; i < 5; i++) {
            login("10.0.0.1", "1", "wrong").andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 8; i++) {
            login("10.0.0.1", "2", "wrong").andExpect(status().isTooManyRequests());
        }

        login("10.0.0.9", "2", "Second").andExpect(status().isOk());
    }

    // SEC-009: a non-numeric id counts against the address only
    @Test
    void sec009_nonNumericIdCountsAgainstTheAddress() throws Exception {
        seedAdmin(1, "Root");
        for (int i = 0; i < 5; i++) {
            login("10.0.0.1", "abc", "Root").andExpect(status().isUnauthorized());
        }

        login("10.0.0.1", "1", "Root").andExpect(status().isTooManyRequests());
        login("10.0.0.2", "1", "Root").andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- SEC-020: logout

    // SEC-020 (replaces RULE-015): logout ends the session, so the old session can no longer reach employees
    @Test
    void sec020_logoutEndsTheSession() throws Exception {
        seedAdmin(1, "Root");
        MockHttpSession session = new MockHttpSession();
        login("10.0.0.1", "1", "Root", session).andExpect(status().isOk());
        mvc.perform(get("/api/employees").session(session)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You have been logged out."));

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/employees").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.adminName").doesNotExist());
    }

    // SEC-020: logout without a session is harmless
    @Test
    void sec020_logoutWithoutSessionSucceeds() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isOk());
    }
}
