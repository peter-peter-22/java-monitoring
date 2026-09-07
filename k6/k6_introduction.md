# Explain k6 Concepts

**User:** Anonymous  
**Created:** 9/1/2026 17:54:12  
**Updated:** 9/1/2026 18:01:48  
**Exported:** 9/1/2026 18:25:09  

## Prompt:
Explain the basic concepts and abilities of the k6 load testing tool.
List the typical workflows.
Start from high level summary, then elaborate on the important details and show examples.

## Response:
I’ll cover k6 from the mental model first, then the core primitives, test types, metrics/checks, thresholds, scenarios, and a few realistic workflows.

# k6 in one mental model

**k6 is a programmable load generator.** You write a JavaScript test describing what a virtual user should do, tell k6 how many users or requests to generate, and k6 records how the system responds.

The basic loop is:

```text
                    k6
                     │
          generate synthetic traffic
                     │
                     ▼
       ┌─────────────────────────┐
       │  Your application       │
       │  API / web server / DB  │
       └─────────────────────────┘
                     │
                     ▼
              responses/errors
                     │
                     ▼
             k6 metrics
                     │
             ┌───────┴────────┐
             ▼                ▼
        pass/fail         analysis
        thresholds       Grafana/etc.
```

A typical k6 test answers questions such as:

> "Can my application handle 200 concurrent users while keeping p95 latency below 500 ms and errors below 1%?"

k6 is particularly good at **HTTP/API and protocol-level load testing**, while it also has browser-testing capabilities for Chromium-based browser interactions. 

---

# 1. The core concepts

There are six concepts worth understanding first:

| Concept | Meaning |
|---|---|
| **VU** | Virtual User; one concurrent execution of your test code |
| **Iteration** | One complete execution of the VU's test function |
| **Scenario** | Defines how load is scheduled |
| **Request** | HTTP/API operation made by the test |
| **Metric** | Measurement collected during the test |
| **Threshold** | Pass/fail criterion for a metric |

The most important distinction is:

**What the user does** and **how much traffic is generated** are separate things.

For example:

```javascript
export default function () {
    http.get('http://localhost:8080/posts');
    http.get('http://localhost:8080/posts/42');
}
```

This describes the **behavior**.

Then:

```javascript
export const options = {
    vus: 50,
    duration: '2m',
};
```

describes approximately **50 concurrent virtual users for two minutes**.

k6's scenario system gives you much finer control over this scheduling. 

---

# 2. Virtual users

A **VU** is an execution context that repeatedly runs your test function.

Suppose:

```javascript
export default function () {
    http.get('http://localhost:8080/posts');
}
```

and:

```javascript
export const options = {
    vus: 10,
    duration: '30s',
};
```

Conceptually:

```text
VU 1 ── GET /posts ── GET /posts ── GET /posts ── ...
VU 2 ── GET /posts ── GET /posts ── GET /posts ── ...
VU 3 ── GET /posts ── GET /posts ── GET /posts ── ...
...
VU 10 ─ GET /posts ── GET /posts ── GET /posts ── ...
```

A VU isn't necessarily equivalent to a real browser tab or a real person.

For a protocol-level HTTP test, a VU is better thought of as:

> **one concurrent worker repeatedly executing my user-flow code**

This distinction becomes important when interpreting "100 users." One hundred k6 VUs making lightweight HTTP requests is not necessarily equivalent to 100 humans using a browser.

---

# 3. Iterations

An **iteration** is one execution of the VU function.

For example:

```javascript
export default function () {
    http.get('/posts');
    http.get('/posts/42');
    http.post('/posts', ...);
}
```

One iteration means:

```text
GET /posts
    ↓
GET /posts/42
    ↓
POST /posts
    ↓
iteration finished
```

Then the VU can start another iteration.

This is useful because you can think of your test as:

> "A user performs this sequence once, then starts over."

There are also executors where k6 explicitly controls **iteration counts** rather than just VU counts or duration. 

---

# 4. Scenarios and executors

This is probably the most important k6 concept after VUs.

A **scenario describes the workload model**.

For example:

```javascript
export const options = {
    scenarios: {
        users: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m', target: 20 },
                { duration: '2m', target: 100 },
                { duration: '1m', target: 0 },
            ],
        },
    },
};
```

This says:

```text
0 users
   │
   │ ramp for 1 min
   ▼
20 users
   │
   │ ramp for 2 min
   ▼
100 users
   │
   │ ramp down for 1 min
   ▼
0
```

k6 has several executor types for different workload models, including:

- fixed/shared iteration counts
- fixed VU counts
- ramping VUs
- constant arrival rates
- ramping arrival rates

The latter are particularly important because they let you model **request/iteration arrival rate** rather than simply maintaining a number of active VUs. 

---

# 5. Closed vs open load models

This distinction is subtle but extremely important.

