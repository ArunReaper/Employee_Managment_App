package com.employee.dao;

import com.employee.model.Admin;
import com.employee.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AdminDAO {

    // Add Admin (Sign Up)
    public void addAdmin(Admin admin) throws SQLException {
        saveAdmin(admin);
    }


    // Save admin details to the database; failures are thrown so the caller can report them
    public static void saveAdmin(Admin admin) throws SQLException {
        String sql = "INSERT INTO admin_data (id, name) VALUES (?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, admin.getId());
            ps.setString(2, admin.getName());
            ps.executeUpdate();
        }
    }

    // Check Admin Login
    public boolean validateAdmin(int id, String name) {
        String sql = "SELECT 1 FROM admin_data WHERE id = ? AND name = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Get Admin by ID
    public Admin getAdminById(int id) {
        String sql = "SELECT id, name FROM admin_data WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Admin admin = new Admin();
                admin.setId(rs.getInt("id"));
                admin.setName(rs.getString("name"));
                return admin;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
