/**
 * This script is for testing if the k6 container works.
 * It's an example from the k6 GH, it does not depend on the local project.
 * Compared to the original, this script is runs for a shorter duration.
 */

import http from "k6/http";
import { check, sleep } from "k6";

// Test configuration
export const options = {
  thresholds: {
    // Assert that 99% of requests finish within 3000ms.
    http_req_duration: ["p(99) < 3000"],
  },
  // Ramp the number of virtual users up and down
  stages: [
    { duration: "1s", target: 15 },
    { duration: "2s", target: 15 },
    { duration: "1s", target: 0 },
  ],
};

// Simulated user behavior
export default function () {
  let res = http.get("https://quickpizza.grafana.com");
  // Validate response status
  check(res, { "status was 200": (r) => r.status == 200 });
  sleep(1);
}