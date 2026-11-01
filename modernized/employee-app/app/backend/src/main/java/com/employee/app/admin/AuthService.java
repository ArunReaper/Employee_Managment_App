package com.employee.app.admin;

import com.employee.app.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Admin sign-up and login, as the hardened legacy SignUpServlet, AuthFilter and Login.jsp do them
 * (security_remediation.patch, ported at the user's request on 2026-10-31).
 */
@Service
public class AuthService {

    static final String INVALID_LOGIN = "Invalid ID or Name. Please try again.";
    static final String SIGNUP_CLOSED = "Sign-up is closed. Ask an existing admin to create your account.";

    private final AdminRepository admins;
    private final LoginThrottle throttle;
    private final boolean openSignUp;

    /** openSignUp (app.security.open-signup, default false) restores legacy RULE-009: anyone may sign up. */
    public AuthService(AdminRepository admins, LoginThrottle throttle,
                       @Value("${app.security.open-signup:false}") boolean openSignUp) {
        this.admins = admins;
        this.throttle = throttle;
        this.openSignUp = openSignUp;
    }

    /**
     * RULE-008 (name trimmed, blank or missing accepted), RULE-012. SEC-007 replaces RULE-009 (open
     * self-registration): anyone may sign up only while no admin exists; after that, only a logged-in admin.
     * With app.security.open-signup=true the gate is off and anyone may sign up, as in legacy.
     */
    public void signUp(String idText, String nameText, boolean byLoggedInAdmin) {
        int id;
        try {
            id = Integer.parseInt(idText == null ? "" : idText.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Admin ID must be a whole number, e.g. 101.");
        }
        String name = nameText == null ? null : nameText.trim();
        if (name != null && name.length() > 255) { // deviation: legacy gave a generic error
            throw ApiException.badRequest("Name must be 255 characters or fewer.");
        }
        if (!openSignUp && !byLoggedInAdmin && admins.anyExists()) {
            throw new ApiException(HttpStatus.FORBIDDEN, SIGNUP_CLOSED);
        }
        try {
            admins.insert(new Admin(id, name));
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("An admin with this ID already exists. Please choose another ID.");
        } catch (DataAccessResourceFailureException e) {
            throw e; // 503
        } catch (DataAccessException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error during sign up. Please try again.", e);
        }
    }

    /**
     * RULE-017 (confirmed): id and name must match exactly; there is no password. The id is trimmed,
     * the name is not (RULE-008). SEC-009: the attempt is reserved with the lockout before the check.
     * Returns the name to keep in the session.
     */
    public String logIn(String idText, String name, String clientAddress) {
        Integer id = null;
        try {
            id = idText == null ? null : Integer.valueOf(idText.trim());
        } catch (NumberFormatException e) {
            // not a number: counts against the address only, then fails below
        }
        if (!throttle.tryAcquire(clientAddress, id)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, LoginThrottle.LOCKED);
        }
        if (id == null || name == null || !admins.existsByIdAndName(id, name)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }
        throttle.reset(clientAddress, id);
        return name;
    }
}
