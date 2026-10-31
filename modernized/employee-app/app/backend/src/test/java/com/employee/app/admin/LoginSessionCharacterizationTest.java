package com.employee.app.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employee.app.support.CharacterizationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * POST /api/auth/login and GET /api/auth/me. Legacy: the Login.jsp scriptlet
 * (validateAdmin(Integer.parseInt(id.trim()), name) -> session.setAttribute("adminName", name))
 * and Welcome.jsp reading sessionScope.adminName. No password (RULE-017, confirmed).
 */
@DisplayName("POST /api/auth/login + GET /api/auth/me (legacy Login.jsp, Welcome.jsp)")
class LoginSessionCharacterizationTest extends CharacterizationTestBase {

    private static final String LOGIN_FAILED = "Invalid ID or Name. Please try again.";

    @BeforeEach
    void seedRavi() {
        seedAdmin(101, "Ravi");
    }

    private ResultActions login(MockHttpSession session, String requestBody) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));
    }

    private ResultActions loginWithoutSession(String requestBody) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requestBody));
    }

    /** Reads /api/auth/me and returns the adminName node, asserting the key is present. */
    private JsonNode me(MockHttpSession sessionOrNull) throws Exception {
        var request = get("/api/auth/me");
        if (sessionOrNull != null) {
            request = request.session(sessionOrNull);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode body = body(result);
        assertThat(body.has("adminName")).as("adminName key present in /api/auth/me").isTrue();
        return body.get("adminName");
    }

    private void expectLoginFails(String requestBody) throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session, requestBody)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(LOGIN_FAILED));
        assertThat(session.getAttribute("adminName")).as("no adminName after failed login").isNull();
        assertThat(me(session).isNull()).isTrue();
    }

    // ---------------------------------------------------------------- success

    // RULE-017: id + exact name -> 200, adminName in body and in the session
    @Test
    void rule017_matchingIdAndNameLogsIn() throws Exception {
        MockHttpSession session = new MockHttpSession();

        login(session, json("id", "101", "name", "Ravi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminName").value("Ravi"))
                .andExpect(jsonPath("$.message").value("Welcome, Ravi!"));

        assertThat(session.getAttribute("adminName")).isEqualTo("Ravi");
    }

    // RULE-017: the session attribute is returned by /api/auth/me on the same session
    @Test
    void rule017_meReturnsAdminNameFromTheSameSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session, json("id", "101", "name", "Ravi")).andExpect(status().isOk());

        assertThat(me(session).textValue()).isEqualTo("Ravi");
    }

    // SEC-012 (security hardening, 2026-10-31): the session id is rotated on login (legacy kept it: session fixation)
    @Test
    void sec012_sessionIdIsRotatedOnLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();

        MvcResult result = login(session, json("id", "101", "name", "Ravi")).andExpect(status().isOk()).andReturn();

        HttpSession used = result.getRequest().getSession(false);
        assertThat(used).isNotNull();
        assertThat(used.getId()).isNotEqualTo(before);
        assertThat(used.getAttribute("adminName")).isEqualTo("Ravi");
    }

    // RULE-017 (Login.jsp trims the id): " 101 " logs in
    @Test
    void rule017_idIsTrimmed() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session, json("id", " 101 ", "name", "Ravi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminName").value("Ravi"));
        assertThat(session.getAttribute("adminName")).isEqualTo("Ravi");
    }

    // RULE-017: "+101" and "0101" are Integer.parseInt(...) == 101
    @ParameterizedTest(name = "RULE-017 id \"{0}\" parses to 101")
    @ValueSource(strings = {"+101", "0101"})
    void rule017_idIsParsedAsInteger(String id) throws Exception {
        loginWithoutSession(json("id", id, "name", "Ravi")).andExpect(status().isOk());
    }

    // RULE-008, RULE-017: adminName is the name as typed; a stored name with inner spaces must be typed exactly
    @Test
    void rule008_nameIsUsedExactlyAsTyped() throws Exception {
        seedAdmin(102, "Root  Admin");
        MockHttpSession session = new MockHttpSession();
        login(session, json("id", "102", "name", "Root  Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminName").value("Root  Admin"))
                .andExpect(jsonPath("$.message").value("Welcome, Root  Admin!"));
        assertThat(me(session).textValue()).isEqualTo("Root  Admin");
    }

    // RULE-008: sign-up stores "Root " as "Root"; login as "Root " fails, "Root" succeeds, "root" fails
    @Test
    void rule008_signUpTrimsButLoginDoesNot() throws Exception {
        postJson("/api/auth/signup", json("id", "7", "name", "Root ")).andExpect(status().isCreated());

        expectLoginFails(json("id", "7", "name", "Root "));
        expectLoginFails(json("id", "7", "name", "root"));
        loginWithoutSession(json("id", "7", "name", "Root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminName").value("Root"));
    }

    // RULE-008: an admin signed up with a blank name ("") can log in with name "" (legacy: name != null, SQL "" = "")
    @Test
    void rule008_adminWithEmptyNameLogsInWithEmptyName() throws Exception {
        seedAdmin(5, "");
        MockHttpSession session = new MockHttpSession();

        login(session, json("id", "5", "name", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminName").value(""))
                .andExpect(jsonPath("$.message").value("Welcome, !"));

        assertThat(session.getAttribute("adminName")).isEqualTo("");
    }

    // RULE-012: two admins may share a name; each logs in with its own id
    @Test
    void rule012_sharedNameLogsInUnderEitherId() throws Exception {
        seedAdmin(102, "Ravi");
        loginWithoutSession(json("id", "101", "name", "Ravi")).andExpect(status().isOk());
        loginWithoutSession(json("id", "102", "name", "Ravi")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- failures

    // RULE-017, RULE-008: name not trimmed on login
    @ParameterizedTest(name = "RULE-008 name \"{0}\" does not match \"Ravi\"")
    @ValueSource(strings = {" Ravi", "Ravi ", " Ravi "})
    void rule008_untrimmedNameFails(String name) throws Exception {
        expectLoginFails(json("id", "101", "name", name));
    }

    // RULE-017: name is case-sensitive
    @ParameterizedTest(name = "RULE-017 name \"{0}\" does not match \"Ravi\"")
    @ValueSource(strings = {"ravi", "RAVI", "rAVI"})
    void rule017_nameIsCaseSensitive(String name) throws Exception {
        expectLoginFails(json("id", "101", "name", name));
    }

    // RULE-017: wrong id
    @Test
    void rule017_wrongIdFails() throws Exception {
        expectLoginFails(json("id", "102", "name", "Ravi"));
    }

    // RULE-017: non-numeric / blank id gives the same generic message
    @ParameterizedTest(name = "RULE-017 id \"{0}\" fails")
    @ValueSource(strings = {"abc", "", "  ", "10.5", "2147483648"})
    void rule017_nonNumericIdFails(String id) throws Exception {
        expectLoginFails(json("id", id, "name", "Ravi"));
    }

    // RULE-017: missing id
    @Test
    void rule017_missingIdFails() throws Exception {
        expectLoginFails(json("name", "Ravi"));
    }

    // RULE-017: missing name
    @Test
    void rule017_missingNameFails() throws Exception {
        expectLoginFails(json("id", "101"));
    }

    // RULE-017: JSON null id / name
    @Test
    void rule017_nullIdOrNameFails() throws Exception {
        expectLoginFails(json("id", null, "name", "Ravi"));
        expectLoginFails(json("id", "101", "name", null));
    }

    // RULE-008: an admin stored with a NULL name can never log in
    @Test
    void rule008_adminWithNullNameCannotLogIn() throws Exception {
        seedAdmin(6, null);
        expectLoginFails(json("id", "6", "name", "null"));
        expectLoginFails(json("id", "6", "name", ""));
        expectLoginFails(json("id", "6"));
    }

    // RULE-017: login does not write to admin_data
    @Test
    void rule017_loginDoesNotChangeAdminData() throws Exception {
        loginWithoutSession(json("id", "101", "name", "Ravi")).andExpect(status().isOk());
        loginWithoutSession(json("id", "999", "name", "Nobody")).andExpect(status().isUnauthorized());
        assertThat(adminCount()).isEqualTo(1);
        assertThat(adminRows(101).get(0).get("name")).isEqualTo("Ravi");
    }

    // ---------------------------------------------------------------- session behaviour

    // RULE-018: /api/auth/me with no session -> adminName null (Welcome.jsp rendered for anonymous users)
    @Test
    void rule018_meWithoutSessionGivesNullAdminName() throws Exception {
        assertThat(me(null).isNull()).isTrue();
    }

    // RULE-018: a fresh session with nobody logged in -> adminName null
    @Test
    void rule018_meWithFreshSessionGivesNullAdminName() throws Exception {
        assertThat(me(new MockHttpSession()).isNull()).isTrue();
    }

    // RULE-017: a failed login does not clear an existing login on the same session (Login.jsp only sets on success)
    @Test
    void rule017_failedLoginKeepsPreviousSessionLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session, json("id", "101", "name", "Ravi")).andExpect(status().isOk());

        login(session, json("id", "101", "name", "ravi")).andExpect(status().isUnauthorized());

        assertThat(session.getAttribute("adminName")).isEqualTo("Ravi");
        assertThat(me(session).textValue()).isEqualTo("Ravi");
    }

    // RULE-017: a second successful login on the same session overwrites adminName
    @Test
    void rule017_secondLoginOverwritesAdminName() throws Exception {
        seedAdmin(102, "Priya");
        MockHttpSession session = new MockHttpSession();
        login(session, json("id", "101", "name", "Ravi")).andExpect(status().isOk());

        login(session, json("id", "102", "name", "Priya")).andExpect(status().isOk());

        assertThat(me(session).textValue()).isEqualTo("Priya");
    }

    // RULE-015: the session keeps adminName across later requests (until logout, SEC-020)
    @Test
    void rule015_sessionKeepsAdminNameAcrossLaterRequests() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session, json("id", "101", "name", "Ravi")).andExpect(status().isOk());

        mvc.perform(get("/api/employees").session(session)).andExpect(status().isOk());

        assertThat(me(session).textValue()).isEqualTo("Ravi");
        assertThat(session.isInvalid()).isFalse();
    }

    // RULE-012: admin removed from the table still has its existing session (no revocation path)
    @Test
    void rule012_existingSessionSurvivesAdminRowRemoval() throws Exception {
        MockHttpSession session = new MockHttpSession();
        login(session, json("id", "101", "name", "Ravi")).andExpect(status().isOk());

        jdbc.update("DELETE FROM admin_data WHERE id = 101");

        assertThat(me(session).textValue()).isEqualTo("Ravi");
    }
}
