# Software delivery process

## Purpose

This repository uses GitHub issues and pull requests to deliver changes to
`main`. Repository automation is configured in
`.ai/agentic.config.json`; the GitHub operation contract lives in
`.ai/trackers/github.md`.

## Flow

1. Shape non-trivial work in a brief or specification under `.ai/specs`.
2. Triage the issue against `main`; check for an existing claim or pull request.
3. Claim active work by assigning the owner, adding `in-progress`, and posting
   a short claim comment.
4. Implement the smallest coherent change with regression coverage, then run the
   validation gate.
5. Open a pull request against `main`, apply one pipeline label, a category,
   priority, and risk labels as appropriate.
6. Review against `CODE_REVIEW.md`. Requested changes return the pull request
   to `changes-requested`; an approved pull request moves to `merge-queue`.
7. Merge only after required checks pass and the QA gate is satisfied. Use
   squash merge unless a maintainer explicitly selects another strategy.

## Labels and QA

Pipeline labels are mutually exclusive: `review`, `changes-requested`,
`qa`, `qa-failed`, `merge-queue`, `blocked`, and `do-not-merge`.
Category, priority, risk, and process labels are additive. Unset priority and
risk mean medium.

A pull request with `needs-qa` cannot merge until a QA reviewer applies
`qa-approved`. `skip-qa` is only for low-risk, non-user-facing work and
must never be combined with `needs-qa`. A self-QA exception also needs
`qa-self-verified` and evidence of the exercised flow.

Release the `in-progress` claim when implementation and reporting finish.
`ci-monitoring` records that a CI follow-up is owed; it is not a lock.

## Validation gate

Run these commands in order before review sign-off:

- `./gradlew :backend:test`
- `pnpm --dir frontend test -- --run`
- `./gradlew build`

Any failing command blocks the pull request. Re-run this setup when the
toolchain, validation commands, or label taxonomy changes.
