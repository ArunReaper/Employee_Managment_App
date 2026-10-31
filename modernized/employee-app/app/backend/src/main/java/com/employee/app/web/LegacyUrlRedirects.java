package com.employee.app.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Permanently redirects the legacy JSP and servlet page URLs to the new routes, so bookmarks keep working. */
@Configuration
public class LegacyUrlRedirects implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        redirect(registry, "/index.jsp", "/");
        redirect(registry, "/signup.jsp", "/signup");
        redirect(registry, "/Login.jsp", "/login");
        redirect(registry, "/Welcome.jsp", "/welcome");
        redirect(registry, "/ShowEmployeeServlet", "/employees");
        redirect(registry, "/showEmployee.jsp", "/employees");
        redirect(registry, "/addEmployee.jsp", "/employees/add");
        redirect(registry, "/editEmployee.jsp", "/employees/edit");
        redirect(registry, "/deleteEmployee.jsp", "/employees/delete");
    }

    private static void redirect(ViewControllerRegistry registry, String legacy, String target) {
        registry.addRedirectViewController(legacy, target).setStatusCode(HttpStatus.MOVED_PERMANENTLY);
    }
}
