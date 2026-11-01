/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.employee.servlet;

import com.employee.dao.AdminDAO;
import com.employee.model.Admin;
import com.employee.util.LoginThrottle;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;


@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = Integer.parseInt(request.getParameter("id"));
        String name = request.getParameter("name");

        // Reserve the attempt before checking, so parallel guesses cannot pass the limit
        String client = request.getRemoteAddr();
        if (!LoginThrottle.tryAcquire(client, id)) {
            response.getWriter().println("<script>alert('Too many failed attempts. Please try again in 15 minutes.');window.location='Login.jsp';</script>");
            return;
        }

        AdminDAO adminDAO = new AdminDAO();  // Create an instance of AdminDAO
        boolean isValid = adminDAO.validateAdmin(id, name);  // Call the method on the object

        if (isValid) {
            LoginThrottle.reset(client, id);
            HttpSession session = request.getSession();
            request.changeSessionId(); // new session id at login (session fixation)
            session.setAttribute("adminName", name);
            response.sendRedirect("Welcome.jsp");
        } else {
            response.getWriter().println("<script>alert('Incorrect name or id');window.location='Login.jsp';</script>");
        }
    }
}
