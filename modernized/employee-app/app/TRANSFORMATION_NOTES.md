# Transformation Notes: employee-app (Phases 1–3, one build pass)

- **Date:** 2026-10-31
- **From:** Java 8 Servlets + JSP + JDBC on Tomcat 9 (`legacy/employee-app`, about 1,100 lines across 21 modules)
- **To:** Spring Boot 3.4.13 REST API on Java 21 (`backend/`, 529 lines) and a React 18.3.1 SPA (`frontend/`, 548 lines), PostgreSQL schema unchanged
- **Scope:** every module in `MODERNIZATION_BRIEF.md` Phases 1–3, built in one pass at the approver's request ("create entire app in one go", 2026-10-31). The one exception is `schema.sql`, which is off limits and is used as-is.
- **Contract:** `API_CONTRACT.md` is the single specification. The tests and the implementation were written independently from it.
- **Per-module notes:** in `module-notes/<module>.md`. They were moved there from `modernized/employee-app/<module>/` on 2026-10-31 (the user's decision), so that the verifier judges the one folder that holds code.
- **Legacy source for verification:** `legacy/employee-app` is a copy of the 29 legacy source files with their original timestamps. It replaced the symlink to the repository root on 2026-10-31 (the user's decision).

## Security hardening (2026-10-31, after verification)

At the user's request ("apply the patch and port the fixes to the modernized app"), `analysis/employee-app/security_remediation.patch` was applied to `legacy/employee-app`, and the same fixes were ported here. Finding ids are those of `analysis/employee-app/SECURITY_FINDINGS.md`. This **replaces** behaviour the reviewer had confirmed for parity: RULE-018 (no login), RULE-009 (open sign-up) and RULE-015 (logout kept the session). RULE-017 (ID + name, no password) stays, because a password needs a schema change.

| Finding | Legacy fix (patch) | Ported to |
|---|---|---|
| SEC-001, 004, 005, 006, 010, 013: no login check | `AuthFilter` | `admin/AdminSessionInterceptor.java` (401 on `/api/employees/**`) and `frontend/src/components/RequireAdmin.jsx` (admin pages send a visitor to /login) |
| SEC-007: open admin sign-up | `AuthFilter` + `AdminDAO.hasAnyAdmin()` | `AuthService.signUp` + `AdminRepository.anyExists()` (403 once an admin exists, unless the caller is logged in). Switch: `app.security.open-signup=true` (env `APP_SECURITY_OPEN_SIGNUP`), default false, reopens it for demos; pinned by `OpenSignUpTest` |
| SEC-009: no brute-force limit | `util/LoginThrottle` | `admin/LoginThrottle.java` (same rules; 429) |
| SEC-012: session fixation | `changeSessionId()` in Login.jsp, LoginServlet | `AuthController.logIn` |
| SEC-014 (partial): CSRF | `SameSite=Lax` in META-INF/context.xml | `server.servlet.session.cookie.same-site: lax` in `application.yml` |
| SEC-020: logout kept the session | `LogoutServlet` | `POST /api/auth/logout`; the dashboard's Logout calls it |
| SEC-002, 003, 018: stored XSS | `<c:out>` in showEmployee.jsp | Nothing needed: React escapes all text (test "shows an HTML-like name as plain text") |

Tests (all green): backend **264 / 264** (with the open-signup switch), front end **23 / 23**.
- The request helpers in `CharacterizationTestBase` now send a logged-in admin's session.
- `EmployeeOperationsWithoutLoginCharacterizationTest` now pins the 401s.
- New: `SecurityHardeningTest` (sign-up gate, lockout, logout), `LoginThrottleTest` (50 parallel attempts → exactly 5 pass) and `SessionCookieTest` (real Tomcat: `SameSite=Lax; HttpOnly`).

Canary: login check disabled in `AdminSessionInterceptor` → 6 of 16 tests in the two security classes failed.

End-to-end run of the rebuilt jar: an anonymous list → 401; the first sign-up → 201, the next → 403; login → 200 with `HttpOnly; SameSite=Lax`; after logout → 401; the 6th failed login → 429, and the lock was logged.

**The equivalence evidence below predates this change.** `EQUIVALENCE.json` and `VERIFICATION.md` were recorded against the unpatched legacy app, and the development cases send employee operations without logging in. A re-run of `/code-modernization:modernize-verify employee-app` will report those cases as differences until the harness logs in first.

## Evidence

| Proof | Result |
|---|---|
| Backend characterization tests (JUnit 5 + MockMvc against `employee_test`; written first by the test-engineer agent from the legacy source) | **248 / 248 pass, 0 skipped.** `equivalence cases executed: 248` |
| Front-end behaviour tests (Vitest + Testing Library) | **17 / 17 pass** |
| Side-by-side run: legacy WAR on a separate Tomcat 9 / JDK 8 (port 8090) vs the new app (8091), both on `employee_equiv` | **48 cases: 31 same, 17 differences approved by the user, 0 unapproved → PROVEN** (`analysis/employee-app/EQUIVALENCE.json`). `compare.py` self-check passed. |
| Canary | `Canary: salary text switched to String.format("%.2f") and edit made to reject negative salaries → 18 tests failed` (restored; 248 pass again) |
| Single deployable | `mvn -Pfrontend package` builds a jar holding the React build. Smoke run: `/`, `/welcome` and `/employees/add` → index.html (200); `/api/employees` and `/api/auth/me` → JSON (200) |
| Characterization tests' own checks (test-engineer, against its own throwaway implementation in the scratchpad) | Each of 10 mutations caught by at least one test. Pointing the suite at a missing database fails it rather than skipping. It refuses to run against a database whose name doesn't end in `_test`. |

The legacy app **was executed here**: equivalence is dual-run, not trace-based.

## Mapping (legacy → target, per behaviour)

| Behaviour (rules) | Legacy | Target |
|---|---|---|
| List all employees, salary text (RULE-002, 023) | `servlet/ShowEmployeeServlet.java:23-30`, `dao/EmployeeDAO.java:84-100`, `webapp/showEmployee.jsp:19-41` | `backend/.../employee/EmployeeController.java:32-35`, `EmployeeService.java:35-37`, `EmployeeRepository.java:22-27`, `EmployeeView.java`; `frontend/src/pages/EmployeeListPage.jsx` |
| Add-employee validation and insert (RULE-003, 004, 005, 010) | `servlet/AddEmployeeServlet.java:22-73`, `dao/EmployeeDAO.java:20-30`, `webapp/addEmployee.jsp:18-32` | `EmployeeService.java:40-64`, `EmployeeRepository.java:29-33`, `EmployeeController.java:37-42`; `AddEmployeePage.jsx` |
| Edit salary or department by id (RULE-006, 007, 016) | `servlet/EditEmployeeServlet.java:20-41`, `dao/EmployeeDAO.java:39-59`, `webapp/editEmployee.jsp:18-35` | `EmployeeService.java:67-86`, `EmployeeRepository.java:36-42`, `EmployeeController.java:45-49`; `EditEmployeePage.jsx` |
| Delete by exact id + name (RULE-013, 014) | `servlet/DeleteEmployeeServlet.java:20-38`, `dao/EmployeeDAO.java:67-78`, `webapp/deleteEmployee.jsp:18-26` | `EmployeeService.java:89-96`, `EmployeeRepository.java:45-47`, `EmployeeController.java:51-55`; `DeleteEmployeePage.jsx` |
| Admin sign-up (RULE-008, 009, 012) | `servlet/SignUpServlet.java:20-49`, `dao/AdminDAO.java:20-28`, `webapp/signup.jsp:53-61` | `admin/AuthService.java:23-45`, `AdminRepository.java:16-18`, `AuthController.java:40-45`; `SignUpPage.jsx` |
| Admin login, ID + name, no password (RULE-017, 008) | `webapp/Login.jsp:66-94`, `dao/AdminDAO.java:31-44` | `AuthService.java:49-62`, `AdminRepository.java:21-27`, `AuthController.java:47-52`; `LoginPage.jsx` |
| Dashboard greeting, session read, fake logout (RULE-015, 018) | `webapp/Welcome.jsp:52-65` | `AuthController.java:54-58` (`/api/auth/me`); `DashboardPage.jsx` |
| Home page | `webapp/index.jsp:74-83` | `HomePage.jsx` |
| Connection pool and config | `util/DBUtil.java:13-28`, `webapp/META-INF/context.xml:6-16` | `backend/src/main/resources/application.yml` (HikariCP, same pool sizing, `SPRING_DATASOURCE_*` from the environment) |
| Models | `model/Employee.java`, `model/Admin.java` | `record Employee`, `record Admin` |
| Error presentation (`<script>alert()</script>`, Tomcat 500 pages, swallowed exceptions) | all servlets; `catch (Exception) { printStackTrace(); return false; }` in both DAOs | `web/ApiException.java`, `web/ApiExceptionHandler.java`, `frontend/src/api.js`, `components/Notices.jsx` (inline alerts and toasts) |
| Styling | `webapp/styles.css` + inline `<style>` in 4 JSPs | `frontend/src/styles.css` (new modern design system, light and dark) |

## Side-by-side: add-employee validation (RULE-003/004)

```
legacy servlet/AddEmployeeServlet.java:29-58                 | target backend/.../employee/EmployeeService.java:40-63
if (isBlank(name) || isBlank(id) || ... ) {                  | if (isBlank(request.id()) || isBlank(request.name()) || ... ) {
    showAlert(response, "Please fill in all fields.");       |     throw ApiException.badRequest("Please fill in all fields.");
employeeId = Integer.parseInt(id.trim());  (catch → alert)   | int id = parseId(request.id().trim());
employeeSalary = Double.parseDouble(salary.trim()); (catch)  | double salary = parseSalary(request.salary().trim());
if (employeeSalary < 0) showAlert("Salary cannot be negative.") | if (salary < 0) { // NaN passes, as in legacy
employee.setName(name.trim()); ...setDepartment(... .trim()) | String name = request.name().trim(); ... department.trim()
                                                             | (new) length check ≤ 255 → message   [approved deviation]
EmployeeDAO.addEmployee → SQLState 23505 → duplicate alert   | catch (DuplicateKeyException) → 409 same message
```

The checks, their order, the parsing functions and every message text are the legacy ones. The new structure is a service method that throws typed errors, which a single handler turns into JSON.

## Deliberate deviations

Approved by the user (2026-10-31, *"Please give appropriet messages in case of error. Also, create a modern asthetic UI in react"*). Marked `approvedDifference` in `cases.json`:
- **RULE-025 (L06):** an unreadable employee table shows an error instead of an empty list. The messages are "We couldn't reach the database. Please try again later." (connection, 503) or "Something went wrong. Please try again." (other, 500).
- **RULE-014 (D02, D03):** a failed delete shows "Employee not found!" and stays on the form. The legacy app lost this alert to the redirect; the dual run confirmed it.
- **Non-numeric ID on edit or delete (E09, D04):** the legacy app showed Tomcat's HTTP 500 page; the new app shows "Employee ID must be a whole number, e.g. 101."
- **Edit errors split (E06, E07, E08):** "Field must be either salary or department.", "Salary must be a number, e.g. 15000." and "Employee not found!" replace the single legacy "Employee not found or invalid data!".
- **Text over 255 characters (A13):** a length message replaces the generic "Error while adding employee".
- **Modern UI:** a new visual design. Parity is proven on behaviour, text, navigation and data.
- **Escaping (brief Q4):** React escapes names, so a name containing HTML is shown as text.

**Approved by the user on 2026-10-31 ("approve the differences"):**
- **Success messages after edit and delete (E01–E05, E11, D01):** the app shows "Employee details updated successfully!" / "Employee deleted successfully!" as a toast. The legacy app stored these in the session but never displayed them (RULE-024).
- **Anonymous dashboard greeting (G06):** "Welcome!" instead of the legacy "Welcome, !".
- **Empty list:** shows "No employees yet." instead of bare headers. This is not covered by a dual-run case, because the harness compares rows, not empty-state text.

## Not migrated, and why

- `servlet/LoginServlet.java`: unreachable from the UI. Dropped per brief Q7 (RULE-020).
- `EmployeeDAO.validateEmployee`, `AdminDAO.getAdminById`, `AdminDAO.addAdmin`: dead code (RULE-021/022).
- `build.xml` (Ant): replaced by Maven (`backend/pom.xml`, `-Pfrontend` profile).
- `schema.sql`: unchanged by design; the tests and the dual run build their databases from it.

## Behaviour kept on purpose (confirmed by the reviewer)

- Login is ID + name with no password (RULE-017). SEC-008/011 stay open because the schema is off limits.
- ~~No endpoint requires a login (RULE-018).~~ Replaced by the security hardening above.
- ~~Logout does not end the session (RULE-015).~~ Replaced by the security hardening above.
- Edit finds the employee by ID only, ignores the name and accepts negative/NaN/Infinity salaries (RULE-016).
- Salary is DOUBLE and shown with Java's `Double.toString` (RULE-001/002).
- IDs are typed in by users (RULE-019).

## How to run

```bash
# API + built UI as one jar (port 8091; 8080 is the legacy Tomcat)
cd backend && mvn -Pfrontend package && java -jar target/employee-app-1.0.0.jar
# or develop the UI with hot reload against the API
cd backend && mvn spring-boot:run      # API on :8091
cd frontend && npm run dev             # UI on :5173, /api proxied to :8091
# tests
cd backend && mvn test                 # needs the employee_test database (schema.sql loaded)
cd frontend && npm test
# side-by-side proof (from the workspace root)
python3 analysis/employee-app/equivalence/run_equivalence.py
python3 <plugin>/scripts/compare.py analysis/employee-app/equivalence/cases.json --out analysis/employee-app/EQUIVALENCE.json
```

Database settings come from `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`; no credential is stored in the repository.

## Follow-ups

- `/code-modernization:modernize-verify employee-app` for the independent re-check.
- Re-run `/code-modernization:modernize-verify employee-app` after the hardening. The equivalence harness needs a login step on both sides first.
- SEC-008/011 (no password): choose between a schema change and a credential store outside the database.
- Optional: a CSRF token on top of SameSite (SEC-014), and the Medium/Low findings in SECURITY_FINDINGS.md.
- Spring Boot 3.4 is past open-source support, and React 19 is current (brief Q3). An upgrade is a follow-up.

## Architecture review

The architecture-critic agent's verdict was "a clean, idiomatic, appropriately small rewrite… no legacy structure carried over into the Java". Its findings, with what was done about each:

**Applied** (all tests re-run afterwards: backend 248/248, frontend 17/17, side-by-side unchanged at 31 same and 17 approved differences, 0 unapproved):
- **HIGH:** non-connection database errors on add and sign-up were turned into a 500 with the cause dropped and nothing logged. `ApiException` now carries the cause, and every 5xx is logged with it.
- **MEDIUM:**
  - Spring MVC's own errors (unknown `/api/...`, 405, 415) and unexpected exceptions now return `{"message": ...}`, through a catch-all handler. Verified: `/api/nope` → 404 "That page or action does not exist."; form-encoded POST → 415 and PUT → 405, both "Invalid data! Please try again."
  - Database timeouts were added: PostgreSQL `connectTimeout=5`, `socketTimeout=30`. The browser `fetch` gets a 15 s timeout with its own message.
  - The UI never shows raw exception text: anything that is not an API message shows the generic text, and a 502/503/504 without a JSON body shows the connection message.
  - Legacy page URLs (`/index.jsp`, `/Login.jsp`, `/signup.jsp`, `/Welcome.jsp`, `/ShowEmployeeServlet`, the three CRUD JSPs) redirect permanently (301) to the new routes (`web/LegacyUrlRedirects.java`).
  - Dark-mode buttons and the error alert now meet WCAG AA contrast.
- **LOW:**
  - The Hikari `minimum-idle` that was wrongly mapped from DBCP `maxIdle` was removed.
  - A solid fallback background and the `-webkit-backdrop-filter` prefix were added for browsers without `color-mix`.

**Not applied** (follow-ups):
- MEDIUM: Spring Boot Actuator `/actuator/health`.
- LOW:
  - widen the 503 mapping to `TransientDataAccessException`
  - typed nulls in `JdbcClient` params
  - pin Node with `frontend-maven-plugin` and run Vitest in the Maven build
  - cache headers for `index.html` and `/assets`
  - `AbortController` cleanup in the effects and a retry button on the list
  - toast live-region roles
  - `aria-describedby` on form errors and `:focus-visible`
  - React Router v7 future flags and ESLint `react-hooks`
  - Testcontainers for CI
  - Spring Boot 3.4 support window
- **Security** (RULE-017/018 confirmed by the owner; left for `harden`): `SameSite`/`Secure` session cookie, session rotation, a real logout, CSRF.
