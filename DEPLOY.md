# Deploying the Center API

The API is a single Spring Boot process. Its database is hosted separately
(Supabase), so the server itself is stateless: nothing on its disk needs to
survive a restart, and it can be rebuilt from this repository at any time.

Two things shape every hosting decision below:

1. **It must not sleep.** Four `@Scheduled` jobs run in the background — the
   WhatsApp delivery monitor and recheck, the Google contacts reconciler, and
   the external-effect outbox that finishes queued work once the line is back.
   A host that suspends the process when traffic stops does not save money
   here; it silently stops the automation.
2. **Latency to the database dominates.** A request makes several round trips
   to Supabase and only one to the browser. Host the API in the same region as
   the Supabase project, not the same region as the users.

## Before the first deploy

- **Rotate any credential that has ever been shared.** The Groq key that used
  to sit in `.env.example` is compromised and must be replaced at
  console.groq.com.
- Generate a real `JWT_SECRET` (32+ random characters). Changing it later logs
  every user out.
- Set `SPRING_PROFILES_ACTIVE=prod`. This disables Swagger and stops responses
  carrying stack traces.

## Environment variables

`.env` is for local development only — production never reads it. The host
injects these as real environment variables.

| Variable | Required | Notes |
| --- | --- | --- |
| `DB_HOST` | yes | Supabase **Session Pooler** host (IPv4-friendly) |
| `DB_PORT` | yes | `5432` |
| `DB_NAME` | yes | `postgres` |
| `DB_USER` | yes | `postgres.<project-ref>` |
| `DB_PASSWORD` | yes | raw, **not** URL-encoded |
| `JWT_SECRET` | yes | 32+ random chars; changing it invalidates all sessions |
| `SPRING_PROFILES_ACTIVE` | yes | `prod` |
| `CORS_ALLOWED_ORIGINS` | only if the frontend is on another origin | e.g. `https://center.vercel.app` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | for contacts sync | Google Cloud Console |
| `GOOGLE_REDIRECT_URI` | for contacts sync | the **frontend** URL, and it must match the OAuth client exactly |
| `GREEN_API_INSTANCE_ID` / `GREEN_API_TOKEN` | for WhatsApp | blank + `GREEN_API_ENABLED=false` logs codes instead of sending |
| `GROQ_API_KEY` | optional | blank turns AI message variants off |
| `PORT` | injected by the host | falls back to 8001 |

`JWT_TTL_HOURS`, the Hikari and scheduler sizes, and the HTTP timeouts all have
working defaults in `application.yml`. Leave them alone unless something
measured says otherwise.

## Where to host

The image is a plain Dockerfile, so anything that runs a container works.

**Free, and genuinely capable — Oracle Cloud Always Free.** An Ampere A1 ARM
instance (up to 4 cores / 24 GB RAM) costs nothing indefinitely and never
sleeps, which is the only free tier that actually fits the scheduled jobs. The
price is operational: it is a bare VM, so Docker, a reverse proxy for TLS
(Caddy is one command), and firewall rules are yours to set up. Capacity for A1
shapes is sometimes unavailable in busy regions — try a neighbouring one.

**Free, and easiest — Render free web service.** One `git push` and it builds
the Dockerfile. It suspends after 15 minutes of inactivity and takes the better
part of a minute to wake, and the scheduled jobs do not run while it sleeps.
Fine for showing the app to somebody; not fine as the real deployment.

**Cheapest paid that just works — Fly.io.** `fly.toml` in this repository is
ready: one always-on `shared-cpu-1x` machine with 1 GB RAM, roughly $5/month.
Deploys are `fly deploy` from this directory, secrets are `fly secrets set`,
and TLS is automatic.

**Best value paid — a Hetzner VPS.** A CAX11 (2 vCPU ARM, 4 GB RAM, 40 GB) is
around €4/month including the IPv4 address: several times the resources of a
$7 managed instance, in exchange for running the VM yourself — same work as the
Oracle path.

**Managed, more expensive — Railway or Render Starter.** Around $5–10/month for
git-push deploys and no VM to maintain. Render's Starter instance is 512 MB,
which is tight for a JVM plus PDF generation; prefer a plan with 1 GB.

Prices move; check before committing.

## What the server actually needs

| | |
| --- | --- |
| Minimum | 1 vCPU, 1 GB RAM, 10 GB disk |
| Comfortable | 2 vCPU, 2–4 GB RAM, 20 GB disk |
| Java | 21 (the image brings its own) |
| Disk | the JAR and its bundled fonts only — all data lives in Supabase |

RAM is the binding constraint, not CPU. 512 MB will boot but leaves nothing for
a PDF batch running alongside normal traffic. The container already caps the
heap at 75% of whatever limit it is given (`JAVA_TOOL_OPTIONS` in the
Dockerfile), so a bigger machine needs no JVM flags changed.

## Deploy

```bash
docker build -t center-api .
docker run --rm -p 8001:8001 --env-file .env center-api
```

On Fly:

```bash
fly launch --no-deploy --copy-config
fly secrets set DB_HOST=... DB_USER=... DB_PASSWORD=... JWT_SECRET=... SPRING_PROFILES_ACTIVE=prod
fly deploy
```

## After deploying

- `GET /api/health` must answer 200 (liveness — no database involved).
- `GET /api/health/ready` reports whether the database is actually reachable.
- Flyway runs pending migrations at boot. Watch the first boot's logs: a failed
  migration stops startup, which is the intended behaviour.
- Add the frontend's real URL to `CORS_ALLOWED_ORIGINS` and to the Google OAuth
  client's authorised redirect URIs, then redeploy.
