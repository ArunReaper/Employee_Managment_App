#!/usr/bin/env python3
"""Side-by-side equivalence run: legacy WAR (Tomcat 9, JDK 8) vs the new Spring Boot app.

Both apps run on this machine against the scratch database employee_equiv, never the real
`employee` database and never the Tomcat on :8080. For every case the database is reset and
seeded identically, the same user action is performed on each app, and a canonical outcome is
written to legacy/<case>.txt and new/<case>.txt:

    message: what the user is shown (alert / inline error / toast), or (none)
    page:    where the user ends up (welcome, login, signup, addEmployee, ..., error-500)
    rows:    for list cases, the table cells (sorted by id: RULE-023 has no guaranteed order)
    db:      admin_data and employee_data after the action

For the new app, "page" follows the React navigation (each page navigates to /welcome on success
and stays on error), which the Vitest suite pins. compare.py then decides same or different.

Usage (from the workspace root):
    python3 analysis/employee-app/equivalence/run_equivalence.py
"""
import http.client, http.cookies, json, os, re, shutil, subprocess, sys, tempfile, time, urllib.parse
from pathlib import Path

ROOT = Path.cwd()
EQ = ROOT / "analysis/employee-app/equivalence"
LEGACY_SRC = ROOT / "legacy/employee-app"
BACKEND = ROOT / "modernized/employee-app/app/backend"
JDK8 = "/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home"
JDK21 = "/Users/arun/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home"
TOMCAT_HOME = Path("/opt/homebrew/opt/tomcat@9/libexec")
DB = "employee_equiv"
LEGACY_PORT, NEW_PORT, SHUTDOWN_PORT = 8090, 8091, 8095
CTX = "/EmployeeManagementApp"

DB_DOWN = "We couldn't reach the database. Please try again later."
USER_REQUEST = ('Approved by the user on 2026-10-31: "Please give appropriet messages in case of error" '
                '(MODERNIZATION_BRIEF.md, approver revision item 2).')


# ---------------------------------------------------------------- database
def psql(sql):
    out = subprocess.run(["psql", "-h", "localhost", "-U", "employee_app", "-d", DB, "-v", "ON_ERROR_STOP=1",
                          "-AtqF", "|", "-c", sql], capture_output=True, text=True)
    if out.returncode:
        raise RuntimeError(out.stderr)
    return out.stdout


def reset(seed=""):
    psql("DROP TABLE IF EXISTS employee_data_offline; TRUNCATE employee_data, admin_data;" + seed)


def db_state():
    a = psql("SELECT id, coalesce(name, '<NULL>') FROM admin_data ORDER BY id").strip()
    e = psql("SELECT id, coalesce(name, '<NULL>'), coalesce(department, '<NULL>'), salary::text "
             "FROM employee_data ORDER BY id").strip()
    return f"admin_data:\n{a or '(empty)'}\nemployee_data:\n{e or '(empty)'}"


def take_table_offline():  # simulates the employee data being unreadable
    psql("ALTER TABLE employee_data RENAME TO employee_data_offline")


def restore_table():
    psql("ALTER TABLE IF EXISTS employee_data_offline RENAME TO employee_data")


# ---------------------------------------------------------------- http
class Client:
    """Minimal HTTP client that never follows redirects and keeps one cookie jar."""

    def __init__(self, port):
        self.port, self.cookies = port, {}

    def call(self, method, path, form=None, body=None):
        conn = http.client.HTTPConnection("localhost", self.port, timeout=20)
        headers = {}
        data = None
        if form is not None:
            data = urllib.parse.urlencode(form)
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        if body is not None:
            data = json.dumps(body)
            headers["Content-Type"] = "application/json"
        if self.cookies:
            headers["Cookie"] = "; ".join(f"{k}={v}" for k, v in self.cookies.items())
        conn.request(method, path, body=data, headers=headers)
        r = conn.getresponse()
        text = r.read().decode("utf-8", "replace")
        for h in r.headers.get_all("Set-Cookie") or []:
            c = http.cookies.SimpleCookie(h)
            for k, m in c.items():
                self.cookies[k] = m.value
        conn.close()
        return r.status, r.headers.get("Location", ""), text


