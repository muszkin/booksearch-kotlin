# D11 — Enforce free open models through an allowlist
- Date, owner: 2026-09-12, muszkin
- Context and the options weighed: OpenRouter exposes current prices but does not expose a canonical open-license field for model eligibility.
- Decision and why: An administrator maintains an allowlist of models verified to be open; the application additionally checks that the selected model's current OpenRouter price is zero.
- Consequences, and what would make us revisit it: A model leaves eligibility when its price is non-zero or its licensing no longer meets the allowlist policy.
- Status: superseded by D22
