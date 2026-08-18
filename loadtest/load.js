// Load test — a read-heavy profile that mimics real day-to-day use.
//
// Run the smoke test first. Then:
//
//   BASE_URL=https://<ip>.sslip.io \
//   TEST_EMAIL=you@example.com \
//   TEST_PASSWORD='...' \
//   k6 run loadtest/load.js
//
// Shape the run without editing the file:
//   PEAK_VUS=100      target concurrent virtual users at the top of the ramp
//   RAMP=30s          time to climb to each new level
//   HOLD=2m           time held at peak
//   P95_MS=800        p95 latency the run is allowed before it fails
//
// SAFETY: this hits every endpoint it touches for real. Point BASE_URL at a
// deployment wired to a TEST Supabase project, not the production database —
// otherwise you are loading real data and burning real quota. This profile is
// read-only (no writes), so it will not create junk rows, but it still spends
// database CPU and connections.

import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

const BASE = (__ENV.BASE_URL || "http://localhost:8001").replace(/\/+$/, "");
const EMAIL = __ENV.TEST_EMAIL;
const PASSWORD = __ENV.TEST_PASSWORD;

const PEAK_VUS = parseInt(__ENV.PEAK_VUS || "50", 10);
const RAMP = __ENV.RAMP || "30s";
const HOLD = __ENV.HOLD || "2m";
const P95_MS = parseInt(__ENV.P95_MS || "800", 10);

// Per-endpoint latency, so a slow single-student read does not hide behind fast
// health checks in the aggregate.
const listTrend = new Trend("t_students_list", true);
const oneTrend = new Trend("t_student_by_id", true);
const meTrend = new Trend("t_me", true);

export const options = {
  scenarios: {
    ramp: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: RAMP, target: Math.ceil(PEAK_VUS / 2) },
        { duration: RAMP, target: PEAK_VUS },
        { duration: HOLD, target: PEAK_VUS },
        { duration: RAMP, target: 0 },
      ],
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    // Under 1% of requests may fail, and 95% must come back within P95_MS.
    http_req_failed: ["rate<0.01"],
    http_req_duration: [`p(95)<${P95_MS}`],
  },
};

// Runs once. Log in, keep the token, and grab a handful of real student ids for
// the "open a student" step. One login for the whole test keeps the login rate
// limiter (which counts failures per ip) out of the picture.
export function setup() {
  if (!EMAIL || !PASSWORD) {
    throw new Error("Set TEST_EMAIL and TEST_PASSWORD in the environment.");
  }
  const login = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(login, { "setup login 200": (r) => r.status === 200 });
  const token = login.json("token");
  if (!token) throw new Error(`Setup login failed (${login.status}): ${login.body}`);

  const auth = { headers: { Authorization: `Bearer ${token}` } };
  const list = http.get(`${BASE}/api/students?page=0&size=50`, auth);
  const content = list.json("content");
  const ids = Array.isArray(content) ? content.map((s) => s.id).filter(Boolean) : [];

  return { token, ids };
}

export default function (data) {
  const auth = { headers: { Authorization: `Bearer ${data.token}` } };

  // Weighted mix of what an assistant actually does: mostly browsing the
  // students list, sometimes opening one, occasionally re-checking identity.
  const roll = Math.random();

  if (roll < 0.65) {
    // Browse a page of the students list (the heaviest common query).
    const page = Math.floor(Math.random() * 5); // pages 0..4
    const res = http.get(`${BASE}/api/students?page=${page}&size=25`, auth, {
      tags: { name: "students_list" },
    });
    listTrend.add(res.timings.duration);
    check(res, { "list 200": (r) => r.status === 200 });
  } else if (roll < 0.9) {
    // Open one student.
    if (data.ids.length > 0) {
      const id = data.ids[Math.floor(Math.random() * data.ids.length)];
      const res = http.get(`${BASE}/api/students/${id}`, auth, {
        tags: { name: "student_by_id" },
      });
      oneTrend.add(res.timings.duration);
      check(res, { "one 200": (r) => r.status === 200 });
    }
  } else {
    // Session restore / identity check.
    const res = http.get(`${BASE}/api/auth/me`, auth, { tags: { name: "me" } });
    meTrend.add(res.timings.duration);
    check(res, { "me 200": (r) => r.status === 200 });
  }

  // Think time — real users pause between clicks. Without it every VU becomes a
  // tight loop and the numbers describe a hammer, not a workload.
  sleep(Math.random() * 2 + 0.5); // 0.5–2.5s
}
