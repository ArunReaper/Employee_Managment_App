/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.employee.servlet;

import com.employee.dao.EmployeeDAO;
import com.employee.model.Employee;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

@WebServlet("/AddEmployeeServlet")
public class AddEmployeeServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String name = request.getParameter("name");
        String id = request.getParameter("id");
        String salary = request.getParameter("salary");
        String department = request.getParameter("department");

        if (isBlank(name) || isBlank(id) || isBlank(salary) || isBlank(department)) {
            showAlert(response, "Please fill in all fields.");
            return;
        }

        int employeeId;
        try {
            employeeId = Integer.parseInt(id.trim());
        } catch (NumberFormatException e) {
            showAlert(response, "Employee ID must be a whole number, e.g. 101.");
            return;
        }

        double employeeSalary;
        try {
            employeeSalary = Double.parseDouble(salary.trim());
        } catch (NumberFormatException e) {
            showAlert(response, "Salary must be a number, e.g. 15000.");
            return;
        }
        if (employeeSalary < 0) {
            showAlert(response, "Salary cannot be negative.");
            return;
        }

        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setName(name.trim());
        employee.setSalary(employeeSalary);
        employee.setDepartment(department.trim());

        try {
            EmployeeDAO employeeDao = new EmployeeDAO();
            employeeDao.addEmployee(employee);
        } catch (SQLException e) {
            e.printStackTrace();
            // 23505 = PostgreSQL unique_violation (ID already taken)
            showAlert(response, "23505".equals(e.getSQLState())
                    ? "An employee with this ID already exists. Please choose another ID."
                    : "Error while adding employee. Please try again.");
            return;
        }

        response.getWriter().println("<script>alert('Employee added successfully!'); window.location.href='Welcome.jsp';</script>");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // Show an alert and send the user back to the form
    private static void showAlert(HttpServletResponse response, String message) throws IOException {
        response.getWriter().println("<script>alert('" + message + "'); window.location.href='addEmployee.jsp';</script>");
    }
}
