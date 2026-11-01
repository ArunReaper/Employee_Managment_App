package com.employee.app.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.employee.app.support.CharacterizationTestBase;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * POST /api/auth/signup. Legacy: SignUpServlet (/SignupServlet).
 * Order: parseInt(id.trim()) -> (deviation) trimmed name length -> insert (23505 -> duplicate, other -> generic).
 * The name is stored trimmed; missing -> NULL, blank -> "" (neither rejected).
 */
@DisplayName("POST /api/auth/signup (legacy SignUpServlet)")
class SignUpCharacterizationTest extends CharacterizationTestBase {

    private static final String BAD_ID = "Admin ID must be a whole number, e.g. 101.";
    private static final String TOO_LONG = "Name must be 255 characters or fewer.";
    private static final String DUPLICATE = "An admin with this ID already exists. Please choose another ID.";
    private static final String SIGNED_UP = "Sign up successful! Please log in.";

    private void signUp(String requestBody, int expectedStatus, String expectedMessage) throws Exception {
        postJson("/api/auth/signup", requestBody)
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    private String storedName(int id) {
        List<Map<String, Object>> rows = adminRows(id);
        assertThat(rows).as("admin_data row with id %d", id).hasSize(1);
        return (String) rows.get(0).get("name");
    }

    // RULE-009: open self-registration
    @Test
    void rule009_newAdminIsStored() throws Exception {
        signUp(json("id", "101", "name", "Ravi"), 201, SIGNED_UP);
        assertThat(storedName(101)).isEqualTo("Ravi");
        assertThat(adminCount()).isEqualTo(1);
    }

    // RULE-008, RULE-009: id and name are trimmed
    @Test
    void rule008_idAndNameAreTrimmed() throws Exception {
        signUp(json("id", " 5 ", "name", " Ravi "), 201, SIGNED_UP);
        assertThat(storedName(5)).isEqualTo("Ravi");
    }

    // RULE-008: trailing space is trimmed on sign-up ("Root " stored as "Root"); case kept
    @Test
    void rule008_caseIsKeptAndOnlyEndsAreTrimmed() throws Exception {
        signUp(json("id", "101", "name", "Root  Admin "), 201, SIGNED_UP);
        assertThat(storedName(101)).isEqualTo("Root  Admin");
    }

    // RULE-008: blank name accepted, stored as ""
    @ParameterizedTest(name = "RULE-008 name \"{0}\" stored as empty string")
    @ValueSource(strings = {"", "   ", "\t"})
    void rule008_blankNameIsAcceptedAndStoredAsEmptyString(String name) throws Exception {
        signUp(json("id", "101", "name", name), 201, SIGNED_UP);
        assertThat(storedName(101)).isEmpty();
    }

    // RULE-008: missing name accepted, stored as NULL
    @Test
    void rule008_missingNameIsStoredAsNull() throws Exception {
        signUp(json("id", "101"), 201, SIGNED_UP);
        assertThat(storedName(101)).isNull();
    }

    // RULE-008: JSON null name behaves like a missing name
    @Test
    void rule008_nullNameIsStoredAsNull() throws Exception {
        signUp(json("id", "101", "name", null), 201, SIGNED_UP);
        assertThat(storedName(101)).isNull();
    }

    static Stream<Arguments> acceptedIds() {
        return Stream.of(Arguments.of("0", 0), Arguments.of("-3", -3), Arguments.of("+7", 7), Arguments.of("007", 7));
    }

    // RULE-009, RULE-010: the server accepts any parseable integer (browser-only min=1)
    @ParameterizedTest(name = "RULE-010 admin id \"{0}\" stored as {1}")
    @MethodSource("acceptedIds")
    void rule010_anyParseableIdIsAccepted(String id, int stored) throws Exception {
        signUp(json("id", id, "name", "Ravi"), 201, SIGNED_UP);
        assertThat(storedName(stored)).isEqualTo("Ravi");
    }

    // RULE-009: non-numeric / blank id rejected, nothing stored
    @ParameterizedTest(name = "RULE-009 id \"{0}\" rejected")
    @ValueSource(strings = {"abc", "", "  ", "10.5", "1e3", "2147483648"})
    void rule009_idThatIsNotAWholeNumberIsRejected(String id) throws Exception {
        signUp(json("id", id, "name", "Ravi"), 400, BAD_ID);
        assertThat(adminCount()).isZero();
    }

    // RULE-009: missing id rejected
    @Test
    void rule009_missingIdIsRejected() throws Exception {
        signUp(json("name", "Ravi"), 400, BAD_ID);
        assertThat(adminCount()).isZero();
    }

    // RULE-009: JSON null id rejected
    @Test
    void rule009_nullIdIsRejected() throws Exception {
        signUp(json("id", null, "name", "Ravi"), 400, BAD_ID);
        assertThat(adminCount()).isZero();
    }

    // RULE-009, RULE-012: duplicate id -> 409; the existing admin is not changed (no update path)
    @Test
    void rule009_duplicateIdIsRejectedAndExistingAdminUnchanged() throws Exception {
        seedAdmin(101, "Priya");

        signUp(json("id", "101", "name", "Ravi"), 409, DUPLICATE);

        assertThat(storedName(101)).isEqualTo("Priya");
        assertThat(adminCount()).isEqualTo(1);
    }

    // RULE-009: duplicate detected on the parsed id (" 0101 " == 101)
    @Test
    void rule009_duplicateDetectedOnParsedId() throws Exception {
        signUp(json("id", "101", "name", "Ravi"), 201, SIGNED_UP);
        signUp(json("id", " 0101 ", "name", "Other"), 409, DUPLICATE);
        assertThat(storedName(101)).isEqualTo("Ravi");
    }

    // RULE-012: admin names need not be unique
    @Test
    void rule012_twoAdminsMayShareAName() throws Exception {
        signUp(json("id", "1", "name", "Ravi"), 201, SIGNED_UP);
        signUp(json("id", "2", "name", "Ravi"), 201, SIGNED_UP);
        assertThat(adminCount()).isEqualTo(2);
    }

    // RULE-019 (VARCHAR(255)); deviation approved 2026-10-31
    @Test
    void rule019_nameLongerThan255IsRejected() throws Exception {
        signUp(json("id", "101", "name", repeat('r', 256)), 400, TOO_LONG);
        assertThat(adminCount()).isZero();
    }

    // RULE-019: the limit applies to the trimmed name; deviation approved 2026-10-31
    @Test
    void rule019_255CharactersAfterTrimIsAccepted() throws Exception {
        String name = repeat('r', 255);
        signUp(json("id", "101", "name", "  " + name + "  "), 201, SIGNED_UP);
        assertThat(storedName(101)).isEqualTo(name);
    }

    // RULE-009 order: the ID check runs before the length check; deviation approved 2026-10-31
    @Test
    void rule009_invalidIdWinsOverTooLongName() throws Exception {
        signUp(json("id", "abc", "name", repeat('r', 256)), 400, BAD_ID);
    }

    // RULE-009 order: the length check runs before the duplicate check; deviation approved 2026-10-31
    @Test
    void rule019_tooLongNameWinsOverDuplicateId() throws Exception {
        seedAdmin(101, "Priya");
        signUp(json("id", "101", "name", repeat('r', 256)), 400, TOO_LONG);
        assertThat(storedName(101)).isEqualTo("Priya");
    }
}
