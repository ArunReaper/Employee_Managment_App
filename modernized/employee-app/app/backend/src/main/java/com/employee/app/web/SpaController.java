package com.employee.app.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the React app's index.html for its client-side routes (keep in step with App.jsx). */
@Controller
public class SpaController {

    @GetMapping({"/signup", "/login", "/welcome", "/employees", "/employees/add", "/employees/edit", "/employees/delete"})
    public String app() {
        return "forward:/index.html";
    }
}
