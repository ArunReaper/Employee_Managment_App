package com.employee.servlet;

import com.employee.dao.AdminDAO;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Lets a request through only when the session holds a logged-in admin ("adminName"),
 * except for the public pages below. Sign-up stays open only until the first admin exists;
 * after that, only a logged-in admin can create another one.
 */
@WebFilter("/*")
public class AuthFilter extends HttpFilter {
    private static final long serialVersionUID = 1L;

    private static final Set<String> PUBLIC_PATHS = new HashSet<>(Arrays.asList(
            "", "/", "/index.jsp", "/Login.jsp", "/LoginServlet", "/styles.css"));

    private static final Set<String> SIGNUP_PATHS = new HashSet<>(Arrays.asList(
            "/signup.jsp", "/SignupServlet"));

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getServletPath();
        if (PUBLIC_PATHS.contains(path) || isLoggedIn(request)
                || (SIGNUP_PATHS.contains(path) && !new AdminDAO().hasAnyAdmin())) {
            chain.doFilter(request, response);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/Login.jsp");
    }

    private static boolean isLoggedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("adminName") != null;
    }
}
