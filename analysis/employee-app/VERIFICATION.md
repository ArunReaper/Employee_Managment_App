# Verification: employee-app

Written by `scripts/proof_pack.py` on 2026-10-31 07:54 UTC. Each module's verdict is computed from the evidence files by the fixed rules at the end of this page. No model's opinion is part of it.

**Overall: PROVEN** (1 proven)


Legacy source: `legacy/employee-app`. Could it run here: yes. The source was checked by modification time, and no version-control tool was run.

| Module | Track | Verdict | Tests executed | Failed | P0 rules tested | Same behavior | Fresh inputs |
|---|---|---|---|---|---|---|---|
| app | rewrite | **PROVEN** | 265 | 0 | 3 of 3 | pass | pass |

## app: PROVEN

Track: rewrite. Folder: `modernized/employee-app/app`. Checked 2026-10-31 07:54 UTC.

**What passed**

- Tests ran: 265 test(s) executed, 0 failed, 0 skipped. Note: Run from clean (target/ deleted). Run from clean (Vite/Vitest caches deleted); result file kept outside the code tree.
- Rules traced: All 3 P0 rule(s) this module answers for are backed by a test that ran and passed.
- Same behavior: 48 of 48 development case(s) executed: 31 same, 0 differ, 0 missing; comparator self-check passed. 17 difference(s) a person approved are listed.
- Fresh inputs: 14 new input(s), 14 comparison(s), ran on the legacy and the new code with no difference (8 approved by a person).
- Canary: 1 canary run(s) shown by a result file or log; the first (AuthService.logIn trims the admin name before matching (breaks RULE-017/RULE-008), scratch copy, LoginSessionCharacterizationTest) made 4 more test(s) fail than the clean run (analysis/employee-app/equivalence/canary/app). 1 more are only claimed.
- Source untouched: legacy/employee-app is untouched: no file changed after the analysis started (29 files compared). Checked by modification time, and no version-control tool was run.

| Check | Result | Detail |
|---|---|---|
| Tests ran | pass | 265 test(s) executed, 0 failed, 0 skipped. Note: Run from clean (target/ deleted). Run from clean (Vite/Vitest caches deleted); result file kept outside the code tree. |
| Rules traced | pass | All 3 P0 rule(s) this module answers for are backed by a test that ran and passed. |
| Same behavior | pass | 48 of 48 development case(s) executed: 31 same, 0 differ, 0 missing; comparator self-check passed. 17 difference(s) a person approved are listed. |
| Fresh inputs | pass | 14 new input(s), 14 comparison(s), ran on the legacy and the new code with no difference (8 approved by a person). |
| Canary | pass | 1 canary run(s) shown by a result file or log; the first (AuthService.logIn trims the admin name before matching (breaks RULE-017/RULE-008), scratch copy, LoginSessionCharacterizationTest) made 4 more test(s) fail than the clean run (analysis/employee-app/equivalence/canary/app). 1 more are only claimed. |
| Source untouched | pass | legacy/employee-app is untouched: no file changed after the analysis started (29 files compared). Checked by modification time, and no version-control tool was run. |

tests executed: 265, failed 0, skipped 0

- backend characterization tests (JUnit 5 + MockMvc, PostgreSQL employee_test): `cd modernized/employee-app/app/backend && mvn -B test -Dmaven.test.failure.ignore=true` (from result files): 248 executed, 0 failed, 0 skipped, 8 result files written 2026-10-31 07:53 UTC. Note: Run from clean (target/ deleted).
- frontend UI tests (Vitest + Testing Library): `cd modernized/employee-app/app/frontend && npx vitest run --reporter=default --reporter=junit --outputFile.junit=<workspace>/analysis/employee-app/equivalence/frontend-junit/junit.xml` (from result files): 17 executed, 0 failed, 0 skipped, 1 result files written 2026-10-31 07:53 UTC. Note: Run from clean (Vite/Vitest caches deleted); result file kept outside the code tree.

### P0 business rules

| Rule | Name | Confidence | Result | Tests | Code | A test that names it |
|---|---|---|---|---|---|---|
| RULE-016 | Edit employee: only salary or department can change, and the employee is found by ID alone | Medium | tested | 31 | 3 | `modernized/employee-app/app/frontend/src/test/pages.test.jsx:127` |
| RULE-017 | Admin login by matching ID and name (no password) | High | tested | 37 | 3 | `modernized/employee-app/app/frontend/src/test/pages.test.jsx:198` |
| RULE-018 | Employee operations are not restricted to logged-in admins | Medium | tested | 17 | 4 | `modernized/employee-app/app/frontend/src/test/pages.test.jsx:233` |

