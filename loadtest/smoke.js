// Smoke test — run this FIRST, before any load test.
//
// One virtual user, one pass. It proves the deployed API is reachable, that a
// real login works, and that the endpoints the load test will hammer actually
// answer 200 with a token. If the smoke test is red, the load numbers would be
// meaningless — fix the smoke first.
//
//   BASE_URL=https://<ip>.sslip.io \
//   TEST_EMAIL=you@example.com \
//   TEST_PASSWORD='...' \
//   k6 run loadtest/smoke.js
//
// Credentials come from the environment, never from this file (it is committed).

import http from "k6/http";
import { check } from "k6";

const BASE = (__ENV.BASE_URL || "http://localhost:8001").replace(/\/+$/, "");
const EMAIL = __ENV.TEST_EMAIL;
const PASSWORD = __ENV.TEST_PASSWORD;

export const options = {
  vus: 1,
  iterations: 1,
  // A smoke run must be clean: any failed check fails the whole run.
  thresholds: {
    checks: ["rate==1.0"],
  },
};

export default function () {
  // 1. Liveness — no auth, no database.
  const health = http.get(`${BASE}/api/health`);
  check(health, { "health 200": (r) => r.status === 200 });

  // 2. Readiness — reports whether Supabase is actually reachable.
  const ready = http.get(`${BASE}/api/health/ready`);
  check(ready, { "ready answers": (r) => r.status === 200 || r.status === 503 });

  if (!EMAIL || !PASSWORD) {
    throw new Error("Set TEST_EMAIL and TEST_PASSWORD in the environment.");
  }

  // 3. Login — must return a token.
  const login = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" } },
  );
  const ok = check(login, {
    "login 200": (r) => r.status === 200,
    "login has token": (r) => !!r.json("token"),
  });
  if (!ok) {
    throw new Error(`Login failed (${login.status}): ${login.body}`);
  }
  const token = login.json("token");
  const auth = { headers: { Authorization: `Bearer ${token}` } };

  // 4. Authed identity round-trip.
  const me = http.get(`${BASE}/api/auth/me`, auth);
  check(me, { "me 200": (r) => r.status === 200 });

  // 5. The main read the load test leans on.
  const list = http.get(`${BASE}/api/students?page=0&size=25`, auth);
  check(list, {
    "students 200": (r) => r.status === 200,
    "students is a page": (r) => Array.isArray(r.json("content")),
  });

  // 6. A single student, if the page had any.
  const content = list.json("content");
  if (Array.isArray(content) && content.length > 0) {
    const id = content[0].id;
    const one = http.get(`${BASE}/api/students/${id}`, auth);
    check(one, { "student by id 200": (r) => r.status === 200 });
  }
}
