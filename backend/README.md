# Backend — Telegram userbot auth + infra slice

Kotlin/Spring Boot backend. Covers: Postgres + Docker infra, the one-time TDLib (userbot)
login handshake with headless session reuse across restarts, a background listener that
mirrors incoming Telegram messages into Postgres, and a first HTTP endpoint to read them back.

## Prerequisites

- Docker Desktop running.
- A Telegram `api_id` / `api_hash` pair from https://my.telegram.org (API development tools).
  Bot tokens don't work here — this logs in as a real user account (needed to read private
  messages sent to you directly, which the Bot API can't see).
- The phone number of the account to log in as, plus access to receive the login code and
  your 2FA password if you have one set.

## First-time setup

1. Copy the env template and fill in real values:
   ```bash
   cp .env.example .env
   ```
   Fill in `POSTGRES_PASSWORD` (any value), `TELEGRAM_API_ID`, `TELEGRAM_API_HASH`.

2. Start Postgres:
   ```bash
   docker compose up -d postgres
   ```

3. Run the one-time interactive login (needs a real terminal — prompts for phone number,
   login code, and 2FA password if enabled):
   ```bash
   docker compose run --rm -it -e SPRING_PROFILES_ACTIVE=auth-cli app \
     bootRun --console=plain
   ```
   Note: no leading `./gradlew` here — the `dev` image's `ENTRYPOINT` already is
   `./gradlew`, so anything after the service name is passed as *arguments to* it, not a
   replacement command. Repeating `./gradlew` makes Gradle try (and fail) to find a task
   literally named `./gradlew`.

   The command override matters here: the default `bootRun --continuous` is for the dev
   loop, not a one-shot interactive login, and `--console=plain` avoids Gradle's rich
   progress-bar UI visually interleaving with the phone/code/2FA prompts (both cosmetic
   fixes). The important fix is already in `build.gradle.kts` — Gradle's `bootRun` task
   doesn't forward the real terminal's stdin to the JVM by default, which without that fix
   makes the prompts print but never receive what you type (looks like a stuck progress bar).

   When asked `[token/phone/qr]`, type **phone** (qr requires scanning from another already
   logged-in device, and isn't wired up here).
   On success it prints `Authenticated as <name> (id=<telegram_user_id>)` and exits 0. The
   session is persisted in the `tdlib_data` Docker volume — this step should only be needed
   once (see "Resetting" below for when you'd need to redo it).

## Normal startup

```bash
docker compose up -d
```

```
docker compose up -d --build app 
```

Starts Postgres + the app headlessly, reusing the persisted session — no prompts. First run
compiles inside the container (downloads the Gradle distribution, compiles Kotlin), so it
takes a minute or two; watch it with:

```bash
docker compose logs -f app
```

Expect a log line confirming the restored session (`Telegram session restored, authorization
ready` / `Confirmed session for ...`). If instead you see "No Telegram session found", the
one-time login step above hasn't succeeded yet.

## Reading messages

```bash
curl "http://localhost:8080/api/messages/recent?limit=50"
```

Returns the last N messages (default 50, across all chats), grouped by chat type
(`private`/`group`/`channel`) then by author within each group, newest first. Content is
plain text only for now — non-text messages (photos, etc.) show up as a `(MessageXxx)`
placeholder; rich formatting/media is a later pass. There's no read/unread tracking yet
(the `is_seen` column exists in the schema but isn't used by this endpoint) and **no auth**
on it — fine while it's only reachable from localhost, not once it's exposed further.

## Routine pause/resume

Prefer `stop`/`start` over `down`/`up` for day-to-day use — it keeps the same containers
instead of destroying and recreating them:

```bash
docker compose stop     # pause everything, containers kept
docker compose start    # resume the same containers
```

Reserve `docker compose down` for actually tearing down the setup (e.g. after a Dockerfile
change). Named volumes (`postgres_data`, `tdlib_data`, `gradle-cache`) survive `down` unless
you pass `-v` — don't pass `-v` unless you intend to lose the Telegram session and/or the DB.

**Note on the project name:** this compose file sets `name: durka-backend` explicitly. Don't
run bare `docker compose -p backend ...` (or anything that overrides the project name) against
this directory — `backend` is a generic name that has collided with an unrelated project on
this machine before.

## Verifying it worked

```bash
# bookkeeping row (not the credential itself - see below)
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT phone_number, telegram_user_id, authorization_state, last_verified_at FROM telegram_session_status;"

# confirm the TDLib session was actually persisted to the volume
docker compose run --rm app ls -la /data/tdlib

# prove a restart doesn't require re-login
docker compose restart app
docker compose logs -f app
```

## Where the session actually lives

The real Telegram session (what lets restarts skip phone/code/2FA) is **only** in TDLib's own
data directory, on the `tdlib_data` named volume. The `telegram_session_status` Postgres table
is bookkeeping/observability only (phone number, Telegram user id, last-verified time) — it is
never the credential store. Concretely:

- Delete the Postgres row but keep `tdlib_data` → headless startup refuses to even try (fails
  fast on the missing bookkeeping row), even though the TDLib session on disk is still valid.
- Delete the `tdlib_data` volume but keep the Postgres row → it'll attempt to resume, but TDLib
  comes up unauthenticated and fails fast immediately instead of hanging on a prompt.

There is currently no encryption key protecting that volume's contents at the TDLib layer
(`tdlight-java`'s `TDLibSettings` doesn't expose one) — treat the volume as sensitive, on par
with a full account credential.

## Resetting (re-doing the login)

Only needed if the session becomes invalid (e.g. logged out from another device, or you want
to switch accounts):

```bash
docker compose down
docker volume rm durka-backend_tdlib_data
docker compose up -d postgres
docker compose run --rm -it -e SPRING_PROFILES_ACTIVE=auth-cli app
```