## Closed model

With a VU-based workload:

```text
VU
 │
 ├── request
 │
 │   wait for response
 │
 ├── request
 │
 │   wait for response
 │
 └── request
```

If the server becomes slower, the VU also becomes slower.

Therefore:

> **server slowdown reduces the request arrival rate.**

This is a **closed model**.

For example, 100 VUs each repeatedly performing an operation does not necessarily mean 1,000 requests/sec.

It depends on how quickly the application completes those operations.

---

## Open model

Arrival-rate executors instead say approximately:

> "Start 100 iterations per second."

The generator tries to maintain that arrival rate independently of how quickly individual iterations finish.

This is useful when you care about the application's behavior under a specified incoming traffic rate.

k6 explicitly distinguishes these open and closed workload models. 

---

# 6. HTTP requests

For backend testing, this is where k6 is especially straightforward.

```javascript
import http from 'k6/http';

export default function () {
    http.get('http://localhost:8080/posts');
}
```

You can make the usual HTTP operations:

```javascript
http.get(...)
http.post(...)
http.put(...)
http.patch(...)
http.delete(...)
http.head(...)
http.options(...)
http.request(...)
```

and issue multiple requests in parallel with `http.batch()`. 

A more realistic login example:

```javascript
import http from 'k6/http';

export default function () {
    const response = http.post(
        'http://localhost:8080/login',
        {
            username: 'alice',
            password: 'secret'
        }
    );

    console.log(response.status);
}
```

You can also maintain cookies, headers, authentication, query parameters, JSON payloads, etc.

---

# 7. Checks

A load test shouldn't merely ask:

> "How long did the request take?"

It should also ask:

> "Did I get the correct result?"

k6 provides **checks** for this.

```javascript
import http from 'k6/http';
import { check } from 'k6';

export default function () {
    const response = http.get('http://localhost:8080/posts');

    check(response, {
        'status is 200': r => r.status === 200,
        'response is not empty': r => r.body.length > 0,
    });
}
```

This allows you to distinguish:

```text
200 OK, 20 ms
```

from:

```text
500 Internal Server Error, 5 ms
```

Both are fast, but only one is useful.

Checks produce a success rate metric. 

---

# 8. Metrics

k6 automatically records metrics while the test runs.

Some particularly important built-in metrics include:

```text
http_req_duration
http_req_failed
iterations
```

There are many others covering request phases, data transferred, iteration duration, VUs, dropped iterations, etc. 

The most important HTTP metric is usually:

```text
http_req_duration
```

which measures request duration.

For example, a result might effectively tell you:

```text
http_req_duration

avg    180 ms
med    120 ms
p90    310 ms
p95    420 ms
p99    900 ms
max    2.4 s
```

The **percentiles are often more useful than the average**.

For example:

```text
average = 200 ms
p95     = 900 ms
```

means the average looks acceptable while a significant tail of requests is considerably slower.

k6's summaries include common statistics such as median and percentile values. 

---

# 9. Custom metrics

You aren't restricted to k6's built-in measurements.

You can define your own:

```javascript
import { Trend, Counter, Rate } from 'k6/metrics';

const dbTime = new Trend('db_time');
const errors = new Counter('application_errors');
const successfulLogins = new Rate('successful_logins');
```

Then record values:

```javascript
dbTime.add(37);
errors.add(1);
successfulLogins.add(true);
```

k6 provides four fundamental metric types:

| Type | Meaning |
|---|---|
| **Counter** | accumulated total |
| **Gauge** | current/latest-type value |
| **Rate** | proportion of successful/non-zero events |
| **Trend** | distribution of measurements |

Trends are especially useful for latency-like data because you can calculate percentiles from them. 

---

# 10. Thresholds

This is where a k6 script becomes a **performance test rather than merely a traffic generator**.

A threshold defines what constitutes success.

For example:

```javascript
export const options = {
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
};
```

Meaning:

```text
95th percentile latency < 500 ms
AND
error rate < 1%
```

Then CI can effectively say:

```text
Test passed ✅
```

or:

```text
Test failed ❌
```

This makes thresholds ideal for performance regression testing.

For example:

```javascript
thresholds: {
    http_req_duration: ['p(95)<400', 'p(99)<1000'],
    checks: ['rate>0.99'],
}
```

Now your test has explicit performance requirements.

k6 exposes thresholds as a first-class option and can use them to determine whether the test succeeds. 

---

# 11. Tags

Tags let you divide your measurements into useful categories.

Suppose you have:

```text
GET /posts
GET /users
GET /comments
```

You may want separate latency statistics for each.

k6 automatically applies tags to HTTP requests, and you can also add your own tags. 

For example:

```javascript
http.get(
    'http://localhost:8080/posts',
    {
        tags: {
            endpoint: 'list-posts'
        }
    }
);
```

