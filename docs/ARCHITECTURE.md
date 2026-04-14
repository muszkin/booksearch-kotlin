# Architecture

## System Overview

BookSearch v2 is a self-hosted application for searching, downloading, and managing e-books from Anna's Archive. The backend (Kotlin/Ktor) serves both the REST API and the Vue.js SPA (embedded in the fat JAR). All browser-automation tasks are delegated to FlareSolverr to bypass Cloudflare protection.

```mermaid
C4Context
    title BookSearch v2 — System Context

    Person(user, "User", "Searches, downloads and manages e-books")

    System(frontend, "Frontend SPA", "Vue.js 3 + Pinia + Tailwind CSS 4")
    System(backend, "Backend API", "Kotlin 2.3 / Ktor 3.4 / JOOQ / Liquibase")
    SystemDb(sqlite, "SQLite", "Users, books, library, settings, mirrors")
    System_Ext(flaresolverr, "FlareSolverr", "Headless browser proxy for Cloudflare bypass")
    System_Ext(annas, "Anna's Archive", "E-book search engine")
    System_Ext(smtp, "SMTP / Mailpit", "E-mail delivery for Kindle & PocketBook")

    Rel(user, frontend, "Uses", "HTTPS")
    Rel(frontend, backend, "REST API", "JSON / JWT")
    Rel(backend, sqlite, "Read/Write", "JDBC")
    Rel(backend, flaresolverr, "POST /v1", "HTTP")
    Rel(flaresolverr, annas, "Browse", "HTTP")
    Rel(backend, smtp, "Send", "SMTP")
```

## Data Flow — Search

When a user performs a search the request travels through several layers before results are returned.

```mermaid
sequenceDiagram
    participant U as User
    participant ST as SearchToolbar
    participant SS as SearchStore (Pinia)
    participant API as Axios Client
    participant BE as SearchRoutes
    participant SRV as SearchService
    participant SCR as ScraperService
    participant SOL as SolvearrClient
    participant FS as FlareSolverr
    participant AA as Anna's Archive
    participant HP as HtmlParser
    participant BR as BookRepository

    U->>ST: Enter query + filters
    ST->>SS: search(query, lang, format)
    SS->>API: GET /api/search?q=...&lang=...&format=...
    API->>BE: HTTP request with JWT
    BE->>SRV: search(query, lang, format, userId)
    SRV->>BR: Check cached results (TTL)
    alt Cache hit
        BR-->>SRV: Cached BookResult[]
    else Cache miss
        SRV->>SCR: scrape(query, lang, format)
        SCR->>SOL: buildSearchUrl + request
        SOL->>FS: POST /v1 {cmd: "request.get", url}
        FS->>AA: GET search page
        AA-->>FS: HTML response
        FS-->>SOL: {solution: {response: html}}
        SOL-->>SCR: Raw HTML
        SCR->>HP: parse(html)
        HP-->>SCR: ParsedBookEntry[]
        SCR-->>SRV: ParsedBookEntry[]
        SRV->>BR: upsert books + cache
    end
    SRV-->>BE: SearchResponse
    BE-->>API: 200 JSON
    API-->>SS: Update state
    SS-->>ST: Reactive render
```

## Data Flow — Download

Downloading a book is a multi-step process that involves resolving the detail page, finding the slow-download link, fetching the binary, and extracting metadata.

```mermaid
sequenceDiagram
    participant U as User
    participant DR as DownloadRoutes
    participant DS as DownloadService
    participant SOL as SolvearrClient
    participant FS as FlareSolverr
    participant AA as Anna's Archive
    participant IMP as ImpersonatorHttpClient
    participant FS2 as File Storage
    participant MS as MetadataService
    participant BR as BookRepository
    participant UL as UserLibraryRepository

    U->>DR: POST /api/download/{md5}
    DR->>DS: startDownload(userId, md5, format)
    DS-->>DR: 202 {jobId}
    Note over DS: Async coroutine begins

    DS->>SOL: Fetch detail page for MD5
    SOL->>FS: POST /v1
    FS->>AA: GET /md5/{md5}
    AA-->>FS: Detail HTML
    FS-->>SOL: HTML
    SOL-->>DS: Parsed slow_download link

    DS->>SOL: Fetch slow_download page
    SOL->>FS: POST /v1
    FS->>AA: GET slow_download URL
    AA-->>FS: Page with binary link
    FS-->>SOL: HTML with direct URL
    SOL-->>DS: Direct download URL

    DS->>IMP: GET binary file
    IMP-->>DS: Binary stream
    DS->>FS2: Write to data/{userId}/{md5}.{format}
    DS->>MS: extractMetadata(file)
    MS-->>DS: Title, Author, Cover
    DS->>BR: Update book metadata
    DS->>UL: Mark entry as downloaded
```

## Authentication Flow

The application uses JWT-based authentication with access/refresh token rotation and optional password-reset via SMTP.

