# Deploying BookSearch v2 with Portainer

## Adding the Stack from GitHub

1. Open Portainer and navigate to **Stacks** in the left sidebar.
2. Click **Add stack**.
3. Select **Repository** as the build method.
4. Fill in the repository details:
   - **Repository URL**: `https://github.com/muszkin/booksearch-v2`
   - **Reference**: `refs/heads/main`
   - **Compose path**: `docker-compose.yml`
5. Scroll down to **Environment variables** and add the required variables (see below).
6. Click **Deploy the stack**.

## Required Environment Variables

Set these in the Portainer stack environment section:

| Variable | Example | Notes |
|----------|---------|-------|
| `JWT_SECRET` | `a-random-64-char-string` | **Required.** Generate with `openssl rand -hex 32`. |
| `SERVER_PORT` | `8080` | Change if port 8080 is already in use. |
| `SOLVEARR_URL` | `http://flaresolverr:8191` | Default is correct when using the bundled FlareSolverr. |
| `SMTP_HOST` | `smtp.gmail.com` | For production, point to your real SMTP server. |
| `SMTP_PORT` | `587` | TLS port for most providers. |

All other variables have sensible defaults. See `.env.example` for the complete list.

## Volume Mapping for Data Persistence

The `docker-compose.yml` declares a named volume `booksearch-data` mapped to `/app/data` inside the container. This stores:

- `booksearch.db` — the SQLite database (users, library, settings)
- `library/` — downloaded book files organized by user

In Portainer, the named volume is created automatically. To use a specific host path instead:

1. In the stack editor, change the volumes section:
   ```yaml
   volumes:
     booksearch-data:
       driver: local
       driver_opts:
         type: none
         o: bind
         device: /path/on/host/booksearch-data
   ```
2. Ensure the directory exists and is writable by UID 1000 (the `app` user inside the container):
   ```bash
   mkdir -p /path/on/host/booksearch-data
   chown 1000:1000 /path/on/host/booksearch-data
   ```

## Updating the Stack

1. Go to **Stacks** > **booksearch-v2**.
2. Click **Pull and redeploy**.
3. Portainer pulls the latest `docker-compose.yml` from the repository, rebuilds the images, and restarts the services.

The SQLite database and library files persist across redeployments through the named volume.

## Production Considerations

- Remove the `mailpit` service from the compose file (or simply do not expose its ports) and set `SMTP_HOST` / `SMTP_PORT` to your real mail server.
- Set `FLARESOLVERR_LOG_LEVEL=warn` to reduce log noise.
- Place a reverse proxy (Traefik, Caddy, nginx) in front of the backend for TLS termination.
