# employee-app REST API contract

This is the API the Spring Boot backend (`backend/`) exposes and the React app (`frontend/`) calls. Each endpoint replaces one legacy servlet or JSP scriptlet. The behaviour is the legacy behaviour (rule ids are from `analysis/employee-app/BUSINESS_RULES.md`), except where marked **[deviation]**. Deviations were approved by the user on 2026-10-31: "Please give appropriet messages in case of error".

General:
- Base path `/api`. All bodies are JSON. **Every response that is not a list is `{"message": "<text>"}`**, except `/api/auth/me` and `/api/auth/login` (shown below).
- **Request fields are strings, never numbers**, exactly as the legacy form posted them, so that parsing and validation are the legacy parsing. The backend parses IDs with `Integer.parseInt` and salaries with `Double.parseDouble`, as the legacy app did.
- **Every `/api/employees` endpoint requires a logged-in admin** (session attribute `adminName`); without one → **401** `{"message": "Please log in to continue."}`. **[hardening]** This replaces RULE-018 (no endpoint required a login) at the user's request on 2026-10-31 ("apply the patch and port the fixes to the modernized app"), matching the hardened legacy app (`analysis/employee-app/security_remediation.patch`, finding ids from `analysis/employee-app/SECURITY_FINDINGS.md`). `/api/auth/*` stays public.
- The session cookie is sent with `HttpOnly; SameSite=Lax`, so cross-site POST, PATCH and DELETE requests carry no session (SEC-014). **[hardening]**
- A database that cannot be reached (connection failure) gives **503** `{"message": "We couldn't reach the database. Please try again later."}` on every endpoint. **[deviation]**
- Any other unexpected database error, where the endpoint below names no specific message, gives **500** `{"message": "Something went wrong. Please try again."}`. **[deviation]** Legacy showed an empty list, a misleading "not found", or a stack trace.
- Text longer than 255 characters (the `VARCHAR(255)` columns) gives **400**. The messages are given per endpoint below. **[deviation]** Legacy gave a generic error.
- Java packages (fixed, so that tests can mock the data layer for failure injection):
  - `com.employee.app.employee.EmployeeRepository`, a Spring bean with methods:
    - `List<Employee> findAll()`
    - `void insert(Employee e)`
    - `int updateSalary(int id, double salary)`
    - `int updateDepartment(int id, String department)`
    - `int deleteByIdAndName(int id, String name)`
  - `com.employee.app.admin.AdminRepository`, with methods:
    - `void insert(Admin a)`
    - `boolean existsByIdAndName(int id, String name)`
  - `record com.employee.app.employee.Employee(int id, String name, String department, double salary)`
  - `record com.employee.app.admin.Admin(int id, String name)`

---

## GET /api/employees  (legacy: ShowEmployeeServlet + showEmployee.jsp)

**200** with a JSON array. Each element is `{"id": 101, "name": "Asha", "department": "Finance", "salary": 15000.0, "salaryText": "15000.0"}`.
- **All rows**, with no filter and no guaranteed order (RULE-023).
- `salaryText` is exactly Java's `Double.toString(salary)`, which is what the JSP printed (RULE-002). Examples:
  - `15000` → `"15000.0"`
  - `1.0E7` → `"1.0E7"`
  - `15000.005` → `"15000.005"`
  - `-500` → `"-500.0"`
  - `0.001` → `"0.001"`
  - `1.0E-4` → `"1.0E-4"`
  - NaN → `"NaN"`
  - +∞ → `"Infinity"`
  - −∞ → `"-Infinity"`
- `salary` is the number, or `null` when it is NaN or infinite (JSON cannot carry those).
- `name` and `department` may be `null` (the columns are nullable). The UI then shows the text `null`, as the JSP did.
- Empty table → `[]`.
- Database read failure → **503** (connection) or **500** (any other error), each with its message. **[deviation from RULE-025]** Legacy showed an empty table.

## POST /api/employees  (legacy: AddEmployeeServlet; RULE-003/004/005/010)

Body: `{"id": "101", "name": "Asha", "department": "Finance", "salary": "15000"}`. Checked in this order, and the first failure wins:

1. Any field missing, or blank after trim → **400** "Please fill in all fields."
2. `Integer.parseInt(id.trim())` fails → **400** "Employee ID must be a whole number, e.g. 101."
   - `"0"`, `"-5"` and `"+7"` are **accepted**: the server does not require ≥ 1 (RULE-010).
3. `Double.parseDouble(salary.trim())` fails → **400** "Salary must be a number, e.g. 15000."
   - `"1e3"` and `"NaN"` parse.
4. Salary `< 0` → **400** "Salary cannot be negative."
   - `"NaN"` passes, because NaN < 0 is false. `"Infinity"` passes.
5. Trimmed name or department longer than 255 characters → **400** "Name and department must be 255 characters or fewer." **[deviation]**
6. The ID already exists (SQLState 23505) → **409** "An employee with this ID already exists. Please choose another ID."
7. Any other database error that is not a connection failure → **500** "Error while adding employee. Please try again."

On success, the row is stored with trimmed name and department and the parsed id and salary → **201** "Employee added successfully!"

## PATCH /api/employees/{id}  (legacy: EditEmployeeServlet; RULE-006/007/016)

