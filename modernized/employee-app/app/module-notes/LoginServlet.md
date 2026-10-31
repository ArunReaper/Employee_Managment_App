# LoginServlet: transformation notes

- **Legacy:** `servlet/LoginServlet.java`
- **Target:** NOT MIGRATED: unreachable from the UI, dropped per brief Q7
- **Rules:** RULE-020
- **Status:** transformed on 2026-10-31 as part of the one-pass build of Phases 1–3 (approver instruction).
- **Proof:** backend characterization tests 248/248; front-end tests 17/17; side-by-side run of 48 cases (31 same, 17 differences approved by the user; PROVEN); canary → 18 tests failed.

The full mapping (legacy file:lines → target file:lines), the deviations and the evidence are in [`../TRANSFORMATION_NOTES.md`](../TRANSFORMATION_NOTES.md).