```mermaid
sequenceDiagram
    participant U as Browser
    participant FE as Vue Router Guard
    participant AX as Axios Interceptor
    participant BE as AuthRoutes
    participant AS as AuthService
    participant DB as SQLite

    U->>FE: Navigate to protected route
    FE->>FE: Check authStore.isAuthenticated

    alt Not authenticated
        FE->>U: Redirect to /login
        U->>BE: POST /api/auth/login {email, password}
        BE->>AS: login(email, password)
        AS->>DB: Verify credentials (bcrypt)
        AS-->>BE: {accessToken, refreshToken, user}
        BE-->>U: 200 JSON
        U->>U: Store tokens in localStorage
    end

    U->>AX: Request to /api/*
    AX->>AX: Attach Authorization: Bearer {accessToken}
    AX->>BE: Protected request
    BE->>BE: Ktor JWT plugin validates token
    BE->>BE: Extract UserPrincipal (userId, email, isSuperAdmin)
    BE->>BE: CallId → MDC for request logging
    BE-->>AX: Response

    alt 401 Unauthorized (token expired)
        AX->>BE: POST /api/auth/refresh {refreshToken}
        BE->>AS: refresh(refreshToken)
        AS->>DB: Validate + rotate refresh token
        AS-->>BE: New {accessToken, refreshToken}
        BE-->>AX: 200
        AX->>AX: Update localStorage
        AX->>BE: Retry original request
    end
```

## Component Diagram

### Backend Package Structure

```mermaid
graph TD
    subgraph "api/"
        AuthRoutes
        AdminRoutes
        SearchRoutes
        LibraryRoutes
        DownloadRoutes
        ConvertRoutes
        DeliverRoutes
        SettingsRoutes
        MirrorRoutes
        HealthRoute
        OpenApiRoutes
    end

    subgraph "service/"
        AuthService
        SearchService
        ScraperService
        DownloadService
        LibraryService
        ConversionService
        DeliveryService
        MetadataService
        MirrorService
        CalibreWrapper
    end

    subgraph "repository/"
        UserRepository
        RefreshTokenRepository
        PasswordResetTokenRepository
        SystemConfigRepository
        BookRepository
        UserLibraryRepository
        UserSettingsRepository
        MirrorRepository
        DownloadJobRepository
        DeliveryRepository
    end

    subgraph "infrastructure/"
        DatabaseFactory
        SolvearrClient
        ImpersonatorHttpClient
        HtmlParser
        RequestLoggerPlugin
        ScraperConfig
        MirrorConfig
    end

    AuthRoutes --> AuthService
    AdminRoutes --> AuthService
    SearchRoutes --> SearchService
    LibraryRoutes --> LibraryService
    DownloadRoutes --> DownloadService
    ConvertRoutes --> ConversionService
    DeliverRoutes --> DeliveryService
    SettingsRoutes --> UserSettingsRepository
    MirrorRoutes --> MirrorService

    SearchService --> ScraperService
    SearchService --> BookRepository
    ScraperService --> SolvearrClient
    ScraperService --> MirrorService
    DownloadService --> SolvearrClient
    DownloadService --> ImpersonatorHttpClient
    DownloadService --> MetadataService
    ConversionService --> CalibreWrapper
    DeliveryService --> UserSettingsRepository
    MirrorService --> MirrorRepository

    SolvearrClient --> ScraperConfig
    ImpersonatorHttpClient --> ScraperConfig
    DatabaseFactory --> UserRepository
    DatabaseFactory --> BookRepository
```

### Frontend Architecture

```mermaid
graph TD
    subgraph "views/"
        LoginView
        RegisterView
        SearchView
        LibraryView
        SettingsView
        AdminView
    end

    subgraph "components/"
        subgraph "layout/"
            MainLayout
            AuthLayout
            AppSidebar
            AppLogo
            MobileDrawer
        end
        subgraph "search/"
            SearchToolbar
            SearchResultCard
            SelectionPanel
        end
        subgraph "library/"
            LibraryTable
            BookActions
        end
        subgraph "admin/"
            UserTable
            InviteUserForm
            ChangeUserPasswordModal
        end
        subgraph "settings/"
            DeviceSettingsForm
        end
        subgraph "base/"
            BaseButton
            BaseInput
            BaseSelect
            AlertMessage
            EmptyState
        end
    end

    subgraph "stores/"
        AuthStore
        SearchStore
        SelectionStore
        LibraryStore
        SettingsStore
        AdminStore
    end

    subgraph "api/"
        AxiosClient
        subgraph "generated/"
            GenAuthService[AuthService]
            GenSearchService[SearchService]
            GenLibraryService[LibraryService]
            GenDownloadService[DownloadService]
            GenConvertService[ConvertService]
            GenDeliverService[DeliverService]
            GenSettingsService[SettingsService]
            GenAdminService[AdminService]
        end
    end

    SearchView --> SearchToolbar
    SearchView --> SearchResultCard
    SearchView --> SelectionPanel
    LibraryView --> LibraryTable
    AdminView --> UserTable
    AdminView --> InviteUserForm

    SearchStore --> GenSearchService
    LibraryStore --> GenLibraryService
    AuthStore --> GenAuthService
    SettingsStore --> GenSettingsService
    AdminStore --> GenAdminService

    GenSearchService --> AxiosClient
    GenLibraryService --> AxiosClient
    GenAuthService --> AxiosClient
```
