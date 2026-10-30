<%@ page import="com.employee.dao.AdminDAO" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login Page</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background: linear-gradient(135deg, #74EBD5, #9FACE6);
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
            margin: 0;
        }

        .container {
            background: rgba(255, 255, 255, 0.2);
            padding: 20px;
            border-radius: 10px;
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
            backdrop-filter: blur(10px);
            width: 400px;
        }

        h2 {
            text-align: center;
            color: #fff;
        }

        label {
            color: #fff;
            font-weight: bold;
        }

        input[type="text"], input[type="number"], input[type="submit"] {
            width: 100%;
            padding: 10px;
            margin: 10px 0;
            border-radius: 5px;
            border: none;
        }

        input[type="submit"] {
            background-color: #1E90FF;
            color: #fff;
            font-weight: bold;
            cursor: pointer;
        }

        input[type="submit"]:hover {
            background-color: #63B3FF;
        }

        .error {
            color: #FF6B6B;
            text-align: center;
        }
    </style>
</head>
<body>
    <div class="container">
        <h2>Admin Login</h2>
        <form method="post" action="Login.jsp">
            <label for="id">Admin ID:</label>
            <input type="number" name="id" min="1" step="1" required>

            <label for="name">Admin Name:</label>
            <input type="text" name="name" required>

            <input type="submit" value="Login">
        </form>

        <%
            // Authenticate via AdminDAO (uses the Tomcat connection pool)
            String id = request.getParameter("id");
            String name = request.getParameter("name");

            if (id != null && name != null) {
                try {
                    // Check if admin exists
                    if (new AdminDAO().validateAdmin(Integer.parseInt(id.trim()), name)) {
                        session.setAttribute("adminName", name);
                        response.sendRedirect("Welcome.jsp");
                    } else {
                        out.println("<p class='error'>Invalid ID or Name. Please try again.</p>");
                    }
                } catch (NumberFormatException e) {
                    out.println("<p class='error'>Invalid ID or Name. Please try again.</p>");
                }
            }
        %>
    </div>
</body>
</html>
