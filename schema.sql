-- Run once against MySQL before deploying (previously Hibernate created these tables).
CREATE DATABASE IF NOT EXISTS employee;
USE employee;

CREATE TABLE IF NOT EXISTS admin_data (
    id   INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS employee_data (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255),
    department VARCHAR(255),
    salary     DOUBLE NOT NULL
);
