# BookSearch v2

A self-hosted web application for searching, downloading, and managing e-books from Anna's Archive. It provides a clean interface for discovering books with language and format filters, maintains a personal library per user, and supports sending books directly to Kindle and PocketBook devices via SMTP. The backend handles Cloudflare-protected pages through FlareSolverr and offers format conversion between EPUB and MOBI using Calibre.

## Features

- Full-text search against Anna's Archive with language (Polish, English, German) and format (EPUB, MOBI, PDF) filters
- Multi-user support with JWT authentication and role-based access (super-admin, regular user)
- Personal library per user with download, deletion, and ownership tracking
- Asynchronous book downloads with job status polling
- EPUB to MOBI (and reverse) conversion via Calibre
- Send books to Kindle and PocketBook devices over SMTP
- Delivery history with per-book and per-device indicators
- Automatic mirror discovery and rotation for Anna's Archive
- Super-admin panel: toggle registration, manage users, force password resets
- OpenAPI 3.0 specification with Swagger UI
- Full request logging with correlation IDs
- Containerized deployment with a single `docker compose up`

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Kotlin 2.3.20, Ktor 3.4.2, JOOQ 3.21.1, Liquibase 5.0.2 |
| Frontend | Vue.js 3.5, Pinia 3, Tailwind CSS 4, TypeScript 6 |
| Database | SQLite |
| Cloudflare bypass | FlareSolverr |
| Format conversion | Calibre (ebook-convert) |
| Dev mail | Mailpit |
| Runtime | Eclipse Temurin JRE 21 (Debian) |

## Quick Start

```bash
git clone https://github.com/muszkin/booksearch-v2.git
cd booksearch-v2
cp .env.example .env
# Edit .env — at minimum set JWT_SECRET to a random string

docker compose up -d
```

The application is available at `http://localhost:8080`. Register the first user account — it automatically becomes the super-admin.

Mailpit UI (dev): `http://localhost:8025`

## Screenshots

| View | Description |
|------|-------------|
| **Login** | Clean centered card with email/password fields and links to register / reset password. |
| **Search** | Top toolbar with query input, language dropdown, format dropdown, and search button. Results appear as cards showing title, author, language, format, file size, and ownership indicators. |
| **Selection Panel** | Slide-in panel from the right listing all checked search results with download/send actions. |
| **Library** | Paginated table of downloaded books with columns for title, author, format, file size, and action buttons (download file, convert, send to device). Delivery indicators shown per entry. |
| **Settings** | Tabbed form for Kindle and PocketBook SMTP configuration (host, port, username, password, from address, recipient). |
| **Admin** | User management table with invite form and password change modal. Registration toggle switch. |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | HTTP port the backend listens on |
| `DATABASE_PATH` | `./data/booksearch.db` | Path to the SQLite database file |
| `JWT_SECRET` | `dev-secret-change-in-production` | HMAC256 signing key for JWT tokens. **Set a strong random value in production.** |
| `JWT_ISSUER` | `booksearch` | JWT issuer claim |
| `JWT_AUDIENCE` | `booksearch-users` | JWT audience claim |
| `SOLVEARR_URL` | `http://flaresolverr:8191` | FlareSolverr endpoint |
| `MIRROR_DOMAINS` | `annas-archive.gd,...` | Comma-separated list of Anna's Archive mirror domains |
| `MAX_CONCURRENT_DOWNLOADS` | `2` | Maximum parallel download jobs |
| `DATA_PATH` | `./data/library` | Directory for downloaded book files |
| `SMTP_HOST` | `mailpit` | SMTP server host (used for system emails) |
| `SMTP_PORT` | `1025` | SMTP server port |

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — system overview, data flow diagrams, component structure
- [API Reference](docs/API.md) — complete list of REST endpoints
- [Development Guide](docs/DEVELOPMENT.md) — local setup, testing, building

## License

MIT