### Development cases, judged again

48 of 48 executed: 31 same, 0 differ, 0 missing.
- Approved difference L06: RULE-025 marked wrong by the user. Approved by the user on 2026-10-31: "Please give appropriet messages in case of error" (MODERNIZATION_BRIEF.md, approver revision item 2).
- Approved difference A13: Approved by the user on 2026-10-31: "Please give appropriet messages in case of error" (MODERNIZATION_BRIEF.md, approver revision item 2). Legacy showed a generic error for text over 255 characters.
- Approved difference E01: Approved by the user on 2026-10-31 ("approve the differences"). The success message is shown as a toast; legacy stored it in the session but never displayed it (RULE-024).
- Approved difference E02: Approved by the user on 2026-10-31 ("approve the differences"). The success message is shown as a toast; legacy stored it in the session but never displayed it (RULE-024).
- Approved difference E03: Approved by the user on 2026-10-31 ("approve the differences"). The success message is shown as a toast; legacy stored it in the session but never displayed it (RULE-024).
- Approved difference E04: Approved by the user on 2026-10-31 ("approve the differences"). The success message is shown as a toast; legacy stored it in the session but never displayed it (RULE-024).
- Approved difference E05: Approved by the user on 2026-10-31 ("approve the differences"). The success message is shown as a toast; legacy stored it in the session but never displayed it (RULE-024).
- Approved difference E06: Approved by the user on 2026-10-31: "Please give appropriet messages in case of error" (MODERNIZATION_BRIEF.md, approver revision item 2). Legacy showed 'Employee not found or invalid data!' for an invalid field.
- Approved difference E07: Approved by the user on 2026-10-31: "Please give appropriet messages in case of error" (MODERNIZATION_BRIEF.md, approver revision item 2). Legacy showed 'Employee not found or invalid data!' for a non-numeric salary.
- Approved difference E08: Approved by the user on 2026-10-31: "Please give appropriet messages in case of error" (MODERNIZATION_BRIEF.md, approver revision item 2). Distinct not-found message.
- Approved difference E09: Approved by the user on 2026-10-31: "Please give appropriet messages in case of error" (MODERNIZATION_BRIEF.md, approver revision item 2). Legacy showed Tomcat's HTTP 500 page.
- Approved difference E11: Approved by the user on 2026-10-31 ("approve the differences"). The success message is shown as a toast; legacy stored it in the session but never displayed it (RULE-024).
- Approved difference D01: Approved by the user on 2026-10-31 ("approve the differences"). The success message is shown as a toast; legacy stored it in the session but never displayed it (RULE-024).
- Approved difference D02: RULE-014 marked wrong by the user. Approved by the user on 2026-10-31: "Please give appropriet messages in case of error" (MODERNIZATION_BRIEF.md, approver revision item 2).
- Approved difference D03: RULE-014 marked wrong by the user. Approved by the user on 2026-10-31: "Please give appropriet messages in case of error" (MODERNIZATION_BRIEF.md, approver revision item 2).
- Approved difference D04: Approved by the user on 2026-10-31: "Please give appropriet messages in case of error" (MODERNIZATION_BRIEF.md, approver revision item 2). Legacy showed Tomcat's HTTP 500 page.
- Approved difference G06: Approved by the user on 2026-10-31 ("approve the differences"). The anonymous dashboard greeting reads "Welcome!" instead of "Welcome, !".

Inputs left out of the fresh check: Malformed records (letters, tabs or blanks in numeric fields beyond the development cases) were not tried: INTENT.md asks for feature parity, not quirk-for-quirk matching.

### Fresh inputs

14 new input(s) counted from 15 comparison(s) executed: 7 same, 0 differ, 0 missing.
- Not counted, because the legacy output equals a development output (status lines repeat by nature): F04-out

### Waiting for a person

Claude never ticks these. A person decides them.