def page_of(target):
    name = target.split("/")[-1].split("?")[0].split(";")[0]
    return {"Welcome.jsp": "welcome", "Login.jsp": "login", "signup.jsp": "signup", "index.jsp": "home",
            "addEmployee.jsp": "addEmployee", "editEmployee.jsp": "editEmployee",
            "deleteEmployee.jsp": "deleteEmployee", "ShowEmployeeServlet": "list"}.get(name, name)


# ---------------------------------------------------------------- legacy side
def legacy_outcome(status, location, html, stay_page):
    if status in (301, 302, 303):
        return "(none)", page_of(location)
    if status >= 500:
        return "(server error page)", "error-500"
    m = re.search(r"alert\('(.*?)'\);\s*window\.location(?:\.href)?\s*=\s*'([^']+)'", html)
    if m:
        return m.group(1), page_of(m.group(2))
    m = re.search(r"<p class='error'>(.*?)</p>", html)
    if m:
        return m.group(1), stay_page
    return "(none)", stay_page


def legacy_rows(html):
    rows = re.findall(r"<tr>\s*<td>(.*?)</td>\s*<td>(.*?)</td>\s*<td>(.*?)</td>\s*<td>(.*?)</td>\s*</tr>", html, re.S)
    return [tuple(c.strip() for c in r) for r in rows]


def legacy_greeting(html):
    m = re.search(r"<h1>(.*?)</h1>", html, re.S)
    return m.group(1).strip() if m else "(no heading)"


# ---------------------------------------------------------------- new side (React navigation rules)
SUCCESS_PAGE = {"add": "welcome", "edit": "welcome", "delete": "welcome", "signup": "login", "login": "welcome"}
STAY_PAGE = {"add": "addEmployee", "edit": "editEmployee", "delete": "deleteEmployee", "signup": "signup", "login": "login"}


def new_outcome(action, status, text):
    body = json.loads(text) if text.strip() else {}
    if status < 300:
        # React shows the API message as a toast on success; login shows the dashboard greeting instead
        msg = "(none)" if action == "login" else body.get("message", "(none)")
        return msg, SUCCESS_PAGE[action]
    return body.get("message", "(no message)"), STAY_PAGE[action]


def new_greeting(me):
    name = json.loads(me)["adminName"]
    return f"Welcome, {name}!" if name else "Welcome!"   # DashboardPage.jsx


