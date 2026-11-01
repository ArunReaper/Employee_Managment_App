#!/usr/bin/env python3
"""Extract the dependency topology of employee-app into topology.json.

Java class -> class edges come from jdeps (DOT output) over a fresh compile.
Everything jdeps cannot see is parsed here: @WebServlet routes, JSP links/forms,
redirects/forwards, JSP scriptlet imports, session attributes and SQL tables.

Usage: python3 analysis/employee-app/extract_topology.py   (run from the workspace root)
"""
import json, os, re, shutil, subprocess, sys, tempfile
from collections import defaultdict

ROOT = "legacy/employee-app"          # symlink to the repo root; only named subdirs are walked
OUT = "analysis/employee-app/topology.json"
SRC_DIRS = ["dao", "model", "servlet", "util"]

# Domains from ASSESSMENT.md (D5 Build & Docs has no runtime edges and is left out)
DOMAINS = {
    "dom:access":  ("Access & Identity", ["index.jsp", "signup.jsp", "Login.jsp", "SignUpServlet",
                                         "LoginServlet", "Admin", "AdminDAO"]),
    "dom:crud":    ("Employee CRUD", ["addEmployee.jsp", "editEmployee.jsp", "deleteEmployee.jsp",
                                     "showEmployee.jsp", "AddEmployeeServlet", "EditEmployeeServlet",
                                     "DeleteEmployeeServlet", "ShowEmployeeServlet", "Employee", "EmployeeDAO"]),
    "dom:shell":   ("Presentation Shell", ["Welcome.jsp", "styles.css"]),
    "dom:persist": ("Persistence Infrastructure", ["DBUtil", "context.xml", "schema.sql"]),
}


