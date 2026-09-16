# Translation control and recovery

User-authorized scope: recover the paused Inhibitor Phase job without losing completed segments; expose actionable diagnostics, bounded automatic free-model fallback, manual model selection on resume, references/glossary, and chapter TXT/Markdown export.

Persist options, sampled reference excerpts and attempt metadata atomically in each existing private job workspace. Existing jobs default to automatic fallback and empty context. Owner-scoped endpoints expose chapter status, attempts and exports. No source/completion text or credentials in operational logs. Validation errors distinguish JSON syntax, item count, blank items, invalid characters, truncation and HTTP failures. Record requested and actual model, token usage, timestamp and segment index. Retry invalid responses with smaller batches before moving to the next eligible model; never accept unvalidated output or use paid models. Preserve original segment numbering for existing workspaces.

Start/resume accepts a preferred model, optional ordered fallback list (otherwise currently free models), automatic fallback toggle, reference library IDs, glossary and context notes. References must be owned downloaded Polish EPUBs from the same author; sample 1–5 chapters once, persist the sample, cap prompt context. The UI supports selecting library references and finding/downloading additional titles through existing search; downloads do not trigger delivery. Existing delivery remains available. Updating context on resume affects remaining segments only and is explicitly shown.

Completed chapter exports contain translated text only, reconstruct paragraph boundaries from the EPUB, support TXT and Markdown, and remain available while the job is paused. Partial chapters are visibly marked and missing segments are not represented as translated text. No HTML rendering of provider text.

Verification: workspace validation/export regressions; service fallback, cost guard and resume preservation; owner isolation and options validation; UI controls and API generation; repository test/build gates and production health plus paused-job diagnostics.
