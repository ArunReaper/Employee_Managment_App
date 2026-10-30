# Employee Management App

A full-stack Java EE web application for managing employee records, built with 
JDBC, Java Servlets, JSP, and Apache Ant. Supports complete CRUD operations 
through a clean web interface with admin authentication.

## 🚀 Features
- Admin Sign Up & Login with session management
- Add, View, Edit, and Delete employee records
- Plain JDBC with `PreparedStatement` for database access
- Tomcat-managed connection pool (JNDI DataSource)
- DAO pattern for clean separation of data access logic
- JSP-based frontend with form validation

## 🛠️ Tech Stack
| Layer | Technology |
|-------|------------|
| Language | Java 8 |
| Web Framework | Java Servlets 4.0 (javax) + JSP |
| Data Access | JDBC + Tomcat connection pool |
| Build Tool | Apache Ant |
| Server | Apache Tomcat 9 |
| Database | PostgreSQL |
| Architecture | MVC + DAO Pattern |

## 📁 Project Structure
```
EmployeeManagementApp/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/employee/
│       │       ├── dao/
│       │       │   ├── AdminDAO.java
│       │       │   └── EmployeeDAO.java
│       │       ├── model/
│       │       │   ├── Admin.java
│       │       │   └── Employee.java
│       │       ├── servlet/
│       │       │   ├── AddEmployeeServlet.java
│       │       │   ├── DeleteEmployeeServlet.java
│       │       │   ├── EditEmployeeServlet.java
│       │       │   └── ShowEmployeeServlet.java
│       │       └── util/
│       │           └── DBUtil.java
│       └── webapp/
│           ├── index.jsp
│           ├── Login.jsp
│           ├── signup.jsp
│           ├── Welcome.jsp
│           ├── addEmployee.jsp
│           ├── showEmployee.jsp
│           ├── deleteEmployee.jsp
│           └── editEmployee.jsp
├── lib/provided/          # servlet-api + PostgreSQL driver (supplied by Tomcat at runtime)
└── build.xml
```

## ⚙️ Setup & Installation

### Prerequisites
- Java 8 (JDK 1.8)
- Apache Tomcat 9 (Tomcat 10+ requires Java 11 and the jakarta namespace)
- PostgreSQL
- Apache Ant 1.10+

### Steps

1. **Clone the repository**
```bash
git clone https://github.com/your-username/employee-management-app.git
cd employee-management-app
```

2. **Create the database** — a login role, the `employee` database, and its tables:
```bash
psql -d postgres -c "CREATE ROLE employee_app LOGIN"
psql -d postgres -c "CREATE DATABASE employee OWNER employee_app"
psql -h localhost -U employee_app -d employee -f schema.sql
```

3. **Configure the connection pool** — update the `<Resource>` in `webapp/META-INF/context.xml` with your DB credentials:
```xml
url="jdbc:postgresql://localhost:5432/employee"
username="employee_app"
password="your_password"
```
   Copy the PostgreSQL driver jar (`lib/provided/postgresql-42.7.4.jar`) into Tomcat's `lib/` folder — the pool is created by Tomcat, so the driver must be on Tomcat's classpath.

4. **Build the project**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 1.8) ant clean war
```

5. **Deploy** the generated `dist/EmployeeManagementApp.war` to Tomcat's `webapps/` directory

6. **Access the app** at:http://localhost:8080/EmployeeManagementApp/

## 📋 How It Works

1. Admin registers via the Sign Up page
2. Admin logs in — credentials are validated against the database with a SQL query
3. From the dashboard, the admin can:
   - **Add Employee** — enter ID, name, department, salary
   - **Show Employees** — view all employees in a table
   - **Delete Employee** — remove by ID and name
   - **Edit Employee** — update salary or department dynamically
4. All data operations go through the DAO layer using JDBC connections from the Tomcat pool

## 🗃️ Key DAO Methods

**AdminDAO**
- `addAdmin(Admin)` — persist new admin
- `validateAdmin(int id, String name)` — login check via SQL
- `getAdminById(int id)` — fetch admin by ID

**EmployeeDAO**
- `addEmployee(Employee)` — save new employee
- `updateEmployee(int id, String name, String field, String newValue)` — SQL update
- `deleteEmployee(int id, String name)` — SQL delete query
- `getEmployeeById(int id)` — fetch single employee
- `getAllEmployees()` — fetch full employee list

| Page | Description |
|------|-------------|
| Home | Landing page with Sign Up / Login |
| Sign Up | Admin registration form |
| Login | Admin authentication |
| Dashboard | Action menu — Add, Delete, Show, Edit |
| Add Employee | Form to add new employee record |
| Employee List | Table view of all employees |
| Delete Employee | Remove employee by ID and name |
| Edit Employee | Update department or salary |