# ---------------------------------------------------------------- cases
# Each case: id, title, seed SQL, and the action (kind + inputs). "approved": a difference the user approved.
E1 = "INSERT INTO employee_data VALUES (101,'Asha','Finance',15000),(102,'Bob','HR',15000.5);"
A1 = "INSERT INTO admin_data VALUES (7,'Ravi');"
LONG = "N" * 300
CASES = [
    # list (ShowEmployeeServlet / GET /api/employees)
    ("L01", "List: empty table", "", ("list",)),
    ("L02", "List: normal rows, no login (RULE-018, RULE-023)", E1, ("list",)),
    ("L03", "List: salary text formats (RULE-002)",
     "INSERT INTO employee_data VALUES (1,'a','d',10000000),(2,'b','d',15000.005),(3,'c','d',-500),(4,'e','d',0),"
     "(5,'f','d',0.001),(6,'g','d',0.0001),(7,'h','d','NaN'),(8,'i','d','Infinity'),(9,'j','d','-Infinity');", ("list",)),
    ("L04", "List: NULL name and department", "INSERT INTO employee_data VALUES (1,NULL,NULL,5);", ("list",)),
    ("L05", "List: HTML-like name (brief Q4)", "INSERT INTO employee_data VALUES (1,'<b>x</b>','D',1);", ("list",)),
    ("L06", "List: employee data unreadable (RULE-025)", E1, ("list-offline",)),
    # add
    ("A01", "Add: success", "", ("add", {"id": "101", "name": "Asha", "department": "Finance", "salary": "15000"})),
    ("A02", "Add: name and department trimmed", "", ("add", {"id": "101", "name": "  Asha ", "department": " Finance ", "salary": "15000"})),
    ("A03", "Add: whitespace-only field", "", ("add", {"id": "101", "name": "Asha", "department": "   ", "salary": "1"})),
    ("A04", "Add: decimal id", "", ("add", {"id": "10.5", "name": "A", "department": "D", "salary": "1"})),
    ("A05", "Add: text id", "", ("add", {"id": "abc", "name": "A", "department": "D", "salary": "1"})),
    ("A06", "Add: id 0 accepted by the server (RULE-010)", "", ("add", {"id": "0", "name": "A", "department": "D", "salary": "1"})),
    ("A07", "Add: negative id accepted by the server (RULE-010)", "", ("add", {"id": "-5", "name": "A", "department": "D", "salary": "1"})),
    ("A08", "Add: text salary", "", ("add", {"id": "1", "name": "A", "department": "D", "salary": "abc"})),
    ("A09", "Add: negative salary", "", ("add", {"id": "1", "name": "A", "department": "D", "salary": "-500"})),
    ("A10", "Add: NaN salary passes the negative check", "", ("add", {"id": "1", "name": "A", "department": "D", "salary": "NaN"})),
    ("A11", "Add: exponent salary 1e3", "", ("add", {"id": "1", "name": "A", "department": "D", "salary": "1e3"})),
    ("A12", "Add: duplicate id (RULE-005)", E1, ("add", {"id": "101", "name": "X", "department": "D", "salary": "1"})),
    ("A13", "Add: 300-character name", "", ("add", {"id": "1", "name": LONG, "department": "D", "salary": "1"})),
    # edit
    ("E01", "Edit: salary, name ignored (RULE-016)", E1, ("edit", "101", {"name": "Anyone", "field": "salary", "newValue": "18000"})),
    ("E02", "Edit: negative salary accepted (RULE-007/016)", E1, ("edit", "101", {"name": "Asha", "field": "salary", "newValue": "-2000"})),
    ("E03", "Edit: field name case-insensitive", E1, ("edit", "101", {"name": "Asha", "field": "SALARY", "newValue": "1"})),
    ("E04", "Edit: department", E1, ("edit", "101", {"name": "Asha", "field": "department", "newValue": "HR"})),
    ("E05", "Edit: department stored untrimmed", E1, ("edit", "101", {"name": "Asha", "field": "department", "newValue": "  Ops  "})),
    ("E06", "Edit: unknown field", E1, ("edit", "101", {"name": "Asha", "field": "name", "newValue": "Z"})),
    ("E07", "Edit: text salary", E1, ("edit", "101", {"name": "Asha", "field": "salary", "newValue": "abc"})),
    ("E08", "Edit: unknown id", E1, ("edit", "999", {"name": "Asha", "field": "salary", "newValue": "1"})),
    ("E09", "Edit: text id", E1, ("edit", "abc", {"name": "Asha", "field": "salary", "newValue": "1"})),
    ("E10", "Edit: field missing", E1, ("edit", "101", {"name": "Asha", "newValue": "1"})),
    ("E11", "Edit: NaN salary accepted", E1, ("edit", "101", {"name": "Asha", "field": "salary", "newValue": "NaN"})),
    # delete
    ("D01", "Delete: exact id and name", E1, ("delete", "101", "Asha")),
    ("D02", "Delete: name in wrong case (RULE-013/014)", E1, ("delete", "101", "asha")),
    ("D03", "Delete: name with trailing space", E1, ("delete", "101", "Asha ")),
    ("D04", "Delete: text id", E1, ("delete", "abc", "Asha")),
    ("D05", "Delete: name missing", E1, ("delete", "101", None)),
    # sign-up
    ("S01", "Sign-up: success", "", ("signup", {"id": "7", "name": "Ravi"})),
    ("S02", "Sign-up: name trimmed (RULE-008)", "", ("signup", {"id": "7", "name": "  Ravi  "})),
    ("S03", "Sign-up: blank name stored as empty (RULE-008)", "", ("signup", {"id": "7", "name": "   "})),
    ("S04", "Sign-up: text id", "", ("signup", {"id": "abc", "name": "Ravi"})),
    ("S05", "Sign-up: duplicate id (RULE-009)", A1, ("signup", {"id": "7", "name": "Other"})),
    ("S06", "Sign-up: name missing, stored as NULL", "", ("signup", {"id": "7"})),
    # login and session
    ("G01", "Login: success, then greeting (RULE-017)", A1, ("login", {"id": "7", "name": "Ravi"})),
    ("G02", "Login: name in wrong case", A1, ("login", {"id": "7", "name": "ravi"})),
    ("G03", "Login: name not trimmed (RULE-008)", A1, ("login", {"id": "7", "name": " Ravi"})),
    ("G04", "Login: id trimmed", A1, ("login", {"id": " 7 ", "name": "Ravi"})),
    ("G05", "Login: text id", A1, ("login", {"id": "abc", "name": "Ravi"})),
    ("G06", "Dashboard greeting without login (RULE-018)", A1, ("greet",)),
    ("G07", "Logout keeps the session (RULE-015)", A1, ("login-logout", {"id": "7", "name": "Ravi"})),
]

