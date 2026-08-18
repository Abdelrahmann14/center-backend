// Write/sync load test — exercises the offline write path under concurrency.
//
// Each iteration POSTs a batch of student "upsert" mutations to /api/sync/push,
// the exact endpoint the offline engine uses. Every mutation runs in its own
// REQUIRES_NEW transaction server-side and flows through studentService.upsert —
// the assigned-id insert path that the earlier bug hunt fixed. So this is the
// load test for that fix: does the write path stay correct and fast when many
// clients flush their offline queues at once.
//
//   BASE_URL=https://<ip>.sslip.io \
//   TEST_EMAIL=loadassist@center.user.com \
//   TEST_PASSWORD='...' \
//   PEAK_VUS=25 BATCH=10 HOLD=1m \
//   k6 run loadtest/sync-write.js
//
// The push endpoint requires an ASSISTANT (role USER) or STUDENT — an admin
// token is rejected. Every student created is tagged notes="LOADTEST_SEED" so
// cleanup is a single precise DELETE.

import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Counter } from "k6/metrics";

const BASE = (__ENV.BASE_URL || "http://localhost:8001").replace(/\/+$/, "");
const EMAIL = __ENV.TEST_EMAIL;
const PASSWORD = __ENV.TEST_PASSWORD;
const PEAK_VUS = parseInt(__ENV.PEAK_VUS || "25", 10);
const BATCH = parseInt(__ENV.BATCH || "10", 10);
const HOLD = __ENV.HOLD || "1m";
const RAMP = __ENV.RAMP || "20s";

const pushTrend = new Trend("t_sync_push", true);
const appliedC = new Counter("mutations_applied");
const rejectedC = new Counter("mutations_rejected");

export const options = {
  scenarios: {
    ramp: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: RAMP, target: PEAK_VUS },
        { duration: HOLD, target: PEAK_VUS },
        { duration: RAMP, target: 0 },
      ],
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    // The whole point: not one write may be rejected or conflict.
    mutations_rejected: ["count<1"],
  },
};

function uuid() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function phone(prefix) {
  return prefix + Math.floor(10000000 + Math.random() * 89999999); // 3 + 8 = 11 digits
}

// A student's name must be unique within the tenant (hard duplicate check) AND
// pass @ArabicName (Arabic letters and single spaces only — no digits). Three
// random Arabic-letter words make collisions vanishingly unlikely across the
// whole run while staying valid.
const AR = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي".split("");
function arWord(n) {
  let s = "";
  for (let i = 0; i < n; i++) s += AR[(Math.random() * AR.length) | 0];
  return s;
}
function arName() {
  return `${arWord(5)} ${arWord(6)} ${arWord(4)}`;
}

export function setup() {
  if (!EMAIL || !PASSWORD) throw new Error("Set TEST_EMAIL and TEST_PASSWORD.");
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" } },
  );
  check(res, { "login 200": (r) => r.status === 200 });
  const token = res.json("token");
  if (!token) throw new Error(`login failed ${res.status}: ${res.body}`);
  return { token };
}

export default function (data) {
  const auth = {
    headers: { Authorization: `Bearer ${data.token}`, "Content-Type": "application/json" },
  };

  const mutations = [];
  for (let i = 0; i < BATCH; i++) {
    mutations.push({
      mutationId: uuid(),
      entity: "student",
      op: "upsert",
      rowId: uuid(),
      baseVersion: 0,
      payload: {
        name: arName(),
        grade: "الصف الأول الثانوي",
        student_phones: [phone("010")],
        parent_phones: [phone("011")],
        notes: "LOADTEST_SEED",
        allow_duplicate_phone: true,
      },
      queuedAt: "2026-08-18T12:00:00.000Z",
    });
  }

  const res = http.post(`${BASE}/api/sync/push`, JSON.stringify({ mutations }), auth);
  pushTrend.add(res.timings.duration);
  const ok = check(res, { "push 200": (r) => r.status === 200 });

  if (ok) {
    const results = res.json("results") || [];
    let applied = 0;
    let rejected = 0;
    for (const r of results) {
      if (r.outcome === "applied") applied++;
      else if (r.outcome === "rejected" || r.outcome === "conflict") rejected++;
    }
    appliedC.add(applied);
    rejectedC.add(rejected);
    check(res, { "no rejects": () => rejected === 0 });
  }

  sleep(Math.random() * 1.5 + 0.5);
}
