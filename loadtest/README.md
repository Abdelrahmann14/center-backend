# Load testing the Center API with k6

Two scripts:

- **`smoke.js`** — one user, one pass. Proves the API is up, login works, and
  the endpoints answer 200. Run this first, every time.
- **`load.js`** — a read-heavy ramp that mimics real use (browse students, open
  a student, check identity), with latency and error-rate thresholds.

Credentials and the target URL come from **environment variables**, never from
these files. The files are committed; secrets are not.

## The one rule that matters

**Never load-test against the production database.** Point `BASE_URL` at a
deployment wired to a **separate, throwaway Supabase project** seeded with fake
students. Reasons:

1. A load test spends database CPU and connections. On the production Supabase
   that competes with real teachers using the system right now.
2. Even though `load.js` is read-only (it never creates rows), a stress or spike
   run can exhaust the connection pool and make the live app fail.

Recommended setup for a real test:

1. Create a second Supabase project (free tier is fine for the test itself).
2. Run the API a second time pointed at it (locally, or a second cheap box),
   with its own `.env` holding the test `DB_*`.
3. Seed it with fake students (a few thousand is plenty to be realistic — the
   query plans are the same at 5k as at 50k once the indexes are in).
4. Point `BASE_URL` at that instance and run k6 from your laptop.

## Install k6

- Windows: `winget install k6` (or `choco install k6`)
- macOS: `brew install k6`
- Linux: see grafana.com/docs/k6

## Run

Smoke first:

```bash
BASE_URL=https://<ip>.sslip.io \
TEST_EMAIL=you@example.com \
TEST_PASSWORD='your-password' \
k6 run loadtest/smoke.js
```

Then load. Start gentle and climb across separate runs — do not open at 200.

```bash
# Warm-up: 20 users
BASE_URL=... TEST_EMAIL=... TEST_PASSWORD='...' \
PEAK_VUS=20 HOLD=1m k6 run loadtest/load.js

# Realistic peak: 50 users
BASE_URL=... TEST_EMAIL=... TEST_PASSWORD='...' \
PEAK_VUS=50 HOLD=2m k6 run loadtest/load.js

# Find the ceiling: push until thresholds go red
BASE_URL=... TEST_EMAIL=... TEST_PASSWORD='...' \
PEAK_VUS=200 HOLD=2m k6 run loadtest/load.js
```

`PEAK_VUS` is concurrent virtual users, not total accounts. 50 VUs pausing
0.5–2.5s between requests is a much heavier stream than 50 humans clicking, so
50 VUs already over-represents "50 teachers online at once."

## Reading the result

k6 prints a summary at the end. The lines that matter:

| Line | Meaning | Watch for |
| --- | --- | --- |
| `http_req_failed` | share of requests that errored | must stay `< 1%` |
| `http_req_duration ... p(95)` | 95th-percentile latency | rising fast = a bottleneck |
| `t_students_list p(95)` | the heavy query alone | the first thing to slow down |
| `iterations` / `http_reqs` | throughput | requests per second sustained |

If `http_req_duration` climbs while CPU on the box stays low, the bottleneck is
the **database**, not the server — the requests are waiting on Supabase, which
is the expected first limit and the reason the API and DB should share a region.

## Interpreting for capacity

- Thresholds green at `PEAK_VUS=50` → comfortably handles ~50 teachers online.
- Green at 100–200 → large headroom on this box.
- To grow the server: resize the Hetzner instance (CX23 → CX33) — no code
  change. To grow the database: a bigger Supabase compute tier. The database is
  almost always the binding limit first.