APPROVED = {
    "L06": "RULE-025 marked wrong by the user. " + USER_REQUEST,
    "A13": USER_REQUEST + " Legacy showed a generic error for text over 255 characters.",
    "E06": USER_REQUEST + " Legacy showed 'Employee not found or invalid data!' for an invalid field.",
    "E07": USER_REQUEST + " Legacy showed 'Employee not found or invalid data!' for a non-numeric salary.",
    "E08": USER_REQUEST + " Distinct not-found message.",
    "E09": USER_REQUEST + " Legacy showed Tomcat's HTTP 500 page.",
    "D02": "RULE-014 marked wrong by the user. " + USER_REQUEST,
    "D03": "RULE-014 marked wrong by the user. " + USER_REQUEST,
    "D04": USER_REQUEST + " Legacy showed Tomcat's HTTP 500 page.",
}
UI_APPROVAL = 'Approved by the user on 2026-10-31 ("approve the differences"). '
for _cid in ("E01", "E02", "E03", "E04", "E05", "E11", "D01"):
    APPROVED[_cid] = UI_APPROVAL + "The success message is shown as a toast; legacy stored it in the session but never displayed it (RULE-024)."
APPROVED["G06"] = UI_APPROVAL + 'The anonymous dashboard greeting reads "Welcome!" instead of "Welcome, !".'


def run_legacy(kind, *args):
    c = Client(LEGACY_PORT)
    if kind in ("list", "list-offline"):
        if kind == "list-offline":
            take_table_offline()
        try:
            s, loc, html = c.call("GET", f"{CTX}/ShowEmployeeServlet")
        finally:
            restore_table()
        msg, page = legacy_outcome(s, loc, html, "list")
        return msg, page, legacy_rows(html), None
    if kind == "add":
        s, loc, html = c.call("POST", f"{CTX}/AddEmployeeServlet", form=args[0])
        return (*legacy_outcome(s, loc, html, "addEmployee"), None, None)
    if kind == "edit":
        s, loc, html = c.call("POST", f"{CTX}/EditEmployeeServlet", form={"id": args[0], **args[1]})
        return (*legacy_outcome(s, loc, html, "editEmployee"), None, None)
    if kind == "delete":
        form = {"id": args[0]} | ({"name": args[1]} if args[1] is not None else {})
        s, loc, html = c.call("POST", f"{CTX}/DeleteEmployeeServlet", form=form)
        return (*legacy_outcome(s, loc, html, "deleteEmployee"), None, None)
    if kind == "signup":
        s, loc, html = c.call("POST", f"{CTX}/SignupServlet", form=args[0])
        return (*legacy_outcome(s, loc, html, "signup"), None, None)
    if kind in ("login", "login-logout"):
        s, loc, html = c.call("POST", f"{CTX}/Login.jsp", form=args[0])
        msg, page = legacy_outcome(s, loc, html, "login")
        if kind == "login-logout":
            c.call("POST", f"{CTX}/index.jsp")  # the legacy Logout button
        greet = legacy_greeting(c.call("GET", f"{CTX}/Welcome.jsp")[2]) if page == "welcome" or kind == "login-logout" else None
        return msg, page, None, greet
    if kind == "greet":
        return "(none)", "welcome", None, legacy_greeting(c.call("GET", f"{CTX}/Welcome.jsp")[2])
    raise ValueError(kind)


