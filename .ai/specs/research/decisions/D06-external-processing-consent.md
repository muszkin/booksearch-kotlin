# D06 — Permit external OpenRouter processing
- Date, owner: 2026-09-12, muszkin
- Context and the options weighed: Source text and selected context can remain local or be sent through OpenRouter for translation.
- Decision and why: Sending the source text and selected context through the OpenRouter API is acceptable for this private-use feature.
- Consequences, and what would make us revisit it: The UI must make this external processing clear before the first translation; credentials remain runtime configuration, not database content.
- Status: active
