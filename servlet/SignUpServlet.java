/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.employee.servlet;

import com.employee.dao.AdminDAO;
import com.employee.model.Admin;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/SignupServlet")
public class SignUpServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String idParam = request.getParameter("id");
        String name = request.getParameter("name");

        int id;
        try {
            id = Integer.parseInt(idParam == null ? "" : idParam.trim());
        } catch (NumberFormatException e) {
            response.getWriter().println("<script>alert('Admin ID must be a whole number, e.g. 101.'); window.location='signup.jsp';</script>");
            return;
        }

        try {
            Admin admin = new Admin();
            admin.setId(id);
            admin.setName(name == null ? null : name.trim());

            AdminDAO.saveAdmin(admin);

            // Show success alert and redirect to login page
            response.getWriter().println("<script>alert('Sign up successful! Please log in.'); window.location='Login.jsp';</script>");
        } catch (SQLException e) {
            e.printStackTrace();
            // 23505 = PostgreSQL unique_violation (ID already taken)
            String message = "23505".equals(e.getSQLState())
                    ? "An admin with this ID already exists. Please choose another ID."
                    : "Error during sign up. Please try again.";
            response.getWriter().println("<script>alert('" + message + "'); window.location='signup.jsp';</script>");
        }
    }
}
