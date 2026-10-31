# Rule Reviews: employee-app

A person reviewed the business rules from `BUSINESS_RULES.md` that need judgement: the three flagged P0 rules, all of them Medium confidence or with a suspected defect. On 2026-10-31 all three were **confirmed as correct and wanted**. The new app must reproduce today's behaviour for each of them, including the suspected defects they describe: edit by ID with no salary checks, login by ID and name with no password, and no login guard on employee operations. The other 8 rules with SME questions in `BUSINESS_RULES.md` (RULE-001, 007, 011, 014, 019, 020, 021/022, 023) were not in this review and remain open for `brief`.


Later the same day, the user's build instruction ("Please give appropriet messages in case of error") marked RULE-014 and RULE-025 as **not wanted**: the new app reports these errors instead of reproducing the silent legacy behaviour.

| Rule | Verdict | When | Title | Note |
|---|---|---|---|---|
| RULE-014 | Wrong / not wanted | 2026-10-31 12:51 | Failed delete still sends the user to the home page as if it worked | Not wanted. The user asked: "Please give appropriet messages in case of error". A failed delete must show "Employee not found!". |
| RULE-016 | Confirmed | 2026-10-31 12:38 | Edit employee: only salary or department can change, and the employee is found by ID alone | Right: keep edit-by-ID only, with no salary checks on edit and no name confirmation (as the code does today). |
| RULE-017 | Confirmed | 2026-10-31 12:38 | Admin login by matching ID and name (no password) | Right: keep ID + name login exactly as today, with no password. |
| RULE-018 | Confirmed | 2026-10-31 12:38 | Employee operations are not restricted to logged-in admins | Right: keep employee operations open, with no login required, as today. |
| RULE-025 | Wrong / not wanted | 2026-10-31 12:51 | Employee list shows an empty table when the employee data cannot be read | Not wanted. The user asked: "Please give appropriet messages in case of error". A read failure must show an error message, not an empty list. |