This becomes very useful once your test starts generating thousands or millions of measurements.

---

# 12. Test lifecycle

k6 scripts have four conceptual phases:

```text
init
  ↓
setup()
  ↓
VU code
  ↓
teardown()
```

For example:

```javascript
import http from 'k6/http';

export function setup() {
    return {
        token: getTestToken()
    };
}

export default function (data) {
    http.get('http://localhost:8080/posts', {
        headers: {
            Authorization: `Bearer ${data.token}`
        }
    });
}

export function teardown(data) {
    // cleanup
}
```

`setup()` executes once for the test, while the VU function executes repeatedly according to the workload configuration. `teardown()` runs afterward. 

This is useful for things such as:

```text
setup:
    create test users
    obtain authentication token
    prepare test data

VU:
    perform realistic workload

teardown:
    delete temporary resources
```

---

# 13. Browser testing

k6 is primarily interesting for your backend because you can test the HTTP interface directly.

But k6 also supports browser-level testing using Chromium.

A browser test can do things such as:

```text
open page
    ↓
find button
    ↓
click
    ↓
wait for navigation
    ↓
inspect page
```

For example, browser tests use asynchronous operations:

```javascript
import { browser } from 'k6/browser';

export default async function () {
    const page = await browser.newPage();

    await page.goto('http://localhost:8080/');

    await page.locator('text=Login').click();

    await page.close();
}
```

The browser functionality uses Chromium and supports locators and normal browser interactions. 

However, **don't automatically use browser tests for everything**.

For backend load testing, protocol-level HTTP tests are generally much cheaper and easier to scale.

A browser test models substantially more of the real frontend stack.

---

# 14. The typical workflow

A realistic k6 workflow usually looks like this.

## Workflow 1: Basic smoke/performance test

Start with:

```text
write script
    ↓
1-5 VUs
    ↓
verify application works
    ↓
inspect latency/errors
```

Example:

```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 2,
    duration: '30s',
};

export default function () {
    const res = http.get('http://localhost:8080/posts');

    check(res, {
        'status 200': r => r.status === 200,
    });
}
```

This is not really a stress test yet.

It's primarily:

> "Does my workload script work, and does the application behave correctly?"

---

# 15. Workflow 2: Load test

Then determine the expected production load.

For example:

```text
ramp to 20 users
        ↓
ramp to 50
        ↓
ramp to 100
        ↓
hold 100
        ↓
ramp down
```

```javascript
export const options = {
    scenarios: {
        load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '2m', target: 20 },
                { duration: '2m', target: 50 },
                { duration: '5m', target: 100 },
                { duration: '2m', target: 0 },
            ],
        },
    },
};
```

Then observe:

```text
latency
error rate
throughput
application CPU
application memory
database CPU
database connections
```

The key point is that **k6 measures the client-facing behavior; it does not automatically tell you why the system is slow**.

That's where your application's monitoring stack comes in.

---

# 16. Workflow 3: Stress test

A stress test deliberately pushes beyond the expected workload.

For example:

```text
100 users
   ↓
200
   ↓
300
   ↓
500
   ↓
800
   ↓
1000
```

You're looking for:

> **At what point does the system stop satisfying its requirements?**

You might discover:

```text
100 VUs   p95 = 220 ms
200 VUs   p95 = 310 ms
300 VUs   p95 = 490 ms
400 VUs   p95 = 1.2 s
500 VUs   p95 = 3.8 s
```

This tells you much more than simply saying:

> "The server works with 100 users."

It reveals the **capacity curve**.

---

# 17. Workflow 4: Spike test

A spike deliberately changes the traffic very quickly:

```text
        ┌─────────────
        │             │
────────┘             └──────
        ↑
     huge spike
```

For example:

```text
20 VUs
   ↓
500 VUs almost immediately
```

This asks:

> "How does the system behave when traffic suddenly increases?"

This can reveal:

- connection-pool exhaustion
- autoscaling delays
- queue buildup
- cache warm-up effects
- database overload
- thread-pool exhaustion

---

# 18. Workflow 5: Soak/endurance test

Instead of increasing the load, keep it stable for a long period:

```text
100 VUs
──────────────────────────────
      2 hours
```

This is useful for detecting things that don't show up in a 5-minute test:

```text
memory leaks
connection leaks
resource exhaustion
GC problems
gradual performance degradation
database problems
```

For example:

```text
0 min      p95 = 250 ms
30 min     p95 = 270 ms
60 min     p95 = 300 ms
90 min     p95 = 450 ms
120 min    p95 = 900 ms
```

That pattern is much more interesting than the result of a short test.

---

# 19. Workflow 6: CI performance regression test

This is arguably one of the most useful applications.

Suppose your application normally achieves:

```text
p95 < 500 ms
error rate < 1%
```

Put that directly into the test:

```javascript
export const options = {
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
};
```

