#!/usr/bin/env python3
"""Verification runner (modernize-verify): re-runs the NEW side of the development cases and runs
fresh inputs, which development never used, on both apps.

It reuses the development harness's functions (../run_equivalence.py) but never its main(): the
legacy outputs and cases.json of development are left untouched. Both apps are built from scratch
copies, so neither legacy/ nor modernized/.../target is written. Database: employee_equiv (local).

Writes: ../new/<case>.txt (new side of the development cases), fresh/legacy/<F>/out.txt,
fresh/new/<F>/out.txt, ../fresh-cases.json.

Usage (from the workspace root): python3 analysis/employee-app/equivalence/fresh/run_fresh.py
"""
import importlib.util, json, os, shutil, subprocess, sys, tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
EQ = HERE.parent
spec = importlib.util.spec_from_file_location("harness", EQ / "run_equivalence.py")
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)

LONG255 = "L" * 255
DEPT255 = "D" * 255
SEED_LIST = ("INSERT INTO employee_data VALUES "
             "(1,'José Müller','Ingeniería',999999.99),(2,'李雷','研发',1234567),(3,'O''Brien','Sales',12345678),"
             "(4,'Anne-Marie','HR',0.30000000000000004),(5,'Zed','Ops',100.1),(6,'Tiny','Ops',0.000025),"
             "(7,'Below','Ops',9999999),(8,'At','Ops',10000000),(9,'Milli','Ops',0.0009999),"
             "(10,'Max','Ops',1.7976931348623157E308),(2147483647,'MaxId','Ops',1),(-3,'NegId','Ops',2);")
SEED_ONE = "INSERT INTO employee_data VALUES (42,'José Müller','Ingeniería',5000);"
SEED_TWIN = "INSERT INTO employee_data VALUES (10,'Sam','A',1),(11,'Sam','B',2);"
SEED_ZOE = "INSERT INTO admin_data VALUES (8,'Zoë'),(9,'Ravi');"

FRESH = [
    ("F01", "List: 12 rows with boundary salaries, non-ASCII names, max and negative ids", SEED_LIST, ("list",)),
    ("F02", "Add: non-ASCII name and department", "", ("add", {"id": "42", "name": "José Müller", "department": "Ingeniería", "salary": "99999.99"})),
    ("F03", "Add: largest int id, smallest cent salary", "", ("add", {"id": "2147483647", "name": "Max", "department": "Ops", "salary": "0.01"})),
    ("F04", "Add: id one above the largest int", "", ("add", {"id": "2147483648", "name": "Max", "department": "Ops", "salary": "1"})),
    ("F05", "Add: padded id, apostrophe in name, zero salary", "", ("add", {"id": " 42 ", "name": "O'Brien", "department": "Sales", "salary": "0"})),
    ("F06", "Add: name of exactly 255 characters", "", ("add", {"id": "5", "name": LONG255, "department": "Ops", "salary": "1"})),
    ("F07", "Edit: non-ASCII department", SEED_ONE, ("edit", "42", {"name": "x", "field": "department", "newValue": "Ingeniería y Diseño"})),
    ("F08", "Edit: salary 9999999 (just below the 1.0E7 notation switch)", SEED_ONE, ("edit", "42", {"name": "x", "field": "salary", "newValue": "9999999"})),
    ("F09", "Edit: department of exactly 255 characters", SEED_ONE, ("edit", "42", {"name": "x", "field": "department", "newValue": DEPT255})),
    ("F10", "Delete: non-ASCII name, exact match", SEED_ONE, ("delete", "42", "José Müller")),
    ("F11", "Delete: one of two employees with the same name", SEED_TWIN, ("delete", "11", "Sam")),
    ("F12", "Sign-up: largest int id, non-ASCII name", "", ("signup", {"id": "2147483647", "name": "Zoë"})),
    ("F13", "Login: non-ASCII admin name", SEED_ZOE, ("login", {"id": "8", "name": "Zoë"})),
    ("F14", "Login: id written with a plus sign", SEED_ZOE, ("login", {"id": "+9", "name": "Ravi"})),
    ("F15", "List: empty department and a name with trailing spaces",
     "INSERT INTO employee_data VALUES (1,'Pad  ','',3);", ("list",)),
]


