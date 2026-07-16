# Durka — HMD Barbie Phone Portal

Personal feed portal: a Kotlin/Spring backend mirrors Telegram messages, a Node/Express
frontend (BFF) content-negotiates between a JS-less legacy render (for Opera Mini on the
Barbie Phone) and a modern client. See [backend/README.md](backend/README.md) for the
backend's own setup/auth/security details — this file covers running both together.

Production deployment (`docker-compose.prod.yml` + `Caddyfile` at this root) is separate
from local dev — see that section at the bottom.

## Local development — start everything

Backend and frontend run as two separate Docker Compose projects (`durka-backend`,
`durka-frontend`), so a local frontend can reach a local backend before either is deployed
anywhere. Start the backend first:

```bash
cd backend
cp .env.example .env   # fill in real values - see backend/README.md for how
docker compose up -d postgres
docker compose run --rm -it -e SPRING_PROFILES_ACTIVE=auth-cli app bootRun --console=plain   # one-time Telegram login, first time only
docker compose up -d
```

Then the frontend:

```bash
cd ../frontend
cp .env.example .env   # FEED_API_TOKEN must be identical to backend/.env's value
docker compose up -d
```

Visit `http://localhost:3000/?token=<FEED_API_TOKEN>`.

## Restarting after code changes

Both dev setups bind-mount source and auto-reload (Gradle `--continuous` for the backend,
`ts-node-dev` for the frontend) — most source edits just take effect on save, no restart
needed. A few things do need one:

```bash
# backend: build.gradle.kts changed (new/changed dependency) - bind-mounted, no --build needed
cd backend && docker compose restart app

# frontend: package.json changed (new/changed npm dependency) - node_modules is a named
# volume populated at image build time, NOT bind-mounted, so this needs an actual rebuild
cd frontend && docker compose up -d --build app

# either service acting weird / want a clean slate without losing data (DB, TDLib session)
docker compose restart app          # or: docker compose stop app && docker compose start app

# tail logs
docker compose logs -f app
```

Full teardown/rebuild (rarely needed — loses nothing in named volumes, but recreates
containers from scratch):
```bash
docker compose down
docker compose up -d --build
```

## Production deployment

See `docker-compose.prod.yml` (merged backend + frontend + Caddy, single shared network,
only Caddy has published ports) and `Caddyfile` at this root. Copy `.env.example` to `.env`
here too and fill in fresh secrets — don't reuse local dev values. Bring up in the same
order (`postgres` → one-time `auth-cli` → everything else):

```bash
docker compose -f docker-compose.prod.yml up -d postgres
docker compose -f docker-compose.prod.yml run --rm -it -e SPRING_PROFILES_ACTIVE=auth-cli backend
docker compose -f docker-compose.prod.yml up -d
```

After a `git pull` on the server, redeploy code changes with:
```bash
docker compose -f docker-compose.prod.yml up -d --build
```
(rebuilds whatever changed, recreates only the affected containers — safe to run even when
unsure what changed, since `postgres`/`caddy` use fixed upstream images untouched by this).
