package com.employee.app.admin;

/** One row of admin_data (schema unchanged): an id chosen at sign-up and a name; no password. */
public record Admin(int id, String name) {
}