def run_new_rendered(*action):
    """The harness trims legacy table cells (legacy_rows: c.strip()); trim the new side's cells the
    same way. A browser collapses that whitespace on both pages, so this compares what a user sees."""
    msg, page, rows, greet = h.run_new(*action)
    if rows is not None:
        rows = [tuple(c.strip() for c in r) for r in rows]
    return msg, page, rows, greet


def start_new_from_copy(work):
    """Same as the harness's start_new, but packaged from a scratch copy of the backend."""
    copy = work / "backend"
    shutil.copytree(h.BACKEND, copy, ignore=shutil.ignore_patterns("target"))
    env = os.environ | {"JAVA_HOME": h.JDK21}
    subprocess.run(["mvn", "-q", "-B", "-Dmaven.test.skip=true", "package"], cwd=copy, env=env, check=True, capture_output=True)
    jar = next((copy / "target").glob("employee-app-*.jar"))
    log = open(work / "new-app.log", "w")
    proc = subprocess.Popen([f"{h.JDK21}/bin/java", "-jar", str(jar), f"--server.port={h.NEW_PORT}"],
                            env=env | {"SPRING_DATASOURCE_URL": f"jdbc:postgresql://localhost:5432/{h.DB}"},
                            stdout=log, stderr=subprocess.STDOUT)
    h.wait_http(h.NEW_PORT, "/api/auth/me")
    return proc


def main():
    work = Path(tempfile.mkdtemp(prefix="verify-"))
    tenv = proc = None
    try:
        print("starting legacy Tomcat on", h.LEGACY_PORT, "and the new app on", h.NEW_PORT, "(both on", h.DB + ")")
        tenv = h.start_legacy(work)
        proc = start_new_from_copy(work)
        for cid, _title, seed, action in h.CASES:  # development cases: new side only
            h.reset(seed)
            (EQ / "new" / f"{cid}.txt").write_text(h.render(*h.run_new(*action)))
        print(f"re-ran the new side of {len(h.CASES)} development cases")
        cases = []
        for fid, title, seed, action in FRESH:
            for side, runner in (("legacy", h.run_legacy), ("new", run_new_rendered)):
                out = HERE / side / fid
                out.mkdir(parents=True, exist_ok=True)
                h.reset(seed)
                (out / "out.txt").write_text(h.render(*runner(*action)))
            cases.append({"id": f"{fid}-out", "input": fid, "title": title,
                          "legacy": f"fresh/legacy/{fid}/out.txt", "new": f"fresh/new/{fid}/out.txt"})
        target = EQ / "fresh-cases.json"
        doc = json.loads(target.read_text()) if target.exists() else {}
        kept = {c["id"]: c for c in doc.get("cases", [])}
        for c in cases:  # keep any approvedDifference a person added on an earlier run
            if "approvedDifference" in kept.get(c["id"], {}):
                c["approvedDifference"] = kept[c["id"]]["approvedDifference"]
        doc.update({"system": "employee-app",
                    "legacy": {"label": "Servlet/JSP WAR on Tomcat 9, JDK 8", "command": "fresh/run_fresh.py (port 8090)"},
                    "new": {"label": "Spring Boot 3.4 on Java 21, outcomes per React navigation", "command": "fresh/run_fresh.py (port 8091)"},
                    "cases": cases})
        target.write_text(json.dumps(doc, indent=2, ensure_ascii=False))
        print(f"{len(cases)} fresh inputs recorded on both sides")
    finally:
        h.restore_table()
        h.reset()
        if proc:
            proc.terminate()
            proc.wait(timeout=30)
        if tenv:
            h.stop_legacy(tenv)
        print("stopped the legacy Tomcat (8090) and the new app (8091); logs in", work)


if __name__ == "__main__":
    sys.exit(main())
