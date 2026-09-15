# D22 — Use all currently free models regardless of licence
- Date, owner: 2026-09-12, muszkin
- Context and the options weighed: D11 required an open-licence allowlist in addition to a zero-price check.
- Decision and why: Any OpenRouter model with a current price of zero is eligible; its licence is not an eligibility criterion. The administrator may modify the configured model selection.
- Consequences, and what would make us revisit it: The application checks live pricing before a task starts and must reject a model whose price is no longer zero. D11 is superseded.
- Status: active
