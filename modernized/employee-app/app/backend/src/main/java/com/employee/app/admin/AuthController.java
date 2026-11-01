package com.employee.app.admin;

import com.employee.app.web.ApiMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces SignUpServlet (/SignupServlet), the login scriptlet in Login.jsp, the session read in
 * Welcome.jsp and the hardened legacy LogoutServlet. The session keeps the legacy attribute name "adminName".
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String SESSION_ADMIN_NAME = "adminName";

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record Credentials(String id, String name) {
    }

    public record LoginResult(String adminName, String message) {
    }

    public record CurrentAdmin(String adminName) {
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiMessage signUp(@RequestBody Credentials request, HttpServletRequest http) {
        auth.signUp(request.id(), request.name(), AdminSessionInterceptor.isLoggedIn(http));
        return new ApiMessage("Sign up successful! Please log in.");
    }

    @PostMapping("/login")
    public LoginResult logIn(@RequestBody Credentials request, HttpServletRequest http) {
        String name = auth.logIn(request.id(), request.name(), http.getRemoteAddr());
        HttpSession session = http.getSession();
        http.changeSessionId(); // SEC-012: new session id at login (session fixation)
        session.setAttribute(SESSION_ADMIN_NAME, name);
        return new LoginResult(name, "Welcome, " + name + "!");
    }

    // SEC-020 replaces RULE-015: logout ends the session
    @PostMapping("/logout")
    public ApiMessage logOut(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return new ApiMessage("You have been logged out.");
    }

    @GetMapping("/me")
    public CurrentAdmin me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return new CurrentAdmin(session == null ? null : (String) session.getAttribute(SESSION_ADMIN_NAME));
    }
}
