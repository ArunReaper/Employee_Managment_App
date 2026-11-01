package com.employee.app.admin;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Data access for admin_data, with the same SQL as the legacy AdminDAO. */
@Repository
public class AdminRepository {

    private final JdbcClient jdbc;

    public AdminRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Admin admin) {
        jdbc.sql("INSERT INTO admin_data (id, name) VALUES (?, ?)").params(admin.id(), admin.name()).update();
    }

    // SEC-007: sign-up is open only while this is false
    public boolean anyExists() {
        return !jdbc.sql("SELECT 1 FROM admin_data LIMIT 1").query(Integer.class).list().isEmpty();
    }

    // RULE-017: exact, case-sensitive match on id and name
    public boolean existsByIdAndName(int id, String name) {
        return !jdbc.sql("SELECT 1 FROM admin_data WHERE id = ? AND name = ?")
                .params(id, name)
                .query(Integer.class)
                .list()
                .isEmpty();
    }
}