def run_new(kind, *args):
    c = Client(NEW_PORT)
    if kind in ("list", "list-offline"):
        if kind == "list-offline":
            take_table_offline()
        try:
            s, _, text = c.call("GET", "/api/employees")
        finally:
            restore_table()
        if s == 200:
            js = lambda v: "null" if v is None else str(v)  # EmployeeListPage renders String(value)
            rows = [(js(e["id"]), js(e["name"]), js(e["department"]), e["salaryText"]) for e in json.loads(text)]
            return "(none)", "list", rows, None
        return json.loads(text)["message"], "list", [], None
    if kind == "add":
        s, _, text = c.call("POST", "/api/employees", body=args[0])
        return (*new_outcome("add", s, text), None, None)
    if kind == "edit":
        s, _, text = c.call("PATCH", f"/api/employees/{urllib.parse.quote(args[0])}", body=args[1])
        return (*new_outcome("edit", s, text), None, None)
    if kind == "delete":
        q = f"?name={urllib.parse.quote(args[1])}" if args[1] is not None else ""
        s, _, text = c.call("DELETE", f"/api/employees/{urllib.parse.quote(args[0])}{q}")
        return (*new_outcome("delete", s, text), None, None)
    if kind == "signup":
        s, _, text = c.call("POST", "/api/auth/signup", body=args[0])
        return (*new_outcome("signup", s, text), None, None)
    if kind in ("login", "login-logout"):
        s, _, text = c.call("POST", "/api/auth/login", body=args[0])
        msg, page = new_outcome("login", s, text)
        # login-logout: the React Logout button only navigates home; no request is made
        greet = new_greeting(c.call("GET", "/api/auth/me")[2]) if page == "welcome" or kind == "login-logout" else None
        return msg, page, None, greet
    if kind == "greet":
        return "(none)", "welcome", None, new_greeting(c.call("GET", "/api/auth/me")[2])
    raise ValueError(kind)


def render(msg, page, rows, greet):
    out = [f"message: {msg}", f"page: {page}"]
    if greet is not None:
        out.append(f"greeting: {greet}")
    if rows is not None:
        out.append("rows:")
        out += ["  " + " | ".join(r) for r in sorted(rows, key=lambda r: int(r[0]))] or ["  (none)"]
    out.append(db_state())
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------- servers
def wait_http(port, path, timeout=90):
    end = time.time() + timeout
    while time.time() < end:
        try:
            Client(port).call("GET", path)
            return
        except OSError:
            time.sleep(1)
    raise RuntimeError(f"port {port} did not come up")


