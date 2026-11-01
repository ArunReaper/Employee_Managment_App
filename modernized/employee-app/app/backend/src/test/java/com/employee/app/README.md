# employee-app characterization tests

These tests pin what the legacy Servlet/JSP app actually did (the oracle). The new Spring Boot API must reproduce it,
except where `API_CONTRACT.md` marks a **[deviation]** (approved 2026-10-31). Every test names the `RULE-NNN` it pins
(see `analysis/employee-app/BUSINESS_RULES.md`) in its method name or in a comment just above it.

| Class | Legacy source | Endpoint |
|---|---|---|
| `employee.EmployeeListCharacterizationTest` | ShowEmployeeServlet + showEmployee.jsp | `GET /api/employees` |
| `employee.AddEmployeeCharacterizationTest` | AddEmployeeServlet | `POST /api/employees` |
| `employee.EditEmployeeCharacterizationTest` | EditEmployeeServlet + EmployeeDAO.updateEmployee | `PATCH /api/employees/{id}` |
| `employee.DeleteEmployeeCharacterizationTest` | DeleteEmployeeServlet + EmployeeDAO.deleteEmployee | `DELETE /api/employees/{id}?name=` |
| `employee.EmployeeOperationsWithoutLoginCharacterizationTest` | Welcome.jsp (no session check anywhere) | all employee endpoints |
| `admin.SignUpCharacterizationTest` | SignUpServlet | `POST /api/auth/signup` |
| `admin.LoginSessionCharacterizationTest` | Login.jsp scriptlet, Welcome.jsp | `POST /api/auth/login`, `GET /api/auth/me` |
| `DatabaseUnavailableTest` | (deviation: errors are reported) | all, with the repositories mocked |

## Running

1. Create the test database from the unchanged legacy `schema.sql`:
   ```
   createdb employee_test
   psql -d employee_test -f schema.sql
   ```
2. Credentials come only from the environment (see `src/test/resources/application-test.yml`):
   `TEST_DATASOURCE_URL` (default `jdbc:postgresql://localhost:5432/employee_test`), `TEST_DATASOURCE_USERNAME`
   (default `employee_app`), `TEST_DATASOURCE_PASSWORD` (default empty). Never put a password in a test file.
3. `mvn test` from `backend/`.

The DB-backed tests **TRUNCATE `employee_data` and `admin_data` before every test**. As a safety guard, the base class
fails unless `current_database()` ends in `_test`.

## Failing, never skipping

There is no `assumeTrue` or `@Disabled`. If PostgreSQL is down or the tables are missing, every DB-backed test fails.
After each class the `ExecutedCaseCounter` extension prints a running total,
`equivalence cases executed: N (passed: P)`. A run that executes 0 cases proves nothing.

## Adding a case

1. Find the legacy branch in the servlet/DAO/JSP and work out the expected value from the code itself
   (for example `Double.toString`, `Integer.parseInt`, or the order of the checks). Do not rely on comments.
2. Add a `@Test`, or a row to an existing `@MethodSource` / `@ValueSource`, in the class for that endpoint.
   Name it `ruleNNN_whatItPins` or put `// RULE-NNN` on the line above it.
3. Seed rows with plain SQL (`seedEmployee`, `seedEmployeeWithSalaryLiteral`, `seedAdmin`), then assert both the
   response and the final table state with plain SQL (`employeeRow`, `assertStoredSalary`, `adminRows`, ...).
4. Send JSON bodies with **string** values, as the contract requires (`json("id", "101", ...)`).
5. If the contract deliberately differs from legacy, test the contract and add the comment
   `deviation approved 2026-10-31`.
6. To show the new case can fail, break the implementation in a small way (for example a flipped comparison),
   watch the case go red, then restore the code.
