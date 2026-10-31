package com.employee.app.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.employee.app.support.ExecutedCaseCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * SEC-014 (partial): the real embedded Tomcat sends the session cookie with SameSite=Lax and HttpOnly, so
 * cross-site POST, PATCH and DELETE requests carry no session. MockMvc does not exercise the container's
 * cookie settings, hence a real port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(ExecutedCaseCounter.class)
@DisplayName("SEC-014: session cookie attributes")
class SessionCookieTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    // SEC-014
    @Test
    void sec014_sessionCookieIsSameSiteLaxAndHttpOnly() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).endsWith("_test");
        jdbc.execute("TRUNCATE TABLE employee_data, admin_data");
        jdbc.update("INSERT INTO admin_data (id, name) VALUES (1, 'Root')");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = http.postForEntity("/api/auth/login",
                new HttpEntity<>("{\"id\":\"1\",\"name\":\"Root\"}", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith("JSESSIONID=").contains("SameSite=Lax").contains("HttpOnly");
    }
}