- [ ] `modernized/employee-app/backend` builds with Maven on JDK 21 and Spring Boot 3.4.x; `frontend` builds with React 18.3.x [Phase 1 exit criterion]
- [ ] Integration tests run against PostgreSQL provisioned from the unchanged `schema.sql` (Testcontainers or the local Postgres 18) [Phase 1 exit criterion]
- [ ] The equivalence harness runs legacy and new side by side on identical seeded data, and fails on a planted difference [Phase 1 exit criterion]
- [ ] RULE-002, RULE-023 and RULE-025 are covered by passing equivalence tests: same rows, same row set, same salary text, empty table on a read failure [Phase 1 exit criterion]
- [ ] A `git diff` of `schema.sql` and of every legacy source file is empty [Phase 1 exit criterion]
- [ ] The P0 rule RULE-016 is proven equivalent by side-by-side tests, including name ignored, negative/NaN/Infinity salary accepted, case-insensitive field, unknown field rejected [Phase 2 exit criterion]
- [ ] RULE-003, 004, 005, 006, 007, 010, 011, 013, 014 and 024 are each covered by passing equivalence tests [Phase 2 exit criterion]
- [ ] Every legacy message text for add, edit and delete appears verbatim in the new UI [Phase 2 exit criterion]
- [ ] Database state after each scenario is identical between legacy and new (row-by-row comparison) [Phase 2 exit criterion]
- [ ] The P0 rules RULE-017 and RULE-018 are proven equivalent by side-by-side tests: [Phase 3 exit criterion]
- [ ] RULE-008, 009, 012, 015 and 020 are each covered by passing equivalence tests [Phase 3 exit criterion]
- [ ] All four persona flows in section 4 pass end to end in both apps with identical outcomes [Phase 3 exit criterion]
- [ ] `/code-modernization:modernize-verify employee-app` returns a verdict for every module, and each difference is accepted or fixed [Phase 3 exit criterion]
- [ ] The new app runs as one Spring Boot deployable against the existing `employee` database, with the schema unchanged [Phase 3 exit criterion]

### What this does not prove

- It does not prove behavior on inputs nobody tried: the equivalence cases and the fresh inputs are samples, not the whole input space.
- A difference a person approved is a decision, not evidence.
- It does not cover speed, capacity, security, concurrency, or anything the tests and cases do not exercise.
- It traces only the rules that were extracted: behavior nobody wrote down as a rule has no rule to trace.
- A test that names a rule shows the rule is mentioned, not that the test is a good one. The canary shows only that some test can fail.
- It does not replace the review and sign-off of the people who own the system.

## Sign-off

A person fills this in. Claude leaves it blank.

| | |
|---|---|
| Name | ________________ |
| Role | ________________ |
| Date | __________ |
| Decision | accept / accept with conditions / reject |

## How each verdict is computed

PROVEN needs every check to pass: the six below, and for an uplift three more (7 to 9). NOT PROVEN when any check fails. PARTLY PROVEN when nothing failed but a check could not pass.

1. Tests ran: at least one test executed in a fresh run, none failed, none was skipped without a reason, and every result file is newer than the code. The counts must be read by this script from result files or a saved raw runner log; counts only typed in cannot reach PROVEN.
2. Rules traced (rewrite and reimagine): every P0 rule the module answers for is backed by a test that ran and passed: its id is in the test or class name of a result this script parsed, or a test file names it on a line not marked skipped or pending and that file's class ran and passed. A rule named only by skipped, pending or failing tests is named, not run; one only the notes name is claimed. Neither is tested.
3. Same behavior: the development cases, judged again by compare.py, executed at least one case and none differs or is missing (a difference a person approved is allowed and listed); for an uplift, no regression, no new failure, no missing test and no drop in executed tests against BASELINE.md.
4. Fresh inputs: at least 10 new inputs, none with the same output as a development case, ran on the legacy and the new code with no difference. Needed whenever the legacy could run here; when it could not, the proof is trace-based and the best verdict is PARTLY PROVEN.
5. Canary: a deliberate one-line break whose own result file or saved runner log shows failing tests. A line in the notes is a claim.
6. Source untouched: no file under legacy/<system> is newer than the analysis (PREFLIGHT.md), by a plain file walk; no version-control tool is run.
7. Baseline measured (uplift): the old version's numbers in BASELINE.md come from per-test result files, a per-test results file or a raw runner log that this script parsed (in analysis/<system>/baseline/, or on a Recorded: or Machine-readable: line of BASELINE.md), and the typed table agrees with them. A table typed by hand, or sources that disagree, cannot reach PROVEN.
8. Tests kept (uplift): no test file of the untouched legacy tree is missing from the working copy, no more than 25% of them changed, and the legacy tree has test files to compare against (walked by file, no process is run). The list is for a person to review; weakened assertions cannot be detected, only that files changed.
9. Deltas covered (uplift): every Behavioral-silent delta in DELTA_CATALOG.md has its site's file named, as a whole word, by some test file of the working copy. A name is not proof that the test exercises the change.
Open questions, unticked criteria and the sign-off are for a person. They never change the verdict.
A folder that holds only test code and the files that build it is test tooling: it is listed, not judged, and not counted.
