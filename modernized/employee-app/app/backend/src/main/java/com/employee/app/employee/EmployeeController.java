package com.employee.app.employee;

import java.util.List;

import com.employee.app.web.ApiMessage;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces ShowEmployeeServlet, AddEmployeeServlet, EditEmployeeServlet and DeleteEmployeeServlet.
 * Every operation needs a logged-in admin (AdminSessionInterceptor; SEC-001, replaces RULE-018).
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    public List<EmployeeView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiMessage add(@RequestBody EmployeeRequests.Add request) {
        service.add(request);
        return new ApiMessage("Employee added successfully!");
    }

    // the id stays text so a non-number gets the validation message, not a framework 400
    @PatchMapping("/{id}")
    public ApiMessage edit(@PathVariable String id, @RequestBody EmployeeRequests.Edit request) {
        service.edit(id, request);
        return new ApiMessage("Employee details updated successfully!");
    }

    @DeleteMapping("/{id}")
    public ApiMessage delete(@PathVariable String id, @RequestParam(required = false) String name) {
        service.delete(id, name);
        return new ApiMessage("Employee deleted successfully!");
    }
}
