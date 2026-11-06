# Deployment on free hosting

The public demo costs nothing and needs no credit card:

| Part | Where | Why |
|---|---|---|
| Web app (static files) | **GitHub Pages** | always on, instant, built by `deploy-pages.yml` |
| API (Spring Boot, Docker image) | **Render** free web service | runs the same `backend/Dockerfile` as `docker compose` |
| PostgreSQL | **Neon** free | permanent free plan (1 GB), wakes in a few hundred milliseconds |
| Keep-warm and nightly reset | **GitHub Actions** (`demo-maintenance.yml`) | pings the API every 10 minutes, resets the demo data every night |

```
browser ──► https://<user>.github.io/roastbench/     (GitHub Pages, static)
   │
   └──► https://roastbench-api.onrender.com/api/...  (Render, Docker)  ──► Neon PostgreSQL
```

## Why the API is separate from the web app

Render's free web service has 512 MB of memory and a fraction of a CPU, and it **goes to sleep after 15 minutes without
traffic**; waking it takes up to about a minute for a JVM. The web app is static, so it always loads immediately, and its
`ApiGate` shows an explanation (and continues by itself) while the API wakes up. The keep-warm workflow pings the API
every 10 minutes so that in practice it rarely sleeps (one always-on free service fits inside Render's 750 free hours per
month).

## One-time setup

1. **Neon**: create a project, copy the connection details. The JDBC URL is
   `jdbc:postgresql://<host>/<database>?sslmode=require`, plus the user name and password.
2. **Render**: *New > Blueprint*, pick this repository (`render.yaml` describes the service). Enter
   `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` when asked. Render generates
   `ADMIN_TOKEN`; copy it from the service's environment tab.
3. **GitHub repository settings**:
   - *Pages*: source **GitHub Actions**.
   - *Actions > Variables*: `API_URL` = the Render URL, for example `https://roastbench-api.onrender.com`.
   - *Actions > Secrets*: `DEMO_ADMIN_TOKEN` = the `ADMIN_TOKEN` from Render.
4. Push to `main` (or run the *Deploy web app* workflow). The site appears at
   `https://<user>.github.io/roastbench/`.

Secrets live only in Render and GitHub; nothing sensitive is in the repository.

## Things to know

- The first request after a long idle period can take a minute. That is the price of free hosting.
- Neon stops its compute after 5 idle minutes and resumes on the next query; the API's connection pool reconnects by
  itself.
- GitHub disables scheduled workflows in a repository without activity for 60 days; run *Demo maintenance* manually (or
  push a commit) to re-enable it.
- The public API has no login. To keep the demo small it limits the data (500 roasts, 200 lots, 5 MB uploads) and the
  nightly reset restores the seed data.
- Free-tier limits change. The application does not depend on a particular host: any platform that runs a Docker image
  and offers PostgreSQL works with the same environment variables (see `docker-compose.yml`).
