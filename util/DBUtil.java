package com.employee.util;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hands out connections from the Tomcat-managed pool defined in
 * webapp/META-INF/context.xml (resource name "jdbc/EmployeeDB").
 */
public class DBUtil {
    private static final DataSource dataSource;

    static {
        try {
            dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/EmployeeDB");
        } catch (NamingException ex) {
            System.err.println("DataSource lookup failed." + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