def read(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return f.read()


def loc(rel):
    return sum(1 for _ in open(os.path.join(ROOT, rel), encoding="utf-8"))


# ---- 1. leaves -------------------------------------------------------------
leaves = {}   # id -> node
for d in SRC_DIRS:
    for fn in sorted(os.listdir(os.path.join(ROOT, d))):
        if fn.endswith(".java"):
            rel = f"{d}/{fn}"
            leaves[fn[:-5]] = {"id": fn[:-5], "name": fn[:-5], "kind": "module",
                               "language": "java", "loc": loc(rel), "file": rel}
for fn in sorted(os.listdir(os.path.join(ROOT, "webapp"))):
    rel = f"webapp/{fn}"
    if fn.endswith(".jsp"):
        leaves[fn] = {"id": fn, "name": fn, "kind": "screen", "language": "jsp", "loc": loc(rel), "file": rel}
    elif fn.endswith(".css"):
        leaves[fn] = {"id": fn, "name": fn, "kind": "module", "language": "css", "loc": loc(rel), "file": rel}
leaves["context.xml"] = {"id": "context.xml", "name": "context.xml (JNDI pool jdbc/EmployeeDB)", "kind": "module",
                         "language": "xml", "loc": loc("webapp/META-INF/context.xml"),
                         "file": "webapp/META-INF/context.xml"}
leaves["schema.sql"] = {"id": "schema.sql", "name": "schema.sql", "kind": "module", "language": "sql",
                        "loc": loc("schema.sql"), "file": "schema.sql"}

edges = set()   # (source, target, kind)
observations = []


def edge(s, t, kind):
    if s != t:
        edges.add((s, t, kind))


# ---- 2. Java class graph via jdeps (fallback: regex on source) -------------
java_files = [os.path.join(ROOT, leaves[k]["file"]) for k in leaves if leaves[k].get("language") == "java"]
cp = ":".join([os.path.join(ROOT, "lib/provided/*"), os.path.join(ROOT, "webapp/WEB-INF/lib/*")])
jdeps_ok = False
if shutil.which("javac") and shutil.which("jdeps"):
    tmp = tempfile.mkdtemp()
    try:
        subprocess.run(["javac", "--release", "8", "-nowarn", "-d", f"{tmp}/classes", "-cp", cp] + java_files,
                       check=True, capture_output=True)
        subprocess.run(["jdeps", "-verbose:class", "-filter:none", "-dotoutput", f"{tmp}/dot", f"{tmp}/classes"],
                       check=True, capture_output=True)
        for fn in os.listdir(f"{tmp}/dot"):
            for m in re.finditer(r'"([\w.$]+)"\s*->\s*"([\w.$]+)', open(f"{tmp}/dot/{fn}").read()):
                s, t = m.group(1).split(".")[-1].split("$")[0], m.group(2).split(".")[-1].split("$")[0]
                if m.group(2).startswith("com.employee.") and s in leaves and t in leaves:
                    edge(s, t, "call")
        jdeps_ok = True
    except subprocess.CalledProcessError as e:
        print("jdeps path failed, falling back to regex:", e.stderr.decode()[:300], file=sys.stderr)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
if not jdeps_ok:
    for k, n in leaves.items():
        if n.get("language") == "java":
            src = read(n["file"])
            for other in leaves:
                if other != k and leaves[other].get("language") == "java" and re.search(rf"\b{other}\b", src):
                    edge(k, other, "call")

# ---- 3. routing: @WebServlet url -> servlet --------------------------------
routes = {}
for k, n in leaves.items():
    if n["file"].startswith("servlet/"):
        m = re.search(r'@WebServlet\(\s*"/([^"]+)"', read(n["file"]))
        if m:
            routes[m.group(1)] = k
            n["route"] = "/" + m.group(1)


def resolve(target):
    """Map a relative URL (form action, href, redirect) to a leaf id."""
    target = target.split("?")[0].lstrip("/")
    return routes.get(target) or (target if target in leaves else None)


unresolved = []
# ---- 4. web edges from JSPs and servlets -----------------------------------
for k, n in leaves.items():
    if n.get("language") not in ("jsp", "java"):
        continue
    src = read(n["file"])
    for m in re.finditer(r'<form[^>]*action="([^"]+)"', src):          # form posts: framework-routed
        t = resolve(m.group(1))
        edge(k, t, "dispatch") if t else unresolved.append((k, m.group(1)))
    for m in re.finditer(r'href="([^"]+)"', src):                       # links
        t = resolve(m.group(1))
        if t:
            edge(k, t, "dispatch" if t in routes.values() else "call")
        else:
            unresolved.append((k, m.group(1)))
    for m in re.finditer(r'(?:sendRedirect|getRequestDispatcher)\("([^"]+)"', src):
        t = resolve(m.group(1))
        edge(k, t, "dispatch") if t else unresolved.append((k, m.group(1)))
    for m in re.finditer(r"(?:window\.location(?:\.href)?)\s*=\s*'([^']+)'", src):  # alert-then-navigate
        t = resolve(m.group(1))
        edge(k, t, "dispatch") if t else unresolved.append((k, m.group(1)))
    if n["language"] == "jsp":                                          # scriptlet imports and calls
        for imp in re.findall(r'<%@\s*page\s+import="([^"]+)"', src):
            for cls in imp.split(","):
                cls = cls.strip().split(".")[-1]
                if cls in leaves:
                    edge(k, cls, "call")

# ---- 5. data: SQL tables, session attributes, config -----------------------
datastores = {}


def ds(name, label=None):
    i = f"ds:{name}"
    datastores.setdefault(i, {"id": i, "name": label or name, "kind": "datastore"})
    return i


for k, n in leaves.items():
    if n.get("language") != "java":
        continue
    src = read(n["file"])
    for m in re.finditer(r'"(INSERT INTO|UPDATE|DELETE FROM|SELECT [^"]*? FROM)\s+(\w+)', src):
        verb, table = m.group(1), m.group(2)
        edge(k, ds(table), "read" if verb.startswith("SELECT") else "write")
for k, n in leaves.items():
    if n.get("language") not in ("jsp", "java"):
        continue
    src = read(n["file"])
    for attr in re.findall(r'(?:getSession\(\)|session)\.setAttribute\("(\w+)"', src):
        edge(k, ds("HttpSession"), "write")
        n.setdefault("sessionWrites", []).append(attr)
    if re.search(r'sessionScope\.\w+|session\.getAttribute\(', src):
        edge(k, ds("HttpSession"), "read")
    if re.search(r'request\.getAttribute\("employeeList"\)', src):
        pass  # covered by the forward edge from ShowEmployeeServlet
for table in re.findall(r"CREATE TABLE IF NOT EXISTS (\w+)", read("schema.sql")):
    edge("schema.sql", ds(table), "write")
if "java:comp/env/jdbc/EmployeeDB" in read("util/DBUtil.java") and 'name="jdbc/EmployeeDB"' in read(
        "webapp/META-INF/context.xml"):
    edge("DBUtil", "context.xml", "read")
# styles.css is linked with href= and therefore already an edge

# ---- 6. entry points and dead ends -----------------------------------------
# No web.xml (build.xml: needxmlfile="false"): every @WebServlet is a public URL and
# Tomcat's default welcome file is index.jsp. schema.sql is run by hand with psql.
entry = ["index.jsp"] + sorted(routes.values()) + ["schema.sql"]
inbound = defaultdict(int)
for s, t, _ in edges:
    inbound[t] += 1
ui_inbound = defaultdict(int)          # inbound edges from inside the app (not the URL itself)
for s, t, _ in edges:
    ui_inbound[t] += 1
dead = sorted(k for k in leaves if ui_inbound[k] == 0 and k not in ("index.jsp", "schema.sql"))

# ---- 7. dead methods (method-level, not nodes) ------------------------------
all_src = "\n".join(read(n["file"]) for n in leaves.values() if n.get("language") in ("java", "jsp"))
dead_methods = []
for k in ("AdminDAO", "EmployeeDAO"):
    for meth in re.findall(r"public (?:static )?[\w<>]+ (\w+)\(", read(leaves[k]["file"])):
        if len(re.findall(rf"\b{meth}\(", all_src)) == 1:
            dead_methods.append(f"{k}.{meth}")

# ---- 8. assemble --------------------------------------------------------------
children = []
for did, (dname, members) in DOMAINS.items():
    children.append({"id": did, "name": dname, "kind": "domain",
                     "children": [leaves[m] for m in members]})
children.append({"id": "dom:data", "name": "Data stores", "kind": "domain",
                 "children": sorted(datastores.values(), key=lambda x: x["id"])})
placed = {m for _, ms in DOMAINS.values() for m in ms}
assert placed == set(leaves), f"unplaced leaves: {set(leaves) - placed}"

observations = [
    "No access guard anywhere: all 6 @WebServlet routes and all 8 JSPs are public URLs (no web.xml, no Filter). "
    "HttpSession.adminName is written at login but only read for display by Welcome.jsp.",
    "LoginServlet (/LoginServlet) has no inbound edge: Login.jsp posts to itself and authenticates in a scriptlet "
    "calling AdminDAO directly, so login logic exists twice and the live copy is in a JSP.",
    "HttpSession is written by Edit/DeleteEmployeeServlet (attribute 'message') but no node reads 'message': "
    "a write-only store, so success feedback is lost.",
    "EmployeeDAO is the single writer and reader of employee_data (4 servlets fan in); AdminDAO likewise owns "
    "admin_data (SignUpServlet, Login.jsp, LoginServlet). Clean seams: each DAO maps 1:1 to a Spring repository.",
    "DBUtil is a single point of failure: a failed JNDI lookup of jdbc/EmployeeDB in its static initialiser "
    "breaks every DAO until redeploy.",
    "Dead methods (never called): " + ", ".join(dead_methods) + ".",
    "Welcome.jsp is the hub: every CRUD page links back to it and every success path redirects to it.",
]
if unresolved:
    observations.append(f"{len(unresolved)} unresolved URL references, e.g. {unresolved[:3]}")

flows = [
    {"name": "Register as an administrator", "persona": "A new administrator",
     "description": "A new administrator signs up with an ID and a name so they can log in later.",
     "steps": [
         {"label": "Open the home page and choose Sign Up", "nodes": ["index.jsp", "signup.jsp"]},
         {"label": "Submit admin ID and name", "nodes": ["signup.jsp", "SignUpServlet"]},
         {"label": "Check the ID is a whole number and save the admin", "nodes": ["SignUpServlet", "AdminDAO", "ds:admin_data"]},
         {"label": "See 'Sign up successful' and go to login", "nodes": ["SignUpServlet", "Login.jsp"]},
     ]},
    {"name": "Log in to the dashboard", "persona": "An administrator",
     "description": "An administrator logs in with their ID and name and lands on the dashboard greeting them by name.",
     "steps": [
         {"label": "Open the login page", "nodes": ["index.jsp", "Login.jsp"]},
         {"label": "Submit ID and name (the page posts to itself)", "nodes": ["Login.jsp"]},
         {"label": "Match ID and name against registered admins", "nodes": ["Login.jsp", "AdminDAO", "ds:admin_data"]},
         {"label": "Remember the admin's name for this visit", "nodes": ["Login.jsp", "ds:HttpSession"]},
         {"label": "Show the dashboard 'Welcome, <name>!'", "nodes": ["Welcome.jsp", "ds:HttpSession"]},
     ]},
    {"name": "Add an employee and see the list", "persona": "An administrator",
     "description": "An administrator records a new employee and then checks the full employee list.",
     "steps": [
         {"label": "Choose Add Employee on the dashboard", "nodes": ["Welcome.jsp", "addEmployee.jsp"]},
         {"label": "Submit ID, name, department and salary", "nodes": ["addEmployee.jsp", "AddEmployeeServlet"]},
         {"label": "Validate: all fields filled, whole-number ID, salary a number and not negative", "nodes": ["AddEmployeeServlet"]},
         {"label": "Save the employee (duplicate ID is reported)", "nodes": ["AddEmployeeServlet", "EmployeeDAO", "ds:employee_data"]},
         {"label": "Back to the dashboard, open the employee list", "nodes": ["Welcome.jsp", "ShowEmployeeServlet"]},
         {"label": "Load every employee and show the table", "nodes": ["ShowEmployeeServlet", "EmployeeDAO", "ds:employee_data", "showEmployee.jsp"]},
     ]},
    {"name": "Update or remove an employee", "persona": "An administrator",
     "description": "An administrator changes an employee's department or salary, or removes an employee by ID and name.",
     "steps": [
         {"label": "Choose Update Employee Details", "nodes": ["Welcome.jsp", "editEmployee.jsp"]},
         {"label": "Submit ID, name, field (department or salary) and new value", "nodes": ["editEmployee.jsp", "EditEmployeeServlet"]},
         {"label": "Update that one field by ID", "nodes": ["EditEmployeeServlet", "EmployeeDAO", "ds:employee_data"]},
         {"label": "Choose Remove Employee and submit ID and name", "nodes": ["Welcome.jsp", "deleteEmployee.jsp", "DeleteEmployeeServlet"]},
         {"label": "Delete only when ID and name both match", "nodes": ["DeleteEmployeeServlet", "EmployeeDAO", "ds:employee_data"]},
         {"label": "Return to the dashboard", "nodes": ["DeleteEmployeeServlet", "Welcome.jsp"]},
     ]},
]

leaf_ids = set(leaves) | set(datastores)
for s, t, _ in edges:
    assert s in leaf_ids and t in leaf_ids, (s, t)
for f in flows:
    for st in f["steps"]:
        for nd in st["nodes"]:
            assert nd in leaf_ids, nd

# keep descriptions from a previous run (merged by describe_nodes.py)
if os.path.exists(OUT):
    prev = json.load(open(OUT))
    def walk(n):
        yield n
        for c in n.get("children", []):
            yield from walk(c)
    old = {n["id"]: n.get("description") for n in walk(prev["root"]) if n.get("description")}
    for n in list(leaves.values()) + list(datastores.values()):
        if n["id"] in old:
            n["description"] = old[n["id"]]

topo = {"system": "Employee Management App (employee-app)",
        "root": {"id": "sys", "name": "employee-app", "kind": "system", "children": children},
        "edges": [{"source": s, "target": t, "kind": k} for s, t, k in sorted(edges)],
        "entryPoints": entry, "deadEnds": dead, "observations": observations, "flows": flows}
json.dump(topo, open(OUT, "w"), indent=2)

# ---- summary ------------------------------------------------------------------
kinds = defaultdict(int)
for _, _, k in edges:
    kinds[k] += 1
print(f"java graph source: {'jdeps -dotoutput' if jdeps_ok else 'regex fallback'}")
print(f"leaves: {len(leaves)} modules/screens + {len(datastores)} datastores; edges: {len(edges)} {dict(kinds)}")
print(f"routes: {', '.join(f'/{r} -> {c}' for r, c in sorted(routes.items()))}")
print(f"entry points: {entry}")
print(f"dead ends: {dead}")
print(f"dead methods: {dead_methods}")
print(f"unresolved refs: {unresolved or 'none'}")
for s, t, k in sorted(edges):
    print(f"  {s:24} -{k:8}-> {t}")
print(f"wrote {OUT}")
