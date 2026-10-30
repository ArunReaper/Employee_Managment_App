package com.employee.dao;

import com.employee.model.Employee;
import com.employee.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class EmployeeDAO {

    /**
     * Save an employee to the database.
     * @param employee Employee object to save
     */
    public void addEmployee(Employee employee) {
        String sql = "INSERT INTO employee_data (id, name, department, salary) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employee.getId());
            ps.setString(2, employee.getName());
            ps.setString(3, employee.getDepartment());
            ps.setDouble(4, employee.getSalary());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Update employee details in the database.
     * @param id Employee ID
     * @param field Field to update (e.g., "salary" or "department")
     * @param newValue New value for the field
     * @return true if update is successful, false otherwise
     */
    public boolean updateEmployee(int id, String field, String newValue) {
        try (Connection conn = DBUtil.getConnection()) {
            PreparedStatement ps;
            if ("salary".equalsIgnoreCase(field)) {
                ps = conn.prepareStatement("UPDATE employee_data SET salary = ? WHERE id = ?");
                ps.setDouble(1, Double.parseDouble(newValue));
            } else if ("department".equalsIgnoreCase(field)) {
                ps = conn.prepareStatement("UPDATE employee_data SET department = ? WHERE id = ?");
                ps.setString(1, newValue);
            } else {
                return false;
            }
            try (PreparedStatement update = ps) {
                update.setInt(2, id);
                return update.executeUpdate() > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Delete an employee from the database.
     * @param id Employee ID
     * @param name Employee Name
     * @return true if deletion is successful, false otherwise
     */
    public boolean deleteEmployee(int id, String name) {
        String sql = "DELETE FROM employee_data WHERE id = ? AND name = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Get all employees from the database.
     * @return List of Employee objects
     */
    public List<Employee> getAllEmployees() {
        String sql = "SELECT id, name, department, salary FROM employee_data";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Employee> employees = new ArrayList<>();
            while (rs.next()) {
                Employee employee = new Employee(rs.getString("name"), rs.getString("department"), rs.getDouble("salary"));
                employee.setId(rs.getInt("id"));
                employees.add(employee);
            }
            return employees;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Validate employee login.
     * @param id Employee ID
     * @param name Employee Name
     * @return true if employee exists, false otherwise
     */
    public boolean validateEmployee(int id, String name) {
        String sql = "SELECT 1 FROM employee_data WHERE id = ? AND name = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
