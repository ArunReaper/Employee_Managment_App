# Screenshots — legacy vs modernized

Captured 2026-10-31 with headless Chrome (Playwright), 1280×800, full page, logged in as admin 1 / Arun.
Live `employee` database; no records were created or changed (forms were opened, not submitted).

| # | Page | Legacy (JSP, Tomcat 9 :8080) | Modernized (React 18 + Spring Boot 3.4 :8091) |
|---|---|---|---|
| 01 | Home | `index.jsp` | `/` |
| 02 | Sign up | `signup.jsp` | `/signup` |
| 03 | Login | `Login.jsp` | `/login` |
| 04 | Welcome / dashboard | `Welcome.jsp` | `/welcome` |
| 05 | Employee list | `ShowEmployeeServlet` | `/employees` |
| 06 | Add employee | `addEmployee.jsp` | `/employees/add` |
| 07 | Edit employee | `editEmployee.jsp` | `/employees/edit` |
| 08 | Delete employee | `deleteEmployee.jsp` | `/employees/delete` |

Matching file names in `legacy/` and `modernized/` show the same page.
Re-capture with both apps running: `npm i playwright-core && node capture.js "$PWD"`.