Then your CI pipeline can run k6 after a change:

```text
Git commit
   ↓
build
   ↓
start application
   ↓
start database
   ↓
run k6
   ↓
thresholds
   ↓
PASS / FAIL
```

This catches performance regressions automatically.

---

# 20. A more realistic example

For the kind of Spring application you described previously, you might have:

```text
User login
   ↓
browse homepage
   ↓
browse posts
   ↓
open post
   ↓
write comment
```

A k6 script could model that:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    scenarios: {
        users: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m', target: 20 },
                { duration: '3m', target: 100 },
                { duration: '1m', target: 0 },
            ],
        },
    },

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
};

export default function () {
    let response;

    response = http.get('http://localhost:8080/');
    check(response, {
        'homepage OK': r => r.status === 200,
    });

    sleep(1);

    response = http.get('http://localhost:8080/posts');
    check(response, {
        'posts OK': r => r.status === 200,
    });

    sleep(1);

    response = http.get('http://localhost:8080/posts/42');
    check(response, {
        'post OK': r => r.status === 200,
    });

    sleep(2);
}
```

Now you've created a crude model of a user browsing the application.

The `sleep()` calls matter because otherwise the test behaves more like:

> "A machine repeatedly hammering the endpoints as fast as possible"

rather than:

> "A user spends some time reading between requests."

You would normally make the workflow considerably more realistic than this.

---

# 21. What k6 does **not** do

This is an important boundary.

k6 is primarily a **load generator and measurement tool**.

It does not automatically provide complete application observability.

For example, suppose this happens:

```text
k6:
p95 latency = 2 seconds
```

k6 tells you that the client waited two seconds.

It doesn't inherently answer:

```text
Was that caused by:
    Java GC?
    PostgreSQL?
    database lock?
    slow SQL?
    connection pool?
    CPU saturation?
    network?
    Thymeleaf rendering?
    application thread pool?
```

You need application-side observability for that.

A useful architecture is therefore:

```text
                         ┌─────────────┐
                         │     k6      │
                         │ load client │
                         └──────┬──────┘
                                │
                                ▼
                         Spring Boot
                                │
                  ┌─────────────┼──────────────┐
                  ▼             ▼              ▼
                JVM          PostgreSQL      HTTP
              metrics          metrics       metrics
                  │             │              │
                  └─────────────┼──────────────┘
                                ▼
                       monitoring system
```

That's especially useful when you're trying to understand *why* performance changes rather than merely detecting that it changed.

k6 can stream granular timestamped metrics to external systems as well as produce an end-of-test summary. 

---

# 22. The important distinction: load generation vs monitoring

I'd recommend keeping these concepts separate:

### k6

Answers:

> "What happens to the application when I send this workload?"

### Application monitoring

Answers:

> "What is the application doing internally while that workload happens?"

### Database monitoring

Answers:

> "What is PostgreSQL doing under that workload?"

Together:

```text
                 workload
                    │
                    ▼
                   k6
                    │
             ┌──────┴───────┐
             ▼              ▼
       application       database
        metrics           metrics
             │              │
             └──────┬───────┘
                    ▼
               diagnosis
```

That combination is considerably more powerful than treating k6 as a complete monitoring solution.

---

# 23. The mental model I'd use

You can reduce most k6 usage to four questions:

### 1. What does a user do?

Your JavaScript:

```javascript
export default function () {
    ...
}
```

### 2. How many users/requests do I want?

Your scenario/executor:

```javascript
executor: 'ramping-vus'
```

or:

```javascript
executor: 'constant-arrival-rate'
```

### 3. What do I measure?

Built-in and custom metrics:

```text
http_req_duration
http_req_failed
checks
custom Trend
custom Counter
...
```

### 4. When do I consider the test successful?

Thresholds:

```javascript
thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
}
```

Everything else is largely refinement around those four concepts.

---

# 24. A practical progression for learning k6

I'd learn it in this order:

```text
1. GET/POST requests
        ↓
2. VUs + duration
        ↓
3. iterations
        ↓
4. checks
        ↓
5. metrics
        ↓
6. thresholds
        ↓
7. realistic user workflows
        ↓
8. scenarios/executors
        ↓
9. arrival-rate testing
        ↓
10. stress/spike/soak testing
        ↓
11. external metric storage
        ↓
12. CI integration
        ↓
13. browser testing when actually needed
```

The biggest conceptual jump is **scenarios/executors and the difference between VU-based and arrival-rate workloads**. Once that makes sense, most of k6 becomes fairly straightforward. 

For your Spring Boot learning project, a particularly useful next step would be to build **one k6 script that models several blog-user behaviors and simultaneously observe k6 latency, Spring/JVM metrics, and PostgreSQL query performance**.



---
Powered by [ChatGPT Exporter](https://www.chatgptexporter.com)