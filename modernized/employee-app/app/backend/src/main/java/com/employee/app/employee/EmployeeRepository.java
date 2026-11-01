package com.employee.app.employee;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Data access for employee_data, with the same SQL as the legacy EmployeeDAO. Unlike the DAO,
 * failures propagate instead of being swallowed as "false" or "null".
 */
@Repository
public class EmployeeRepository {

    private final JdbcClient jdbc;

    public EmployeeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // RULE-023: every row, no filter, no ORDER BY (the legacy query)
    public List<Employee> findAll() {
        return jdbc.sql("SELECT id, name, department, salary FROM employee_data")
                .query((rs, n) -> new Employee(rs.getInt("id"), rs.getString("name"),
                        rs.getString("department"), rs.getDouble("salary")))
                .list();
    }

    public void insert(Employee e) {
        jdbc.sql("INSERT INTO employee_data (id, name, department, salary) VALUES (?, ?, ?, ?)")
                .params(e.id(), e.name(), e.department(), e.salary())
                .update();
    }

    // RULE-016: located by id only
    public int updateSalary(int id, double salary) {
        return jdbc.sql("UPDATE employee_data SET salary = ? WHERE id = ?").params(salary, id).update();
    }

    public int updateDepartment(int id, String department) {
        return jdbc.sql("UPDATE employee_data SET department = ? WHERE id = ?").params(department, id).update();
    }

    // RULE-013: id and exact name must both match
    public int deleteByIdAndName(int id, String name) {
        return jdbc.sql("DELETE FROM employee_data WHERE id = ? AND name = ?").params(id, name).update();
    }
}