def start_legacy(work):
    build = work / "build"
    env = os.environ | {"JAVA_HOME": JDK8}
    subprocess.run(["ant", "-q", f"-Dbuild.dir={build}/build", f"-Ddist.dir={build}/dist", "war"],
                   cwd=LEGACY_SRC, env=env, check=True, capture_output=True)
    base = work / "tomcat"
    for d in ("bin", "conf/Catalina/localhost", "lib", "logs", "temp", "webapps", "work"):
        (base / d).mkdir(parents=True, exist_ok=True)
    # catalina.sh reads CATALINA_BASE/bin/setenv.sh before CATALINA_HOME's; this one omits the
    # IntelliJ debug agent (port 5005) that the developer's own Tomcat already holds
    (base / "bin/setenv.sh").write_text(f"JAVA_HOME={JDK8}\nexport JAVA_HOME\n")
    for f in ("web.xml", "catalina.properties", "logging.properties", "context.xml"):
        shutil.copy(TOMCAT_HOME / "conf" / f, base / "conf" / f)
    server = (TOMCAT_HOME / "conf/server.xml").read_text()
    server = server.replace('port="8005"', f'port="{SHUTDOWN_PORT}"').replace('port="8080"', f'port="{LEGACY_PORT}"')
    server = re.sub(r"<Connector[^>]*protocol=\"AJP/1.3\"[^>]*/>", "", server)
    (base / "conf/server.xml").write_text(server)
    # a conf/Catalina/localhost descriptor overrides the WAR's META-INF/context.xml: point it at employee_equiv
    (base / "conf/Catalina/localhost/EmployeeManagementApp.xml").write_text(
        '<Context><Resource name="jdbc/EmployeeDB" auth="Container" type="javax.sql.DataSource" '
        'driverClassName="org.postgresql.Driver" url="jdbc:postgresql://localhost:5432/employee_equiv" '
        'username="employee_app" password="" maxTotal="20" maxIdle="5" maxWaitMillis="10000" validationQuery="SELECT 1"/></Context>')
    shutil.copy(LEGACY_SRC / "lib/provided/postgresql-42.7.4.jar", base / "lib")
    shutil.copy(build / "dist/EmployeeManagementApp.war", base / "webapps")
    tenv = env | {"CATALINA_BASE": str(base), "CATALINA_HOME": str(TOMCAT_HOME), "CATALINA_PID": str(base / "pid")}
    subprocess.run([str(TOMCAT_HOME / "bin/catalina.sh"), "start"], env=tenv, check=True, capture_output=True)
    try:
        wait_http(LEGACY_PORT, f"{CTX}/index.jsp")
    except Exception:
        stop_legacy(tenv)
        raise
    return tenv


def stop_legacy(tenv):
    subprocess.run([str(TOMCAT_HOME / "bin/catalina.sh"), "stop", "10", "-force"], env=tenv, capture_output=True)


def start_new(work):
    env = os.environ | {"JAVA_HOME": JDK21}
    subprocess.run(["mvn", "-q", "-B", "-Dmaven.test.skip=true", "package"], cwd=BACKEND, env=env, check=True, capture_output=True)
    jar = next((BACKEND / "target").glob("employee-app-*.jar"))
    log = open(work / "new-app.log", "w")
    proc = subprocess.Popen([f"{JDK21}/bin/java", "-jar", str(jar), f"--server.port={NEW_PORT}"],
                            env=env | {"SPRING_DATASOURCE_URL": f"jdbc:postgresql://localhost:5432/{DB}"},
                            stdout=log, stderr=subprocess.STDOUT)
    wait_http(NEW_PORT, "/api/auth/me")
    return proc


def main():
    for side in ("legacy", "new"):
        shutil.rmtree(EQ / side, ignore_errors=True)
        (EQ / side).mkdir(parents=True)
    work = Path(tempfile.mkdtemp(prefix="equiv-"))
    tenv = proc = None
    try:
        print("starting legacy Tomcat on", LEGACY_PORT, "and the new app on", NEW_PORT, "(both on", DB + ")")
        tenv = start_legacy(work)
        proc = start_new(work)
        cases = []
        for cid, title, seed, action in CASES:
            for side, runner in (("legacy", run_legacy), ("new", run_new)):
                reset(seed)
                (EQ / side / f"{cid}.txt").write_text(render(*runner(*action)))
            case = {"id": cid, "title": title, "legacy": f"legacy/{cid}.txt", "new": f"new/{cid}.txt"}
            if cid in APPROVED:
                case["approvedDifference"] = APPROVED[cid]
            cases.append(case)
        (EQ / "cases.json").write_text(json.dumps({
            "system": "employee-app",
            "legacy": {"label": "Servlet/JSP WAR on Tomcat 9, JDK 8", "command": "run_equivalence.py (port 8090)"},
            "new": {"label": "Spring Boot 3.4 on Java 21, outcomes per React navigation", "command": "run_equivalence.py (port 8091)"},
            "cases": cases}, indent=2))
        print(f"{len(cases)} cases recorded")
    finally:
        restore_table()
        reset()
        if proc:
            proc.terminate()
            proc.wait(timeout=30)
        if tenv:
            stop_legacy(tenv)
        print("stopped the legacy Tomcat (8090) and the new app (8091); logs in", work)


if __name__ == "__main__":
    sys.exit(main())
