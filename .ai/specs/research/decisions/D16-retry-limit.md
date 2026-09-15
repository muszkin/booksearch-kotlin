# D16 — Stop after three failed attempts per chapter
- Date, owner: 2026-09-12, muszkin
- Context and the options weighed: A failed call to a free model can retry indefinitely, switch model, or stop for a human.
- Decision and why: Retry a chapter at most three times, then stop the translation for manual resume. Do not silently switch to a paid model.
- Consequences, and what would make us revisit it: The job UI must expose the stopped state and manual resume action.
- Status: active
