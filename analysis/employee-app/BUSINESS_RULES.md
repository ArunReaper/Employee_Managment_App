# Business Rules — employee-app

At extraction: 25 confirmed rules (3 P0); later steps may add or correct rules below. Each citation was checked by a second agent that read the cited lines; 0 candidate rules were refuted and left out.

| ID | Name | Category | Priority | Source | Confidence |
|---|---|---|---|---|---|
| RULE-001 | Salary is stored as unrounded floating point | Calculation | P1 | `schema.sql:10-15` | Medium |
| RULE-002 | Salary shown in the employee list as a raw unformatted number | Calculation | P2 | `webapp/showEmployee.jsp:26-40` | High |
| RULE-003 | New employee record validation and creation | Validation | P1 | `servlet/AddEmployeeServlet.java:22-73` | High |
| RULE-004 | New employee record must pass completeness, ID and salary checks | Validation | P1 | `servlet/AddEmployeeServlet.java:24-58` | High |
| RULE-005 | Employee ID must be unique | Validation | P1 | `servlet/AddEmployeeServlet.java:60-72` | High |
| RULE-006 | Edit employee request guard: four fields required, but the supplied name is nev… | Validation | P1 | `servlet/EditEmployeeServlet.java:20-41` | High |
| RULE-007 | Salary edits skip the non-negative and format checks that apply when an employe… | Validation | P1 | `servlet/EditEmployeeServlet.java:22-29` | Medium |
| RULE-008 | Admin name must match exactly as stored, and blank names are allowed at sign-up | Validation | P1 | `servlet/SignUpServlet.java:20-49` | High |
| RULE-009 | Open admin self-registration with a unique numeric admin ID | Validation | P1 | `servlet/SignUpServlet.java:20-49` | High |
| RULE-010 | Employee and admin IDs: browser requires a positive whole number, server accept… | Validation | P2 | `webapp/addEmployee.jsp:18-31` | High |
| RULE-011 | Department is free text with no reference list | Validation | P2 | `webapp/editEmployee.jsp:25-32` | Medium |
| RULE-012 | Admin accounts have a create-only lifecycle | Lifecycle | P1 | `dao/AdminDAO.java:13-65` | High |
| RULE-013 | Deleting an employee requires both ID and exact name to match | Lifecycle | P1 | `dao/EmployeeDAO.java:67-78` | High |
| RULE-014 | Failed delete still sends the user to the home page as if it worked | Lifecycle | P1 | `servlet/DeleteEmployeeServlet.java:25-37` | Medium |
| RULE-015 | Logout does not end the admin session | Lifecycle | P1 | `webapp/Welcome.jsp:62-64` | High |
| RULE-016 | Edit employee: only salary or department can change, and the employee is found… | Policy | P0 | `dao/EmployeeDAO.java:39-59` | Medium |
| RULE-017 | Admin login by matching ID and name (no password) | Policy | P0 | `webapp/Login.jsp:76-94` | High |
| RULE-018 | Employee operations are not restricted to logged-in admins | Policy | P0 | `webapp/Welcome.jsp:52-64` | Medium |
| RULE-019 | Record IDs are typed in by users even though the database can generate them | Policy | P1 | `schema.sql:5-15` | Medium |
| RULE-020 | Second admin login endpoint with weaker input handling (LoginServlet) | Policy | P1 | `servlet/LoginServlet.java:21-34` | Medium |
| RULE-021 | Unused employee identity check (validateEmployee) and admin lookup (getAdminByI… | Policy | P2 | `dao/EmployeeDAO.java:102-121` | Low |
| RULE-022 | Unused employee self-login check (ID plus name) | Policy | P2 | `dao/EmployeeDAO.java:108-121` | Low |
| RULE-023 | Employee listing shows all records unfiltered | Policy | P2 | `dao/EmployeeDAO.java:84-100` | Medium |
| RULE-024 | Success confirmations are stored but never shown | Policy | P2 | `webapp/Welcome.jsp:52-59` | High |
| RULE-025 | Employee list shows an empty table when the employee data cannot be read | Policy | P2 | `webapp/showEmployee.jsp:26-40` | High |

## Calculation

### RULE-001: Salary is stored as unrounded floating point
**Category:** Calculation
**Priority:** P1
**Source:** `schema.sql:10-15`
**Plain English:** Salary is held as a binary floating-point number with no currency, rounding or precision rule; whatever decimal the admin enters is stored and shown as Java's raw double text.
**Specification:**
  Given An admin adds an employee with salary '15000.005' via a direct request (bypassing the browser's step=0.01)
  When  The employee list is displayed
  Then  Salary is stored as DOUBLE PRECISION 15000.005 and displayed via the raw double (e.g. '15000.005'; 15000 shows as '15000.0', large values may show in scientific notation like '1.0E7')
**Parameters:** Column salary DOUBLE PRECISION NOT NULL; Java type double (model/Employee.java:7); display at webapp/showEmployee.jsp:35 with no formatting; client hint step=0.01 (webapp/addEmployee.jsp:29). No currency, no period (annual/monthly) defined.
**Edge cases handled:** Edits store any parsed double, including negatives (see edit rule); No upper limit on salary
**Suspected defect:** Money stored as floating point invites rounding error; no currency or pay-period semantics.
**Confidence:** Medium — What does salary represent (annual or monthly, which currency), and should it be stored as a fixed-point decimal rounded to 2 places with a defined maximum?

### RULE-002: Salary shown in the employee list as a raw unformatted number
**Category:** Calculation
**Priority:** P2
**Source:** `webapp/showEmployee.jsp:26-40`
**Plain English:** The employee list prints each salary using Java's default double-to-text conversion, with no currency symbol, no fixed two decimals and no rounding.
**Specification:**
  Given Employees have salaries 50000, 1234.5 and 12000000
  When  An admin opens 'Show List of Employees'
  Then  The salaries appear as '50000.0', '1234.5' and '1.2E7' (scientific notation from 10,000,000 upward)
**Parameters:** No locale, currency or decimal format is applied. Values come from emp.getSalary() via the <%= %> expression.
**Edge cases handled:** When the DAO returns null on a database error, the table is shown empty with no error message (lines 27-28); Values that cannot be represented exactly, such as 0.1 + 0.2 accumulations, may display as 0.30000000000000004; Name and department are printed without HTML escaping
**Confidence:** High

## Validation

### RULE-003: New employee record validation and creation
**Category:** Validation
**Priority:** P1
**Source:** `servlet/AddEmployeeServlet.java:22-73`
**Plain English:** A new employee needs an ID, name, department, and salary, all non-blank. The ID must be a whole number, the salary must be a number that is not negative, and the ID must not already be in use.
**Specification:**
  Given No employee with ID 205 exists
  When  An admin submits id=205, name=' Ravi ', department='Finance', salary='15000.50'
  Then  An employee_data row (205, 'Ravi', 'Finance', 15000.50) is stored with text trimmed, and the alert 'Employee added successfully!' sends the user to Welcome.jsp
**Parameters:** Minimum salary = 0 (inclusive); duplicate-ID SQLSTATE = 23505; salary stored as DOUBLE PRECISION NOT NULL (schema.sql:10-15); client form hints id min=1, salary min=0 step=0.01 (addEmployee.jsp:20,29)
**Edge cases handled:** Any blank field -> 'Please fill in all fields.'; id='10.5' or 'abc' -> 'Employee ID must be a whole number'; salary='abc' -> 'Salary must be a number'; salary=-1 -> 'Salary cannot be negative.'; salary=0 is accepted; Duplicate ID -> 'An employee with this ID already exists'; Server does not enforce ID >= 1 or 2-decimal salary precision; 'NaN' or 'Infinity' parse as valid doubles and pass the negative check; Salary is stored as floating point, not exact currency
**Suspected defect:** Salary is stored as a binary floating-point number (DOUBLE) and is not rounded to cents. Values like NaN or Infinity pass validation.
**Confidence:** High

### RULE-004: New employee record must pass completeness, ID and salary checks
**Category:** Validation
**Priority:** P1
**Source:** `servlet/AddEmployeeServlet.java:24-58`
**Also cited:** servlet/AddEmployeeServlet.java:24-58
**Plain English:** Before an employee is created, all four fields (ID, name, department, salary) must be filled in, the ID must be a whole number, and the salary must be a number that is zero or more; name and department are trimmed before saving.
**Specification:**
  Given An admin submits the Add Employee form with id='101', name='  Asha ', department='Finance', salary='-500'
  When  The add-employee request is processed
  Then  The request is rejected with 'Salary cannot be negative.' and nothing is saved; with salary='15000' the employee is saved as id 101, name 'Asha', department 'Finance', salary 15000.0
**Parameters:** Minimum salary = 0 (inclusive). ID must parse as a 32-bit integer after trim. Salary parsed as double (any decimals, no rounding). Browser form also sets id min=1 step=1 and salary min=0 step=0.01 (webapp/addEmployee.jsp:20,29) but the server does NOT enforce ID >= 1 or 2-decimal salary.
**Edge cases handled:** Blank or whitespace-only field -> 'Please fill in all fields.'; id='10.5' or 'abc' -> 'Employee ID must be a whole number, e.g. 101.'; salary='abc' -> 'Salary must be a number, e.g. 15000.'; salary='0' is accepted; Server accepts id=0 or negative id even though the form requires min=1; salary 'NaN' or 'Infinity' parse successfully in Java and pass the >= 0 check (Infinity) or comparison (NaN < 0 is false) and would be stored
**Suspected defect:** Server accepts salary='NaN' (NaN < 0 is false) and 'Infinity', and accepts non-positive IDs; only the browser enforces ID >= 1 and cent precision. | The server does not require ID >= 1 (only the browser does). Salary is kept as a binary floating-point value, not a fixed-point currency amount.
**Confidence:** High

### RULE-005: Employee ID must be unique
**Category:** Validation
**Priority:** P1
**Source:** `servlet/AddEmployeeServlet.java:60-72`
**Plain English:** Each employee has a unique ID chosen by the admin; adding an employee with an ID that already exists is refused.
**Specification:**
  Given An employee with ID 101 already exists
  When  An admin tries to add another employee with ID 101
  Then  The database rejects the insert (unique violation 23505) and the admin sees 'An employee with this ID already exists. Please choose another ID.'; on success the admin sees 'Employee added successfully!' and returns to Welcome
**Parameters:** Uniqueness enforced by employee_data.id PRIMARY KEY (schema.sql:11); PostgreSQL SQLSTATE 23505 mapped to the duplicate message. ID is admin-supplied, not generated.
**Edge cases handled:** Any other database error -> generic 'Error while adding employee. Please try again.'; Schema uses GENERATED BY DEFAULT identity, so a mix of supplied and generated IDs could collide later
**Confidence:** High

### RULE-006: Edit employee request guard: four fields required, but the supplied name is never verified
**Category:** Validation
**Priority:** P1
**Source:** `servlet/EditEmployeeServlet.java:20-41`
**Plain English:** An edit request must include employee ID, employee name, the field to change and a new value, but the name is only checked for presence and is never compared with the stored employee, so knowing the ID is enough to change a record.
**Specification:**
  Given Employee 101 'Alice Smith' in department 'Sales' exists
  When  An edit is submitted with id=101, name='Bob', field='department', newValue='Finance'
  Then  Alice's department becomes 'Finance' and 'Employee details updated successfully!' is shown. If any of the four parameters is missing, 'Invalid data! Please try again.' is shown. If the update matches no row or the field is not salary/department, 'Employee not found or invalid data!' is shown.
**Parameters:** Required parameters: id, name, field, newValue (only a null check, so empty strings pass). The form offers field values 'department' and 'salary' (webapp/editEmployee.jsp:25-29). The ID input is a free-text box with no numeric constraint (editEmployee.jsp:20).
**Edge cases handled:** A non-numeric ID such as 'abc' reaches Integer.parseInt without a try/catch (servlet line 29), so the user gets an HTTP 500 error instead of the 'invalid data' alert; An empty newValue '' passes the null check; with field=department the department is set to an empty string; The delete flow requires ID and name to match, but edit does not, so the two operations apply different identity checks
**Suspected defect:** The edit form and servlet ask for the employee name, which suggests it was meant as a second identity check like delete, but the name is never passed to the DAO. Non-numeric IDs are not caught.
**Confidence:** High

### RULE-007: Salary edits skip the non-negative and format checks that apply when an employee is added
**Category:** Validation
**Priority:** P1
**Source:** `servlet/EditEmployeeServlet.java:22-29`
**Also cited:** servlet/EditEmployeeServlet.java:27-29; servlet/EditEmployeeServlet.java:20-41
**Plain English:** When salary is changed, the only checks are that all four form fields are present and that the new value parses as a number. Negative, NaN and Infinity values are all accepted, even though adding an employee requires a non-negative salary.
**Specification:**
  Given Employee 101 exists with salary 50,000.00
  When  An admin submits the Edit form with id=101, field=salary, newValue=-5000
  Then  The salary is updated to -5000.0 and the user sees 'Employee details updated successfully!' (stored in the session). A value such as 'NaN' or 'Infinity' is also saved.
**Parameters:** Required params: id, name, field, newValue (null check only, so an empty string passes); allowed field values 'salary' or 'department' (case-insensitive, EmployeeDAO.java:42-45); salary parsed with Double.parseDouble (EmployeeDAO.java:44)
**Edge cases handled:** Empty newValue for department sets department to an empty string; Non-numeric id (the form field is type=text, editEmployee.jsp:20) throws an unhandled NumberFormatException at line 29 and returns an HTTP 500, not the 'Invalid data' alert; Non-numeric salary is caught in the DAO and reported as 'Employee not found or invalid data!'; Name is required by the form, but the servlet never passes it to the DAO, so it is not checked
**Suspected defect:** Salary validation on edit does not match the add rule, so an edit can store a negative or non-finite salary. | Salary integrity rules differ between add and edit. Negative, NaN and Infinity salaries can be stored through edit, and NaN can be stored through add. | Salary changes can produce negative, NaN or Infinity salaries even though the add path forbids negative salaries. The edit form also asks for the employee's name but never checks it, so the admin may think the name is being verified when it is not.
**Confidence:** Medium — Should salary edits follow the same rules as new employees (non-negative, at most 2 decimals), and should the employee name entered on the edit form have to match the stored name?

### RULE-008: Admin name must match exactly as stored, and blank names are allowed at sign-up
**Category:** Validation
**Priority:** P1
**Source:** `servlet/SignUpServlet.java:20-49`
**Plain English:** Sign-up trims the admin name before saving it but does not require a name and only checks that the ID is a whole number. Login compares the name exactly as typed, without trimming and case-sensitively, so some sign-ups cannot log in.
**Specification:**
  Given An admin signs up with id=101 and name 'Root ' (trailing space)
  When  They log in with id=101 and name 'Root '
  Then  The stored name is 'Root', the comparison against 'Root ' fails, and login is refused. Logging in as 'Root' succeeds and 'root' fails.
**Parameters:** No minimum or maximum ID on the server; the browser form requires min=1, step=1 (webapp/signup.jsp:56). Name column is VARCHAR(255) and nullable (schema.sql:5-8).
**Edge cases handled:** A direct POST with no name stores a NULL name, and that admin can never log in because SQL 'name = ?' never matches NULL; A direct POST with a blank name stores '' as the name; IDs of 0 or below are accepted by the server even though the form requires at least 1; An ID-taken conflict (SQLState 23505) shows 'An admin with this ID already exists'
**Suspected defect:** Names are trimmed on write but not on login comparison, and blank or missing names are accepted.
**Confidence:** High

### RULE-009: Open admin self-registration with a unique numeric admin ID
**Category:** Validation
**Priority:** P1
**Source:** `servlet/SignUpServlet.java:20-49`
**Also cited:** servlet/SignUpServlet.java:20-49; servlet/SignUpServlet.java:20-49
**Plain English:** Anyone can register as an admin by choosing a whole-number admin ID that is not already taken and a name; no approval or invitation is needed.
**Specification:**
  Given No admin with ID 5 exists
  When  A visitor submits Sign Up with id=5, name=' Ravi '
  Then  Admin 5 'Ravi' is created and the visitor sees 'Sign up successful! Please log in.'. id='abc' gives 'Admin ID must be a whole number'. If ID 5 already exists, the visitor sees 'An admin with this ID already exists. Please choose another ID.' (SQLSTATE 23505)
**Parameters:** Uniqueness comes from the PRIMARY KEY on admin_data.id (schema.sql:6). The form (webapp/signup.jsp:56) sets min=1 in the browser only.
**Edge cases handled:** The server does not require a name. A missing or empty name is stored as null or '', and that admin can then log in with an empty name only if the browser allows it; An ID of 0 or a negative ID is accepted by the server; There is no approval workflow, invitation code or role level. Every registrant is a full admin
**Suspected defect:** An admin name is not required on the server, and there is no check on who may register as an admin. | Open self-registration with no approval or invitation means any unauthenticated visitor can give themselves full admin rights over employee and salary data. | Unrestricted admin creation plus name-only login means any visitor can obtain full employee-management rights; server accepts empty/null admin names and IDs <= 0.
**Confidence:** High

### RULE-010: Employee and admin IDs: browser requires a positive whole number, server accepts any integer
**Category:** Validation
**Priority:** P2
**Source:** `webapp/addEmployee.jsp:18-31`
**Plain English:** The Add Employee form allows only IDs of 1 or more and salaries of 0 or more with up to 2 decimal places, but the server enforces only 'whole number' for the ID and 'not negative' for the salary, so a direct request can create ID 0, negative IDs, or salaries with more than 2 decimals.
**Specification:**
  Given A client posts directly to AddEmployeeServlet
  When  It sends id=-5, salary=1000.123
  Then  The employee is created with ID -5 and salary 1000.123. Through the form, the browser would reject both values.
**Parameters:** Client-side: ID min=1, step=1; salary min=0, step=0.01 (addEmployee.jsp:20,29). Server-side: Integer.parseInt for the ID; salary must parse and be >= 0 (AddEmployeeServlet.java:34-52).
**Edge cases handled:** The edit and delete forms use free-text ID inputs with no client constraint; The 2-decimal salary precision implied by step=0.01 is never applied on the server
**Suspected defect:** Range and precision rules exist only in the browser.
**Confidence:** High

### RULE-011: Department is free text with no reference list
**Category:** Validation
**Priority:** P2
**Source:** `webapp/editEmployee.jsp:25-32`
**Plain English:** Department is any text the admin types, both on add and on edit. It is not checked against a list of valid departments, so spelling variants become different departments.
**Specification:**
  Given Employee 101 is in department 'Sales'
  When  An admin edits field=department, newValue='sales '
  Then  The department is saved as 'sales ' (case and trailing space kept) and is treated as different from 'Sales'.
**Parameters:** Field selector options: department (default selection), salary; newValue is a type=text required input; add form department field at addEmployee.jsp:25-26
**Edge cases handled:** The edit field defaults to Department, so a salary typed without changing the selector is saved as the department text
**Confidence:** Medium — Is there a canonical department list that values should be validated against?

## Lifecycle

### RULE-012: Admin accounts have a create-only lifecycle
**Category:** Lifecycle
**Priority:** P1
**Source:** `dao/AdminDAO.java:13-65`
**Plain English:** Admins can only be created (sign-up) and looked up. There is no way to deactivate, rename or delete an admin, and admins have no status field.
**Specification:**
  Given Admin id=7 'Carol' has left the organisation
  When  Someone tries to revoke her access
  Then  The application has no operation for it, so the admin row stays valid forever and 'Carol' can still log in with id 7 and her name.
**Parameters:** admin_data columns: id, name only (schema.sql:5-8); operations: saveAdmin (INSERT), validateAdmin, getAdminById (never called by any page or servlet)
**Edge cases handled:** getAdminById is dead code; Nothing enforces unique admin names, so two admins can share a name with different IDs
**Suspected defect:** There is no way to revoke admin access through the application.
**Confidence:** High

### RULE-013: Deleting an employee requires both ID and exact name to match
**Category:** Lifecycle
**Priority:** P1
**Source:** `dao/EmployeeDAO.java:67-78`
**Also cited:** dao/EmployeeDAO.java:67-78; dao/EmployeeDAO.java:67-78
**Plain English:** An employee is permanently removed only when the admin supplies both the employee's ID and their exact stored name; otherwise nothing is deleted.
**Specification:**
  Given Employee 101 is stored with name 'Asha'
  When  An admin submits Delete with id=101 and name='asha'
  Then  No row matches (case- and whitespace-sensitive), nothing is deleted and 'Employee not found!' is intended; with name='Asha' the record is hard-deleted and the session message 'Employee deleted successfully!' is set
**Parameters:** Match condition: id = ? AND name = ? (exact, case-sensitive, no trimming). Hard delete, no soft-delete/audit.
**Edge cases handled:** Missing id or name -> 'Invalid data! Please try again.' (servlet/DeleteEmployeeServlet.java:25,36); Non-numeric id -> uncaught NumberFormatException (servlet/DeleteEmployeeServlet.java:27); Names saved on add are trimmed, so a delete with leading/trailing spaces will not match
**Suspected defect:** On not-found, DeleteEmployeeServlet writes an alert script and then unconditionally calls sendRedirect('Welcome.jsp') (servlet/DeleteEmployeeServlet.java:31-34), so the 'Employee not found!' alert is likely lost and the user silently lands on Welcome. | On a failed delete, DeleteEmployeeServlet.java:31-34 writes an alert script and then redirects anyway, so the user may never see the 'not found' alert. | When the employee is not found, DeleteEmployeeServlet.java:32-34 writes the 'Employee not found!' script and then calls sendRedirect to Welcome.jsp, so the not-found alert is probably never shown and the admin gets no feedback that nothing was deleted.
**Confidence:** High

### RULE-014: Failed delete still sends the user to the home page as if it worked
**Category:** Lifecycle
**Priority:** P1
**Source:** `servlet/DeleteEmployeeServlet.java:25-37`
**Also cited:** servlet/DeleteEmployeeServlet.java:25-37; servlet/DeleteEmployeeServlet.java:22-37
**Plain English:** After a delete attempt, the user is always redirected to the Welcome page, even when no employee matched. The 'Employee not found!' alert is written to the response before the redirect, so it is effectively lost.
**Specification:**
  Given No employee has ID 999 with name 'Bob'
  When  An admin submits the delete form with id=999 and name=Bob
  Then  Nothing is deleted, the 'not found' alert script is written, and then sendRedirect('Welcome.jsp') runs anyway (line 34). The user usually lands on Welcome without any error shown. On success, 'Employee deleted successfully!' is stored in the session.
**Parameters:** Session attribute key 'message' (line 30). Redirect target: Welcome.jsp. Failure target in the alert: deleteEmployee.jsp.
**Edge cases handled:** A non-numeric ID throws an uncaught NumberFormatException (line 27), which shows as an HTTP 500; A blank name passes the null check and simply matches nothing; The success message is put in the session, but no JSP ever reads the 'message' attribute, so users never see success confirmations for edit or delete either
**Suspected defect:** The redirect at line 34 sits outside the if/else, so a failed deletion looks like a success to the user. If the response is already committed, sendRedirect may also throw an IllegalStateException. | On failure the servlet writes an alert and then calls sendRedirect anyway (line 34 runs on both branches), so the failure message is lost. | sendRedirect at line 34 runs on the failure path too. The response has no return/else, so the failure alert is lost or the request errors.
**Confidence:** Medium — When a deletion matches no employee, should the user stay on the delete page with a clear 'not found' error? / Should a failed delete (no ID+name match) send the user back to the delete form with an error, or to the Welcome page?

### RULE-015: Logout does not end the admin session
**Category:** Lifecycle
**Priority:** P1
**Source:** `webapp/Welcome.jsp:62-64`
**Plain English:** Clicking Logout only goes back to the home page; the admin's session (adminName) stays active.
**Specification:**
  Given Admin 'Arun' is logged in
  When  They click Logout
  Then  They are taken to index.jsp, but the session is not invalidated. Going back to Welcome.jsp still shows 'Welcome, Arun!' until the server session times out
**Parameters:** Logout = a form posting to index.jsp. index.jsp (webapp/index.jsp:74-83) has no session code. The session timeout is the container default (not configured).
**Edge cases handled:** On a shared computer, the next user inherits the previous admin's session
**Suspected defect:** Logout should invalidate the session.
**Confidence:** High

## Policy

### RULE-016: Edit employee: only salary or department can change, and the employee is found by ID alone
**Category:** Policy
**Priority:** P0
**Source:** `dao/EmployeeDAO.java:39-59`
**Also cited:** dao/EmployeeDAO.java:39-59; dao/EmployeeDAO.java:39-59
**Plain English:** After an employee is created, only their salary or department can be changed (field names are case-insensitive). Name and ID cannot be changed, and the record is picked by ID only.
**Specification:**
  Given Employee 101 'Priya' in Finance with salary 15000
  When  An admin submits Edit with id=101, name='Anyone', field='salary', newValue='18000'
  Then  Employee 101's salary becomes 18000 and the admin sees 'Employee details updated successfully!'. With field='department', newValue='HR' the department becomes 'HR'. Any other field name, an unknown ID, or a salary that is not a number shows 'Employee not found or invalid data!'
**Parameters:** Fields that can be edited: 'salary' and 'department' (case-insensitive). The servlet (servlet/EditEmployeeServlet.java:27) requires id, name, field and newValue to be present (not null).
**Edge cases handled:** The name the admin enters is required, but it is never compared to the stored name. The update matches on ID only; A negative salary (e.g. -500) is accepted on edit, even though Add rejects it; Department can be set to an empty or whitespace-only value. The form's 'required' check is only in the browser; newValue is not trimmed. A salary of ' 18000' still parses, because Java's Double.parseDouble trims whitespace; A non-numeric ID throws NumberFormatException in the servlet (unhandled, server error page)
**Suspected defect:** The rules for add and edit don't match: edit allows a negative salary and ignores the employee name entered as a confirmation, so an admin can edit the wrong employee if they mistype the ID. | Edit skips the 'salary cannot be negative' rule enforced on create, and the required employee name is never used to confirm the target record. | Edit bypasses the add-time salary rule: negative, NaN and Infinity salaries are accepted. The required 'name' field is collected (servlet/EditEmployeeServlet.java:23,27) but never used to confirm the employee identity, unlike delete which requires id+name match.
**Confidence:** Medium — Should an edit require the entered name to match the employee's name (as Delete does), and should a salary of zero or more be enforced on edit as well as on add? / Should salary edits apply the same non-negative (and any range) rules as creation, and should the employee name be checked to confirm the record being updated, as delete does?

### RULE-017: Admin login by matching ID and name (no password)
**Category:** Policy
**Priority:** P0
**Source:** `webapp/Login.jsp:76-94`
**Also cited:** dao/AdminDAO.java:31-44; webapp/Login.jsp:76-94
**Plain English:** An admin is logged in when the Admin ID and Admin Name they enter exactly match a stored admin record; no password is involved.
**Specification:**
  Given admin_data contains (101, "Priya")
  When  A user submits Login.jsp with id=101 and name="Priya"
  Then  The session attribute adminName is set to "Priya" and the user is redirected to Welcome.jsp; with name="priya" or id=102 the message 'Invalid ID or Name. Please try again.' is shown
**Parameters:** Match query: SELECT 1 FROM admin_data WHERE id = ? AND name = ? (dao/AdminDAO.java:31-44), exact case-sensitive name match; credential factors = ID + name only
**Edge cases handled:** Name is not trimmed during login while it was trimmed at sign-up, so ' Priya' fails; Non-numeric ID -> same generic invalid message; A duplicate path exists in servlet/LoginServlet.java:21-34 (same rule; throws instead of showing a message on a non-numeric ID; the form posts to Login.jsp, not to LoginServlet); Database error is treated as a failed login (AdminDAO returns false); No lockout or limit on attempts
**Suspected defect:** Authentication uses only an ID and a display name, both guessable, with no password or attempt limit. | Authentication relies solely on ID + name with no password or attempt limiting; names are easily guessable. Two divergent login implementations exist (Login.jsp scriptlet and LoginServlet). | No secret is checked, so the admin name works like a guessable password. Combined with open sign-up (see the sign-up rule), anyone can get admin access.
**Confidence:** High

### RULE-018: Employee operations are not restricted to logged-in admins
**Category:** Policy
**Priority:** P0
**Source:** `webapp/Welcome.jsp:52-64`
**Also cited:** webapp/Welcome.jsp:50-65; webapp/Welcome.jsp:52-64
**Plain English:** The intended policy is that only a logged-in admin manages employees, but no page or servlet checks the session, so add, edit, delete and list are reachable without logging in, and logout does not end the session.
**Specification:**
  Given A user who has never logged in (no adminName in session)
  When  They POST directly to /AddEmployeeServlet, /EditEmployeeServlet, /DeleteEmployeeServlet or GET /ShowEmployeeServlet
  Then  The operation executes exactly as for a logged-in admin (including salary changes and deletion)
**Parameters:** Session attribute 'adminName' is set on login (webapp/Login.jsp:85, servlet/LoginServlet.java:29) and only read for the greeting (webapp/Welcome.jsp:53). No filter, no web.xml security constraint, no session checks in any servlet. Logout is a form posting to index.jsp (webapp/Welcome.jsp:62-64) with no session invalidation.
**Edge cases handled:** Welcome.jsp renders for anonymous users with a blank name; After 'Logout', the session still holds adminName
**Suspected defect:** Missing authorization guard on all employee-data operations; logout does not invalidate the session. | The access check is missing: the app depends on users following the navigation from the login page. | No authorization is enforced on any employee operation, and logout does not invalidate the session (session.invalidate() is never called).
**Confidence:** Medium — Should every employee operation (add, edit, delete, list) require an authenticated admin session, and should logout invalidate the session? Is there any external control (e.g., reverse-proxy auth) that protects these URLs in production? / Confirm that every employee function (including viewing salaries) must require an authenticated admin session, and whether any admin can see or change any emplo…

### RULE-019: Record IDs are typed in by users even though the database can generate them
**Category:** Policy
**Priority:** P1
**Source:** `schema.sql:5-15`
**Also cited:** schema.sql:5-15
**Plain English:** Both the admin and employee tables define the ID as an auto-generated identity ('GENERATED BY DEFAULT'). The application always supplies the ID the user typed, so the generated sequence is never used or advanced. Admin name and employee name and department may be NULL at the database level.
**Specification:**
  Given The admin_data identity sequence is at 1 and users have created admins with IDs 1 and 2
  When  Any future insert omits the ID (for example a data migration or a new feature)
  Then  The database generates id=1, which collides with an existing row and fails with a unique violation (SQLState 23505)
**Parameters:** admin_data.id and employee_data.id: INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY. name, department and admin name are VARCHAR(255) and nullable. salary is DOUBLE PRECISION NOT NULL. Client-side ID minimum is 1 (addEmployee.jsp:20, signup.jsp:56, Login.jsp:68).
**Edge cases handled:** Names longer than 255 characters are rejected by the database, which surfaces as a generic error; The INT range caps IDs at 2,147,483,647, and larger values fail Integer.parseInt; The ID minimum of 1 is only enforced in the browser. ID 0 or negative IDs can be posted directly unless the server checks them
**Suspected defect:** Mixing user-supplied IDs with identity columns leaves the sequences out of step with the data.
**Confidence:** Medium — Should employee and admin IDs be assigned by the business (manually) or generated by the system, and should name and department be mandatory at the database level? / Are employee and admin IDs meant to be chosen by users (for example an HR payroll number), or should the system assign them? / Should employee and admin IDs be business-assigned (for example a payroll number) or system-generated?

### RULE-020: Second admin login endpoint with weaker input handling (LoginServlet)
**Category:** Policy
**Priority:** P1
**Source:** `servlet/LoginServlet.java:21-34`
**Also cited:** servlet/LoginServlet.java:21-34; servlet/LoginServlet.java:21-34
**Plain English:** A second login endpoint exists that also accepts an admin ID and name and starts an admin session when they match. Unlike the page-based login, it does not trim the ID or handle a non-numeric ID. No page in the app currently posts to it, but it can still be called directly.
**Specification:**
  Given Admin 101 'Root' exists
  When  A POST to /LoginServlet sends id=101, name='Root'
  Then  Session attribute adminName='Root' is set and the user is redirected to Welcome.jsp. A wrong pair shows 'Incorrect name or id' and returns to Login.jsp. id=' 101' or 'abc' causes an unhandled error (HTTP 500).
**Parameters:** Same credential rule as Login.jsp: ID plus exact name, no password (dao/AdminDAO.java:31-44). Login.jsp posts to itself (webapp/Login.jsp:66), not to LoginServlet.
**Edge cases handled:** A missing id parameter causes a NumberFormatException on null; Login.jsp trims the ID (Login.jsp:84) but LoginServlet does not, so the two paths accept different inputs
**Suspected defect:** There are two inconsistent implementations of the same authentication rule, and the unused one is still exposed. | Duplicate authentication path with different validation behaviour. Only one should survive the rewrite. | This duplicate authentication path is reachable even though no page links to it, and its edge-case behavior differs from Login.jsp. A rewrite should have exactly one login rule.
**Confidence:** Medium — Is /LoginServlet still an intended entry point, or should it be removed so that only one login rule exists? / Is /LoginServlet still used by any external client, or can it be retired in favour of the Login.jsp flow? / Is /LoginServlet still meant to be used (for example by another client), or can it be removed in favor of a single login flow?

### RULE-021: Unused employee identity check (validateEmployee) and admin lookup (getAdminById)
**Category:** Policy
**Priority:** P2
**Source:** `dao/EmployeeDAO.java:102-121`
**Plain English:** The code contains a check that an employee exists with a given ID and exact name, labelled 'Validate employee login', which suggests a planned employee self-service login. It is never called, and neither is the admin lookup by ID (dao/AdminDAO.java:46-65).
**Specification:**
  Given Employee 101 'Alice Smith' exists
  When  validateEmployee(101, 'Alice Smith') is called
  Then  It returns true. Any mismatch or database error returns false. No servlet or JSP calls it.
**Parameters:** Match rule: id = ? AND name = ? (exact, case-sensitive).
**Edge cases handled:** A DB error is treated as 'not valid' rather than surfaced
**Confidence:** Low — Was employee self-service login (by employee ID and name) ever a requirement, and should the new system carry it over or drop it?

### RULE-022: Unused employee self-login check (ID plus name)
**Category:** Policy
**Priority:** P2
**Source:** `dao/EmployeeDAO.java:108-121`
**Also cited:** dao/EmployeeDAO.java:108-121
**Plain English:** There is a check that would treat an employee as authenticated when an employee_data row matches both the ID and the exact name. No servlet or JSP calls it, so employee self-service login does not exist today.
**Specification:**
  Given Employee id=101, name='Ravi' exists
  When  validateEmployee(101, 'Ravi') is called
  Then  It returns true. validateEmployee(101, 'ravi') returns false because the match is case-sensitive. Any database error returns false.
**Parameters:** Lookup: SELECT 1 FROM employee_data WHERE id=? AND name=?. Similar dead code: AdminDAO.getAdminById (AdminDAO.java:47-65) is also never called.
**Edge cases handled:** Duplicate names under different IDs do not matter because the ID is the primary key; A database failure returns false (not authenticated) instead of an error
**Suspected defect:** Dead code. The comment claims 'employee login', but no executable path exercises it.
**Confidence:** Low — Was employee self-service login (by ID and name) ever a requirement, or is this leftover code that can be dropped in the rewrite? / Was employee self-service login ever a requirement, or can validateEmployee be dropped?

### RULE-023: Employee listing shows all records unfiltered
**Category:** Policy
**Priority:** P2
**Source:** `dao/EmployeeDAO.java:84-100`
**Also cited:** dao/EmployeeDAO.java:84-100
**Plain English:** The employee list shows every employee's ID, name, department, and salary, with no filtering, paging, or guaranteed order.
**Specification:**
  Given Three employees in employee_data
  When  Someone opens ShowEmployeeServlet
  Then  All three rows are listed with raw salary values (e.g. 15000.5), in database order
**Parameters:** Query: SELECT id, name, department, salary FROM employee_data (no ORDER BY, no WHERE); rendered in webapp/showEmployee.jsp:26-40
**Edge cases handled:** A database error returns null, which renders as an empty table; Salary is shown with Java double formatting, not currency formatting; Values are written without HTML escaping
**Suspected defect:** No ORDER BY, so ordering is undefined; DB errors are silently shown as an empty list.
**Confidence:** Medium — Should the employee list be sorted (e.g. by ID or name), and should salary visibility be limited to certain roles?

### RULE-024: Success confirmations are stored but never shown
**Category:** Policy
**Priority:** P2
**Source:** `webapp/Welcome.jsp:52-59`
**Plain English:** Edit and delete store a success message in the session, but the Welcome page never reads or clears it, so the confirmations are invisible and stay in the session.
**Specification:**
  Given An admin has just edited employee 101
  When  The browser is redirected to Welcome.jsp
  Then  Only 'Welcome, <adminName>!' and the action buttons appear. The session attribute 'message' = 'Employee details updated successfully!' is not shown and is not removed.
**Parameters:** Session attribute key 'message' (set at EditEmployeeServlet.java:33 and DeleteEmployeeServlet.java:30)
**Edge cases handled:** A message left in the session could be shown later if a future page starts displaying 'message'
**Suspected defect:** Dead confirmation side effect: users get no confirmation that a change was saved.
**Confidence:** High

### RULE-025: Employee list shows an empty table when the employee data cannot be read
**Category:** Policy
**Priority:** P2
**Source:** `webapp/showEmployee.jsp:26-40`
**Also cited:** webapp/showEmployee.jsp:26-40
**Plain English:** If loading the employee list fails, the DAO returns no list and the page shows an empty table with headers, so a database outage looks the same as having no employees.
**Specification:**
  Given The database is unreachable
  When  An admin opens Show List of Employees (ShowEmployeeServlet, servlet/ShowEmployeeServlet.java:23-30)
  Then  An empty table with ID/Name/Department/Salary headers is shown and no error message appears
**Parameters:** Salary is shown as the raw double (for example 50000.0 or 1.0E7) with no currency formatting or rounding.
**Edge cases handled:** Large salaries display in scientific notation (for example 1.0E7); NaN or negative salaries saved through edit display as-is
**Suspected defect:** A failed load cannot be told apart from an empty workforce.
**Confidence:** High

## Rules requiring SME confirmation

- **RULE-001** (Medium): What does salary represent (annual or monthly, which currency), and should it be stored as a fixed-point decimal rounded to 2 places with a defined maximum?
- **RULE-007** (Medium): Should salary edits follow the same rules as new employees (non-negative, at most 2 decimals), and should the employee name entered on the edit form have to match the stored name?
- **RULE-011** (Medium): Is there a canonical department list that values should be validated against?
- **RULE-014** (Medium): When a deletion matches no employee, should the user stay on the delete page with a clear 'not found' error? / Should a failed delete (no ID+name match) send the user back to the delete form with an error, or to the Welcome page?
- **RULE-016** (Medium): Should an edit require the entered name to match the employee's name (as Delete does), and should a salary of zero or more be enforced on edit as well as on add? / Should salary edits apply the same non-negative (and any range) rules as creation, and should the employee name be checked to confirm the record being updated, as delete does?
- **RULE-018** (Medium): Should every employee operation (add, edit, delete, list) require an authenticated admin session, and should logout invalidate the session? Is there any external control (e.g., reverse-proxy auth) that protects these URLs in production? / Confirm that every employee function (including viewing salaries) must require an authenticated admin session, and whether any admin can see or change any emplo…
- **RULE-019** (Medium): Should employee and admin IDs be assigned by the business (manually) or generated by the system, and should name and department be mandatory at the database level? / Are employee and admin IDs meant to be chosen by users (for example an HR payroll number), or should the system assign them? / Should employee and admin IDs be business-assigned (for example a payroll number) or system-generated?
- **RULE-020** (Medium): Is /LoginServlet still an intended entry point, or should it be removed so that only one login rule exists? / Is /LoginServlet still used by any external client, or can it be retired in favour of the Login.jsp flow? / Is /LoginServlet still meant to be used (for example by another client), or can it be removed in favor of a single login flow?
- **RULE-021** (Low): Was employee self-service login (by employee ID and name) ever a requirement, and should the new system carry it over or drop it?
- **RULE-022** (Low): Was employee self-service login (by ID and name) ever a requirement, or is this leftover code that can be dropped in the rewrite? / Was employee self-service login ever a requirement, or can validateEmployee be dropped?
- **RULE-023** (Medium): Should the employee list be sorted (e.g. by ID or name), and should salary visibility be limited to certain roles?

## Rules folded into another

These rules described the same behavior as another rule in a different place, so they were merged into it (the kept rule lists their locations under "Also cited"):

- Delete failure is reported as a silent return to the home page (servlet/DeleteEmployeeServlet.java:25-37) into Failed delete still sends the user to the home page as if it worked
- Delete outcome handling: success sets a confirmation message, but the not-found alert is overwritte… (servlet/DeleteEmployeeServlet.java:22-37) into Failed delete still sends the user to the home page as if it worked
- Employee removal requires matching ID and name (hard delete) (dao/EmployeeDAO.java:67-78) into Deleting an employee requires both ID and exact name to match
- Delete employee requires matching ID and name (dao/EmployeeDAO.java:67-78) into Deleting an employee requires both ID and exact name to match
- Employee update limited to salary or department (dao/EmployeeDAO.java:39-59) into Edit employee: only salary or department can change, and the employee is found by ID alone
- Only salary or department may be edited after creation (dao/EmployeeDAO.java:39-59) into Edit employee: only salary or department can change, and the employee is found by ID alone
- Record IDs are supplied by users, while the database identity sequence is never used (schema.sql:5-15) into Record IDs are typed in by users even though the database can generate them
- Record IDs are chosen by the user, not generated, even though the database has identity sequences (dao/EmployeeDAO.java:21-24; dao/AdminDAO.java:21-24 (ID taken from the request at servlet/AddEmploy…) into Record IDs are typed in by users even though the database can generate them
- Second admin login endpoint (LoginServlet) duplicates Login.jsp, with weaker input handling (servlet/LoginServlet.java:21-34) into Second admin login endpoint with weaker input handling (LoginServlet)
- Second admin login endpoint with weaker input handling (servlet/LoginServlet.java:21-34) into Second admin login endpoint with weaker input handling (LoginServlet)
- A database failure on the employee list looks the same as having no employees (webapp/showEmployee.jsp:26-40) into Employee list shows an empty table when the employee data cannot be read
- Admin login by ID and name match (dao/AdminDAO.java:31-44) into Admin login by matching ID and name (no password)
- Admin login checks ID and name only (no password) (webapp/Login.jsp:76-94) into Admin login by matching ID and name (no password)
- Employee list shows all employees unfiltered (dao/EmployeeDAO.java:84-100) into Employee listing shows all records unfiltered
- Employee management functions are not restricted to logged-in admins (webapp/Welcome.jsp:50-65) into Employee operations are not restricted to logged-in admins
- Admin session has no access guard and logout does not end the session (webapp/Welcome.jsp:52-64) into Employee operations are not restricted to logged-in admins
- Admin self-registration creates an admin account keyed by unique numeric ID (servlet/SignUpServlet.java:20-49) into Open admin self-registration with a unique numeric admin ID
- Open admin self-registration (servlet/SignUpServlet.java:20-49) into Open admin self-registration with a unique numeric admin ID
- Unused employee identity check (ID + name) suggests a planned employee login (dao/EmployeeDAO.java:108-121) into Unused employee self-login check (ID plus name)
- Add employee: all fields required, ID whole number, salary non-negative number (servlet/AddEmployeeServlet.java:24-58) into New employee record must pass completeness, ID and salary checks
- Edited salary skips the range checks applied when an employee is added (servlet/EditEmployeeServlet.java:27-29) into Salary edits skip the non-negative and format checks that apply when an employee is added
- Salary edit skips the non-negative and two-decimal checks (servlet/EditEmployeeServlet.java:20-41) into Salary edits skip the non-negative and format checks that apply when an employee is added
