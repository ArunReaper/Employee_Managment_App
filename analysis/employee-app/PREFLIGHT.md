# Preflight — employee-app

- **Date:** 2026-10-31 (re-run the same day: answers reused, nothing changed; no source file is newer than the first run, and tools, settings and DB are the same)
- **Target stack:** Spring Boot 3.4 + React 18, PostgreSQL unchanged
- **Source:** `legacy/employee-app` → symlink to `/Users/arun/Documents/Projects/Code-Modernisation/Employee_Managment_App`, the workspace root itself (see Layout note)

## Answers

1. **Scope.** Is this code the complete system, or one slice of a larger codebase?
   > Complete system
2. **Build & test locally.** Can this environment build and run the tests? How long does CI take?
   > Builds locally, no CI
3. **Bespoke build infrastructure.** Is there org-specific build or dependency machinery?
   > None, plain Ant
4. **Prior attempts.** Has anyone tried to modernize this before?
   > No prior attempts
5. **Off limits.** Is anything not allowed to change in this pass?
   > Only the DB schema

## Check 6 — Scope boundary

Standalone git repository (7 commits, all 2026-10-30/31). Nothing outside it depends on it and it reaches nothing outside. No parent repository or reactor. **Inbound consumers: none. Outbound dependencies: none.**

## Layout note (affects every later command)

The source is the workspace root, so `legacy/employee-app` points back at the folder that contains `legacy/` and `analysis/`, and `modernized/` will land there too. As a result:
- Any tool that **follows symlinks** loops forever (`legacy/employee-app/legacy/employee-app/…`). Scans must skip `legacy/`, `analysis/`, `modernized/`, `build/`, `dist/`, `.idea/` and `.git/`.
- Analysis output and new code will show up as untracked files in this repository. This run is a showcase (see INTENT.md), so **commit `analysis/` and `modernized/`** and add only `legacy/` (the self-referencing link) to `.gitignore`. I did not do this, because that file belongs to the source.

## Results

| # | Check | Status | Found | Fix |
|---|---|---|---|---|
| 1 | Stack | ✅ | Java 8: 11 `.java` files in `com.employee.{dao,model,servlet,util}`, kept in flat `dao/ model/ servlet/ util/` folders. 8 JSP, 1 CSS, `schema.sql`. Servlets 4.0 (`javax.servlet`) with `@WebServlet` (no `web.xml`), JSTL 1.2, plain JDBC through a Tomcat JNDI pool (`jdbc/EmployeeDB` in `webapp/META-INF/context.xml`). Ant `build.xml` builds a WAR for Tomcat 9. About 1,000 LOC. | — |
| 2 | Analysis tooling | ⚠️ | `python3` 3.14.3 ✅. `scc`, `cloc` and `lizard` are missing. | `brew install scc` (LOC/complexity falls back to `wc`, which is fine at this size). `lizard` is only needed for `--portfolio`. |
| 3a | Build definition | ✅ | No CI. `build.xml`: `javac release=8`, compile-only jars in `lib/provided/` (`javax.servlet-api-4.0.1`, `postgresql-42.7.4`), JSTL packaged from `webapp/WEB-INF/lib/jstl-1.2.jar`, `deploy` copies to `/opt/homebrew/opt/tomcat@9/libexec` (present). Nothing bespoke. | — |
| 3b | Legacy smoke | ✅ | Level 1: `javac` (JDK 1.8.0_471) compiles `util`, `model` and `dao` OK. Level 2: `ant war`, built into a temp dir, gives **BUILD SUCCESSFUL** and a 393 KB WAR. ⚠️ Gotcha: `/usr/libexec/java_home -v 1.8` picks the **JRE applet plugin**, which has no `javac`, and Ant then fails. Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home`. | Set JAVA_HOME explicitly for legacy builds. |
| 3c | Target smoke | ✅ | **Spring Boot 3.4.13** on JDK 21.0.9 + Maven 3.9.12: a throwaway project with web, jdbc and postgresql starters passed one `@WebMvcTest` test (1 run, 0 failures). **React 18.3.1** + Vite 5 + Vitest 2 + Testing Library: build OK, 1 test passed. Node 25.2.1, npm 11.6.2. JDK 17.0.12 is also installed. Throwaway projects deleted. | — |
| 4 | Source completeness | ✅ | Every import resolves (JDK, `javax.servlet`, JNDI, `com.employee.*`). Every JSP and servlet link targets something in the tree. DDL in `schema.sql` (`admin_data`, `employee_data`). The live DB `employee` on `localhost:5432` (Postgres 18.4) is up and has both tables. No `web.xml` (annotations only, as expected). Binary-only: the 3 third-party jars only; there is no proprietary code without source. Stale `build/` and `dist/` output sits in the tree and is gitignored. | — |
| 5 | Optional context | ⚠️ | Git history exists but is short (7 commits in 2 days), so change frequency adds little to risk ranking. No telemetry or APM. Docker 29.1.2 is available (useful for Testcontainers Postgres in the equivalence tests). | None needed |
| 6 | Scope boundary | ✅ | Standalone repo, no inbound or outbound crossings. | — |
| 7 | Source edit protection | ⚠️ | No project `.claude/` settings. The user settings have no `Edit` deny rule for `legacy/` or the real path. | See below |

### Check 7 — suggested rules

Because the source *is* the workspace root, the usual `Edit(//<real path>/**)` would also block `analysis/` and `modernized/`, where the commands write. Instead, protect the source folders and files by name. Put this in the project's `.claude/settings.json`:

```json
{ "permissions": { "deny": [
  "Edit(/legacy/**)",
  "Edit(/dao/**)", "Edit(/model/**)", "Edit(/servlet/**)", "Edit(/util/**)",
  "Edit(/webapp/**)", "Edit(/lib/**)", "Edit(/build.xml)", "Edit(/schema.sql)"
] } }
```

A permission rule covers Claude's file tools and the shell commands it recognizes (`sed`, `tee`, `> file`). It does not cover a script that opens the files itself, so a green row here is not a guarantee. The hard guarantee is the operating system: a read-only mount, or a sandbox that blocks shell writes. Managed (organization) settings were not read.

## Readiness

| Command | Verdict | Why |
|---|---|---|
| `assess` | **Ready** | Stack is identified, the source is complete, and only `scc` is missing (falls back to `wc`). |
| `map` | **Ready** | python3 is present. Scans must skip the self-referencing `legacy/` link. |
| `extract-rules` | **Ready** | Small, complete source. |
| `brief` | **Ready** | Needs only the discovery artifacts. Target is taken from INTENT.md: Spring Boot 3.4 + React 18, schema off limits. |
| `transform` | **Ready** | Target stack builds and tests here. The legacy build runs too (JDK 8 + Ant + Tomcat 9 + live Postgres), so a true dual run is possible. |
| `reimagine` | **Ready** | Same as transform. |
| `harden` | **Ready-with-gaps** | No Java SAST tool (SpotBugs/FindSecBugs, Semgrep) is installed, so the scan is Claude-only. Optional: `brew install semgrep`. |
| `uplift` | n/a | The goal is a transform, not a same-stack version bump. |

**Nothing is red.** The most useful fixes are the Check 7 deny rules and, optionally, `brew install scc`.

**Next:** `/code-modernization:modernize-assess employee-app`