Body: `{"name": "Asha", "field": "salary", "newValue": "18000"}`. Checked in this order:

1. `name`, `field` or `newValue` is **null or missing** → **400** "Invalid data! Please try again."
   - Empty strings are **not** rejected here, matching the legacy null-only check.
2. `Integer.parseInt({id})` fails → **400** "Employee ID must be a whole number, e.g. 101." **[deviation]** Legacy gave HTTP 500.
3. `field`, compared case-insensitively, is neither `salary` nor `department` → **400** "Field must be either salary or department." **[deviation]** Legacy text: "Employee not found or invalid data!".
4. The field is salary and `Double.parseDouble(newValue)` fails → **400** "Salary must be a number, e.g. 15000." **[deviation]** Same legacy text as above.
   - **No range check:** negative, NaN and Infinity are accepted (RULE-007/016, confirmed by the reviewer).
   - `newValue` is not trimmed beforehand, but `parseDouble` itself ignores surrounding whitespace.
5. The field is department and `newValue` is longer than 255 characters → **400** "Department must be 255 characters or fewer." **[deviation]**
   - The department is stored **as given**: untrimmed, and an empty string is allowed.
6. No row with that id → **404** "Employee not found!" **[deviation]** Legacy text: "Employee not found or invalid data!".

The employee is located by **id only**. `name` is required but **never compared** with the stored name (RULE-016, confirmed). On success → **200** "Employee details updated successfully!"

## DELETE /api/employees/{id}?name=<name>  (legacy: DeleteEmployeeServlet; RULE-013/014)

1. `name` query parameter missing → **400** "Invalid data! Please try again."
2. `Integer.parseInt({id})` fails → **400** "Employee ID must be a whole number, e.g. 101." **[deviation]** Legacy gave HTTP 500.
3. Deletes only when id **and** name match exactly: case-sensitive, not trimmed (RULE-013).
4. Nothing matched → **404** "Employee not found!" **[deviation from RULE-014]** Legacy lost this message and redirected as if the delete worked.

On success → **200** "Employee deleted successfully!"

## POST /api/auth/signup  (legacy: SignUpServlet at /SignupServlet; RULE-008/009/012)

Body: `{"id": "101", "name": "Ravi"}`.

1. `Integer.parseInt(id.trim())` fails, or `id` is missing → **400** "Admin ID must be a whole number, e.g. 101."
2. `name` is stored **trimmed**. A missing name is stored as `NULL`, and a blank name as `""`; neither is rejected (RULE-008, legacy behaviour).
3. Trimmed name longer than 255 characters → **400** "Name must be 255 characters or fewer." **[deviation]**
4. The ID already exists (23505) → **409** "An admin with this ID already exists. Please choose another ID."
5. Any other non-connection database error → **500** "Error during sign up. Please try again."

Before the insert (after steps 1 and 3): when the caller is **not** logged in and `admin_data` already has a row → **403** "Sign-up is closed. Ask an existing admin to create your account." **[hardening, SEC-007]** Anonymous sign-up therefore only creates the first admin (create it before exposing the app); after that, a logged-in admin signs up the others. Replaces RULE-009 (open self-registration). The setting `app.security.open-signup` (env `APP_SECURITY_OPEN_SIGNUP`, default `false`) switches this gate off, so anyone may sign up as in legacy; it is meant for demos, not production.

On success → **201** "Sign up successful! Please log in."

## POST /api/auth/login  (legacy: Login.jsp scriptlet; RULE-017 confirmed, RULE-008)

Body: `{"id": "101", "name": "Ravi"}`.
- `id` is trimmed and parsed as an integer. `name` is used **exactly as typed**: not trimmed, case-sensitive.
- Success, when a row in `admin_data` matches id and name → **200** `{"adminName": "<name as typed>", "message": "Welcome, <name>!"}`. The server-side HTTP session gets the attribute `adminName` = name.
- Any failure (id or name missing, non-numeric id, no match) → **401** "Invalid ID or Name. Please try again."
- **Lockout [hardening, SEC-009]:** each attempt is reserved *before* the check. After 5 attempts in a row from one client address, or 5 against one admin id (from any addresses), that key is locked for 15 minutes → **429** "Too many failed attempts. Please try again in 15 minutes.", even with the right name. A success resets both counts. A locked address never adds to an id's count. A non-numeric id counts against the address only.
- There is **no password** (confirmed; SEC-008/011 stay open because the schema is off limits). The session id **is rotated** at login **[hardening, SEC-012]**; legacy kept it (session fixation).

## GET /api/auth/me  (legacy: Welcome.jsp reading sessionScope.adminName)

**200** `{"adminName": "<name>"}` from the session, or `{"adminName": null}` when nobody has logged in. The UI uses it to send a visitor who is not logged in to the login page.

## POST /api/auth/logout  (legacy: hardened LogoutServlet; SEC-020)

Ends the session (`invalidate()`) → **200** "You have been logged out." Also 200 when there is no session. **[hardening]** Replaces RULE-015 (logout went to the home page and the session survived).

## Dropped

- `POST /LoginServlet` (unreachable from the UI): dropped per brief Q7.
- `EmployeeDAO.validateEmployee`, `AdminDAO.getAdminById`, `AdminDAO.addAdmin` (dead code): not migrated.
