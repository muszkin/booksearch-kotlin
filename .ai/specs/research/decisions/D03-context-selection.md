# D03 — Context comes from chosen books and a glossary
- Date, owner: 2026-09-12, muszkin
- Context and the options weighed: Context could be every book by an author, manually chosen reference books, or only a glossary.
- Decision and why: The user manually selects Polish reference books and uses a glossary. The user may enter another title by the same author even when it is absent from the library; the system retrieves it only as context and never offers it for delivery to Kindle or PocketBook.
- Consequences, and what would make us revisit it: Automatic series detection is deferred. Reference books are not attached wholesale to every chapter; the later design must bound excerpts and glossary generation.
- Status: superseded by D19
