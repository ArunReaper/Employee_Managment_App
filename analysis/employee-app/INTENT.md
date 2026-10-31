# Modernization Intent — employee-app

- **Date:** 2026-10-31 (updated same day; replaces the earlier "understand it first" goal)
- **Source:** /Users/arun/Documents/Projects/Code-Modernisation/Employee_Managment_App (repo root; no `legacy/` copy)
- **Current stack:** Java 8, Servlets 4.0 (javax) + JSP/JSTL 1.2, JDBC → PostgreSQL (driver 42.7.4), Ant build, Tomcat 9. ~1,000 LOC (11 Java, 8 JSP).

## Goal
**transform**: the user said, word for word: "My goal is to traanform this application into SpringBoot 3.4 for backend, React 18 for frontend, and keeping postgres as is"

## From → To
- **Backend:** Servlets + JDBC (Java 8) → **Spring Boot 3.4** (needs Java 17+)
- **Frontend:** JSP/JSTL → **React 18**
- **Database:** PostgreSQL, **unchanged** (keep the existing schema in `schema.sql`)

## Purpose / vision
The user said, word for word: "My main goal is to demonstrate the claude code modernisation plugin's capabilities using this sample project which is built on old tech stack of JSP and Servlet, and modernise it with the help of plugin. I want to showcase this as my personal project, where I effectively used the plugin for modernisation without missing anything from the legacy application"

How later commands apply this:
- Run the **full** pipeline, including the optional steps (map, extract-rules, review, verify, harden). Every artifact is part of the showcase.
- **Nothing from the legacy app may be lost.** Every screen, servlet endpoint, form field, validation, navigation path, session and login behavior, and database operation must have a counterpart in the new app, and `verify` must prove it.
- Known quirks and bugs found along the way are **flagged for a human decision** in `review` (keep as-is or fix). They are never silently dropped or silently changed.

## What must stay true
- PostgreSQL is kept as it is (the user's own words). The schema is off limits (preflight answer: "Only the DB schema").
- Full feature parity with the legacy app (from the stated purpose above).

## Open items
- Java version for the Spring Boot backend (17 or 21): `brief` proposes it, the user approves it.
- Spring Boot 3.4 and React 18 are both behind the latest releases as of 2026-10. The user's choice stands unless they change it.

## Update 2026-10-31 (build instructions)
The user said, word for word: "Please give appropriet messages in case of error. Also, create a modern asthetic UI in react. And also create entire app in one go"
- Error cases must show clear, appropriate messages (deviations from the legacy app's silent failures are approved).
- The React UI gets a modern design; visual parity with the JSPs is not required.
- The whole app is built in one pass rather than phase by phase.
