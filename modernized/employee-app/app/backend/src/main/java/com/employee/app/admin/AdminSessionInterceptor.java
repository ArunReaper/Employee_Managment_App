package com.employee.app.admin;

import com.employee.app.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Every employee operation needs a logged-in admin (SECURITY_FINDINGS.md SEC-001, SEC-004..006, SEC-010;
 * replaces RULE-018 at the user's request, 2026-10-31). Ported from the hardened legacy AuthFilter.
 */
@Configuration
public class AdminSessionInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    static final String LOGIN_REQUIRED = "Please log in to continue.";

    static boolean isLoggedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(AuthController.SESSION_ADMIN_NAME) != null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!isLoggedIn(request)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, LOGIN_REQUIRED);
        }
        return true;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/employees", "/api/employees/**");
    }
}
