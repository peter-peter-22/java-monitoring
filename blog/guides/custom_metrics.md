# Explain Micrometer Metric Types

**User:** Anonymous  
**Created:** 9/9/2026 11:38:26  
**Updated:** 9/9/2026 13:40:10  
**Exported:** 9/9/2026 17:09:41  

## Prompt:
List the types of custom metrics in the spring boot micrometer MeterRegistry.
Show minimal examples where relevant.
Explain their behavior in distributed systems and their optimization (eg., the gauge is reported per instance? how often the gauge and counter is reported? how to effectively track total row counts in the database?)
Summarize the best practices.

## Response:
I’ll separate the metric types by what they *mean* (state vs event totals vs distributions), then cover scrape/reporting behavior, multi-instance aggregation, and database row-count patterns.

## Micrometer custom metric types

Micrometer exposes these main meter primitives:

| Type | Measures | Typical use |
|---|---|---|
| `Counter` | Monotonically increasing events | Requests, jobs completed, errors |
| `Gauge` | Current value/state | Queue depth, pool usage, row count |
| `Timer` | Duration + event count | Request/query/job latency |
| `DistributionSummary` | Distribution of arbitrary values + count | Payload size, batch size |
| `LongTaskTimer` | Currently running long operations | Import/export jobs, long refreshes |
| `FunctionCounter` | Existing monotonically increasing value | Cache eviction count, library-provided cumulative stats |
| `FunctionTimer` | Existing count + total duration | Instrument an object that already tracks both |
| `TimeGauge` | Current time-valued state | Current lease duration, timeout, age |
| `MultiGauge` | Multiple related gauges with dynamic tags | DB counts grouped by status |

Micrometer's core distinction is important: **a metric's type describes the semantics of the value, not how frequently it is sent to the monitoring backend**. Publishing is controlled by the particular registry/backend. 

---

# 1. Counter

Use a counter when something **happens**.

```java
Counter ordersCreated = registry.counter("orders.created");

ordersCreated.increment();
```

Or:

```java
ordersCreated.increment(numberOfOrders);
```

A counter can only increase. Micrometer specifically recommends thinking about counters primarily in terms of **rates**, rather than the absolute value. 

For example, with Prometheus:

```promql
rate(orders_created_total[5m])
```

gives orders/sec.

### Distributed behavior

Suppose you have:

```text
instance A: 100 requests
instance B: 150 requests
instance C:  80 requests
```

Each application instance has its **own counter**.

Prometheus normally sees separate time series distinguished by labels such as `instance`:

```text
http_requests_total{instance="app-1"} 100
http_requests_total{instance="app-2"} 150
http_requests_total{instance="app-3"}  80
```

You aggregate them:

```promql
sum(rate(http_requests_total[5m]))
```

That produces the rate for the whole service.

Prometheus pulls the individual instances; Micrometer does not magically combine your application instances. 

### How often is it reported?

This depends on the registry.

With Prometheus, **Prometheus scrapes the endpoint**. If it scrapes every 15 seconds, you effectively observe the counter every 15 seconds. The counter itself can be incremented thousands of times between scrapes. 

This means:

```java
counter.increment();
counter.increment();
counter.increment();
```

doesn't cause three network transmissions.

Metric recording is intended to be cheap, while publishing is handled separately. 

---

# 2. Gauge

A gauge represents **the value of something right now**.

```java
AtomicInteger queueSize = new AtomicInteger();

Gauge.builder("my.queue.size", queueSize, AtomicInteger::get)
      .register(registry);
```

Or for an object:

```java
Gauge.builder(
        "my.queue.size",
        queue,
        Queue::size
    )
    .register(registry);
```

Micrometer intentionally treats gauges as sampled values rather than accumulated measurements. Values that occur between observations are lost. 

So:

```text
time →

queue size:
10 ── 50 ── 5 ── 30
        ↑
      never observed
```

If the backend only samples at the end, it might only see:

```text
30
```

It does **not** know that the queue briefly reached 50.

### Distributed behavior

Yes: **a gauge is normally reported separately by every application instance.**

Suppose:

```text
app-1 queue.size = 20
app-2 queue.size = 35
app-3 queue.size = 15
```

Prometheus gets three series:

```text
queue_size{instance="app-1"} 20
queue_size{instance="app-2"} 35
queue_size{instance="app-3"} 15
```

You can sum them:

```promql
sum(queue_size)
```

but only if those quantities are **independent pieces of state**.

For example, this is sensible:

```text
queue_size = items waiting in this instance's local queue
```

because:

```text
total queue = queue1 + queue2 + queue3
```

But this is **not** sensible:

```text
database_rows = SELECT COUNT(*) FROM users
```

on every instance.

Three instances might all report:

```text
app-1: 1,000,000
app-2: 1,000,000
app-3: 1,000,000
```

and:

```promql
sum(database_rows)
```

would incorrectly produce:

```text
3,000,000
```

The database contains only 1,000,000 rows.

This distinction becomes especially important with Kubernetes autoscaling.

---

# 3. Timer

A `Timer` is the standard choice for **duration measurements**.

```java
Timer timer = registry.timer("orders.query.duration");

timer.record(() -> {
    queryDatabase();
});
```

Or:

```java
Timer.Sample sample = Timer.start(registry);

try {
    doSomething();
}
finally {
    sample.stop(timer);
}
```

A timer records at least:

- number of events
- total duration

and can additionally provide max, percentiles, histograms, etc., depending on configuration/backend. 

For Prometheus, for example, you get concepts equivalent to:

```text
orders_query_duration_seconds_count
orders_query_duration_seconds_sum
```

So average latency can be calculated as:

```promql
rate(orders_query_duration_seconds_sum[5m])
/
rate(orders_query_duration_seconds_count[5m])
```

### Distributed behavior

Again, every instance has its own timer.

But timers are particularly convenient to aggregate.

If:

```text
instance A: 100 requests
instance B: 200 requests
```

you can aggregate their counts:

```promql
sum(rate(request_duration_seconds_count[5m]))
```

and their total durations:

```promql
sum(rate(request_duration_seconds_sum[5m]))
```

Then:

```promql
sum(rate(request_duration_seconds_sum[5m]))
/
sum(rate(request_duration_seconds_count[5m]))
```

gives the average latency across the service.

### Important

Do **not** do this:

```java
Timer requestTimer = ...
Counter requestCounter = ...
```

and increment both for the same requests.

The timer already has a count. Micrometer explicitly recommends not duplicating a counter for something being timed. 

---

# 4. DistributionSummary

Use this when you want the **distribution of values other than time**.

For example:

```java
DistributionSummary payloadSize =
        DistributionSummary.builder("http.response.size")
                .baseUnit("bytes")
                .register(registry);

payloadSize.record(responseBytes);
```

It is structurally similar to a timer, except the recorded value isn't a duration. 

Good examples:

```text
HTTP response size
message size
batch size
items processed per job
number of DB rows returned
```

Conceptually, you get:

```text
count
sum
max
histogram/percentiles (if configured)
```

rather than a single "current" number.

For example:

```text
batch sizes:
10
12
11
500
15
13
```

A gauge would tell you only the **current batch size**.

A distribution summary lets you ask questions such as:

> What's the p95 batch size?

---

# 5. LongTaskTimer

A normal `Timer` is good for completed operations:

```text
start ───────── finish
              ↓
        record(duration)
```

A `LongTaskTimer` is useful when you want to know about work **while it is still running**.

```java
LongTaskTimer timer =
        registry.more().longTaskTimer("data.import");

LongTaskTimer.Sample sample = timer.start();

try {
    performLargeImport();
}
finally {
    sample.stop();
}
```

It provides information such as:

- number of currently active tasks
- total duration of currently active tasks
- maximum duration of active tasks

It does **not** primarily describe completed tasks like a normal `Timer` does. 

This makes it useful for:

```text
currently running imports
currently running backups
long-running scheduled tasks
metadata refreshes
batch processing
```

For example:

```promql
data_import_active_count
```

can tell you that three imports are currently running.

---

# 6. FunctionCounter

This is useful when the thing you're measuring **already maintains the counter**.

For example, a cache might expose:

```java
cache.stats().evictionCount()
```

You don't want to manually call:

```java
counter.increment();
```

every time the cache evicts something.

Instead:

```java
registry.more().counter(
    "cache.evictions",
    Tags.empty(),
    cache,
    c -> c.stats().evictionCount()
);
```

Micrometer periodically obtains the monotonically increasing value from the object. 

Conceptually:

```text
cache internal value

100
105
108
113
```

Micrometer observes that existing counter rather than maintaining a second counter itself.

This is particularly useful when instrumenting third-party libraries.

---

# 7. FunctionTimer

`FunctionTimer` is the analogous idea for an object that **already maintains duration statistics**.

Conceptually you have an object exposing:

```java
long count();
double totalTime(...);
```

and Micrometer turns those into a timer-like metric.

It's useful when you already have cumulative statistics available and don't want to manually call:

```java
timer.record(...)
```

for every event.

The important distinction is:

```text
Timer
    application records individual events

FunctionTimer
    Micrometer reads cumulative statistics maintained elsewhere
```

---

# 8. TimeGauge

A `TimeGauge` is essentially a gauge whose value represents **time** and therefore needs a time unit.

For example:

```java
TimeGauge.builder(
        "connection.timeout",
        timeoutValue,
        TimeUnit.MILLISECONDS,
        AtomicLong::get
    )
    .register(registry);
```

Micrometer can convert the unit for the particular monitoring backend. 

Use it for things like:

```text
current lease duration
current timeout
age of something
current time-based threshold
```

Do not use it for event durations; use `Timer` for those.

---

# 9. MultiGauge

`MultiGauge` is useful when you have a **small, dynamic set of related gauges**.

A classic example is:

```sql
SELECT status, COUNT(*)
FROM jobs
GROUP BY status;
```

Suppose the result is:

```text
pending   120
running    8
failed     17
completed 950
```

You can expose:

```text
jobs{status="pending"}   120
jobs{status="running"}     8
jobs{status="failed"}     17
jobs{status="completed"} 950
```

Micrometer specifically provides `MultiGauge` for this pattern. 

---

# How frequently are metrics actually sent?

This is one of the most important concepts to understand.

There are **three separate things**:

```text
1. Application records metric
        ↓
2. Meter stores/updates measurement
        ↓
3. Registry exposes/publishes measurement
```

The third step depends heavily on the monitoring system.

## Prometheus

Typical architecture:

```text
Spring Boot
    │
    │ /actuator/prometheus
    ▼
Prometheus
    │
    │ scrape every 15s
    ▼
time-series database
```

Prometheus periodically scrapes the application. 

Therefore:

```java
counter.increment();  // potentially thousands of times
```

doesn't generate thousands of Prometheus requests.

The value is observed on the next scrape.

## Push-based systems

Other registries publish periodically themselves. Micrometer adapts its behavior to the expectations of the backend: some systems expect cumulative values, while others expect rate-normalized values. 

For example, StatsD has a configurable publishing step; its default is one minute. 

So there isn't a universal answer such as:

> "Micrometer reports every 10 seconds."

It depends on your registry/exporter.

---

# The database row-count problem

This is a particularly good example because it exposes the difference between a **gauge** and a **counter**.

Suppose you want:

> How many users currently exist?

That's a **gauge**, not a counter.

```text
users = 1,524,831
```

You care about the current state.

You could technically do:

```java
registry.gauge(
    "database.users",
    userRepository.count()
);
```

but this is a bad implementation.

For one thing, `userRepository.count()` is evaluated when registering the gauge, not continuously. More importantly, you don't want metric observation itself to execute an expensive database query.

---

## Bad approach

Something conceptually like:

```java
Gauge.builder(
    "database.users",
    repository,
    r -> r.count()
).register(registry);
```

means metric observation can cause database access.

Depending on your instrumentation/export architecture, a scrape could effectively result in:

```text
Prometheus scrape
       ↓
Spring
       ↓
SELECT COUNT(*) FROM users
```

and repeat that across every instance.

With:

```text
20 application instances
15-second scrape interval
```

you have created substantial, completely artificial database traffic.

Metrics should generally be cheap to observe; Micrometer explicitly expects meter recording/observation to be inexpensive and separate from publishing. 

---

# Better approach: periodically refresh a gauge

Run the DB query separately:

```java
@Component
public class DatabaseMetrics {

    private final AtomicLong userCount = new AtomicLong();

    public DatabaseMetrics(
            MeterRegistry registry,
            UserRepository repository) {

        Gauge.builder("database.users", userCount, AtomicLong::get)
                .register(registry);
    }

    @Scheduled(fixedRate = 30_000)
    void updateUserCount() {
        userCount.set(repository.count());
    }
}
```

Now the architecture becomes:

```text
                 every 30 seconds
                       │
                       ▼
                 SELECT COUNT(*)
                       │
                       ▼
                   AtomicLong
                       │
                 ┌─────┴─────┐
                 │   Gauge   │
                 └─────┬─────┘
                       │
                 Prometheus scrape
                 every 15 seconds
```

This separates:

```text
database refresh frequency
```

from:

```text
metrics scrape frequency
```

which is usually exactly what you want.

For example:

```text
DB query:       every 30 s
Prometheus:     every 15 s
```

Prometheus gets the most recently known count twice on average.

You don't need to execute:

```sql
SELECT COUNT(*)
```

every time the metrics endpoint is accessed.

---

# But distributed systems make this more interesting

Suppose you have:

```text
              PostgreSQL
             /    |    \
            /     |     \
         app-1   app-2   app-3
```

Each application queries:

```sql
SELECT COUNT(*) FROM users
```

and exposes:

```text
app-1 = 1,000,000
app-2 = 1,000,000
app-3 = 1,000,000
```

Do **not** do:

```promql
sum(database_users)
```

because that produces:

```text
3,000,000
```

The values represent the same shared database state.

You have several better options.

### Option A - query once centrally

Have a dedicated metrics/exporter component query the database:

```text
                   PostgreSQL
                       │
                       ▼
                 DB metrics exporter
                       │
                       ▼
                  Prometheus
```

Then there is only one authoritative series.

This is often the cleanest architecture for infrastructure/database metrics.

### Option B - tolerate duplicate observations

All application instances expose:

```text
database_users = 1,000,000
```

and your dashboard uses something like:

```promql
max(database_users)
```

rather than `sum`.

This can work, but now your application instances are redundantly querying the database.

### Option C - derive the count from an existing database metric

Some database/exporter setups already expose information about table sizes, tuples, etc. In that case, prefer the database monitoring system rather than adding application-level `COUNT(*)` queries.

---

# When should a row count be a Counter?

This is a subtle but important distinction.

Suppose the question is:

> How many users currently exist?

Use:

```text
Gauge
```

Suppose the question is:

> How many users have been created?

Use:

```text
Counter
```

Suppose the question is:

> How many users have been deleted?

Use another:

```text
Counter
```

Then you can monitor:

```text
created rate
deleted rate
```

And potentially derive population changes.

For example:

```promql
rate(users_created_total[5m])
-
rate(users_deleted_total[5m])
```

gives the approximate rate of population change.

But it doesn't replace a periodically observed authoritative row count, because counters don't account for all historical conditions unless your instrumentation is guaranteed to capture every mutation.

---

# A useful metric design example

Imagine your Spring Boot application has a blog.

You might have:

### Requests

```java
Timer timer = registry.timer(
    "blog.request.duration",
    "operation", "view"
);
```

### Posts created

```java
Counter postsCreated =
    registry.counter("blog.posts.created");
```

### Active jobs

```java
LongTaskTimer jobTimer =
    registry.more().longTaskTimer("blog.indexing");
```

### Queue size

```java
Gauge.builder(
    "blog.queue.size",
    queue,
    Queue::size
).register(registry);
```

### Response size

```java
DistributionSummary responseSize =
    DistributionSummary.builder("blog.response.size")
        .baseUnit("bytes")
        .register(registry);
```

### Current DB population

```text
blog.users          gauge
blog.posts          gauge
blog.comments       gauge
```

### DB population by state

```text
blog.posts{status="published"}
blog.posts{status="draft"}
blog.posts{status="deleted"}
```

Potentially using `MultiGauge`.

---

# Tags/dimensions are extremely important

A meter isn't identified by its name alone.

Micrometer considers the **name + dimensions/tags** to identify a meter. 

This:

```java
registry.counter(
    "orders.created",
    "type", "premium"
);
```

and:

```java
registry.counter(
    "orders.created",
    "type", "free"
);
```

are different time series.

That's powerful:

```promql
sum by (type) (rate(orders_created_total[5m]))
```

But it has a cost.

Avoid unbounded tags such as:

```text
userId
requestId
email
UUID
timestamp
URL containing arbitrary IDs
```

because you'd potentially create enormous numbers of time series.

Prefer:

```text
operation
status
region
method
result
type
```

with a small bounded set of values.

This is one of the most important optimization rules for metrics.

---

# Metrics and distributed aggregation

A good mental model is:

```text
                Application instances

             app-1   app-2   app-3
               │       │       │
               ▼       ▼       ▼
             metrics metrics metrics
                \       |       /
                 \      |      /
                  ▼     ▼     ▼
                    Prometheus
                         │
                         ▼
                     aggregation
```

The application generally records **local observations**.

The monitoring system performs the service-wide aggregation.

For example:

### Counter

```promql
sum(rate(requests_total[5m]))
```

Usually good.

### Timer

```promql
sum(rate(request_duration_seconds_sum[5m]))
/
sum(rate(request_duration_seconds_count[5m]))
```

Usually good.

### Local queue gauge

```promql
sum(queue_size)
```

Usually good.

### Shared database gauge

```promql
sum(database_users)
```

Usually **wrong**.

### Shared database gauge

```promql
max(database_users)
```

Potentially correct, but preferably expose it from one authoritative source.

---

# Optimization principles

## 1. Don't perform expensive work when metrics are observed

Bad:

```text
Prometheus scrape
    ↓
expensive SQL query
    ↓
gauge
```

Better:

```text
scheduled SQL query
    ↓
cached AtomicLong
    ↓
gauge
```

---

## 2. Don't use a Gauge for events

Bad:

```java
Gauge "requests"
```

Good:

```java
Counter "requests"
```

A gauge loses intermediate observations. A counter preserves accumulated event information. 

---

## 3. Don't use a Counter for current state

Bad:

```text
users currently in database
```

Good:

```text
Gauge
```

---

## 4. Don't create a Counter alongside a Timer for the same event

Instead of:

```text
request_counter
request_timer
```

use the timer's count.

A timer already measures both frequency and duration. 

---

## 5. Use rates for counters

Usually:

```promql
rate(my_counter_total[5m])
```

is more useful than:

```promql
my_counter_total
```

because raw counters depend on the lifetime of the application instance. Restarts reset application-local counters. 

---

## 6. Keep cardinality under control

This is often a **much bigger performance problem than the cost of calling `counter.increment()`**.

Prefer:

```text
http_requests{method, status}
```

over:

```text
http_requests{user_id, request_id, session_id}
```

---

## 7. Choose histogram/percentile configuration deliberately

For timers and distribution summaries, enabling histograms, SLO buckets, and percentiles can create substantially more time series than a basic metric.

Spring Boot exposes configuration for:

```text
percentiles
percentiles-histogram
SLO buckets
minimum expected value
maximum expected value
expiry
buffer length
```

so these should be enabled according to an actual observability requirement rather than indiscriminately. 

For example, if all you need is:

> "What is the average request latency?"

you don't necessarily need an elaborate percentile histogram.

If you need:

> "What percentage of requests complete within 100 ms / 500 ms / 1 s?"

then histogram/SLO buckets become valuable.

---

# Spring Boot registration

The typical pattern is simply dependency injection:

```java
@Component
public class MyMetrics {

    private final Counter counter;

    public MyMetrics(MeterRegistry registry) {
        this.counter = registry.counter("my.events");
    }

    public void eventHappened() {
        counter.increment();
    }
}
```

Spring Boot auto-configures the application's `MeterRegistry` and supported registry implementations, depending on what is on the classpath. 

For reusable groups of instrumentation, Spring Boot also supports `MeterBinder`, which is often cleaner than scattering registration throughout application components. 

---

# Practical decision table

| Question you're asking | Use |
|---|---|
| "How many times did X happen?" | `Counter` |
| "How fast is X happening?" | `Counter` + rate |
| "How long does X take?" | `Timer` |
| "How many X are there right now?" | `Gauge` |
| "What's the distribution of X?" | `DistributionSummary` |
| "How many long-running X are currently active?" | `LongTaskTimer` |
| "A library already exposes a cumulative count" | `FunctionCounter` |
| "A library already exposes count + total duration" | `FunctionTimer` |
| "What's the current value of a time quantity?" | `TimeGauge` |
| "I have several dynamic gauges distinguished by bounded tags" | `MultiGauge` |

---

# Best-practice summary

The most useful way to think about Micrometer is:

```text
EVENT                → Counter
EVENT + DURATION     → Timer
VALUE DISTRIBUTION   → DistributionSummary
CURRENT STATE        → Gauge
CURRENT LONG WORK    → LongTaskTimer
EXISTING CUMULATIVE  → FunctionCounter
EXISTING TIMING DATA → FunctionTimer
TIME-VALUED STATE    → TimeGauge
DYNAMIC GAUGE SET    → MultiGauge
```

For a Spring Boot application running multiple instances:

```text
Counter / Timer
    → record locally
    → aggregate across instances in monitoring backend

Gauge
    → each instance reports its local state
    → aggregate only when the underlying states are additive

Shared database state
    → do NOT sum identical per-instance observations
    → preferably expose it from one authoritative exporter/source
    → otherwise periodically query and cache the value and use an appropriate
      aggregation such as max rather than sum
```

And for optimization:

```text
cheap metric operation
        +
bounded cardinality
        +
appropriate metric type
        +
reasonable collection/export interval
        +
aggregation in the monitoring backend
```

That combination is much more important than trying to minimize individual `counter.increment()` or `timer.record()` calls. Micrometer is designed for those operations to be inexpensive; the bigger practical risks are **high-cardinality tags, expensive metric observation, excessive histogram/percentile series, and incorrect cross-instance aggregation**. 

A particularly useful architecture for your Spring Boot + Prometheus/OTel setup is to treat **application metrics and database/infrastructure metrics as two different sources of truth**: application events belong in Micrometer, while authoritative global DB state is often better collected centrally rather than having every application replica query and report the same value.

## Prompt:
When the open telemetry collector is used, how the communication between prometheus and micrometer happens?

## Response:
The important point is that **Prometheus does not normally talk directly to Micrometer when an OpenTelemetry Collector is inserted**. The Collector sits between them, and there are two fundamentally different architectures.

## 1. The architecture I would recommend with Micrometer

For a Spring Boot application using Micrometer:

```text
┌──────────────────┐
│ Spring Boot      │
│                  │
│ Micrometer       │
│ OtlpMeterRegistry│
└────────┬─────────┘
         │
         │ OTLP
         │ HTTP or gRPC
         ▼
┌──────────────────┐
│ OpenTelemetry    │
│ Collector        │
│                  │
│ OTLP receiver    │
│       ↓          │
│ processors       │
│       ↓          │
│ exporter         │
└────────┬─────────┘
         │
         │ Prometheus Remote Write
         ▼
┌──────────────────┐
│ Prometheus       │
│                  │
│ time series DB   │
└──────────────────┘
```

Here **Micrometer pushes metrics to the Collector using OTLP**. The Collector then pushes them to Prometheus using Prometheus Remote Write. This is one of the deployment patterns recommended by OpenTelemetry. 

In other words:

```text
Micrometer ──OTLP──> Collector ──Prometheus Remote Write──> Prometheus
```

Prometheus is **not scraping Micrometer** in this architecture.

---

# 2. What happens inside Spring Boot

Spring Boot's metrics abstraction is still **Micrometer**. When you configure the OTLP registry, your custom metrics remain ordinary Micrometer metrics:

```java
Counter counter = registry.counter("orders.created");

counter.increment();
```

The difference is which `MeterRegistry` Spring Boot has configured.

With OTLP:

```text
MeterRegistry
      │
      ▼
OtlpMeterRegistry
      │
      ▼
OTLP export
```

Spring Boot supports configuring the OTLP registry with properties such as:

```yaml
management:
  otlp:
    metrics:
      export:
        url: http://otel-collector:4318/v1/metrics
```

Spring Boot's documentation explicitly describes Micrometer's `OtlpMeterRegistry` as the way Micrometer metrics are exported through OTLP. 

So your application might have:

```text
Spring Boot
    │
    ├── Counter
    ├── Timer
    ├── Gauge
    └── DistributionSummary
            │
            ▼
      OtlpMeterRegistry
            │
            ▼
        OTLP /v1/metrics
```

---

# 3. Is the communication push or pull?

This is where the architecture differs from the standard Micrometer + Prometheus setup.

### Direct Prometheus setup

```text
Prometheus
    │
    │ HTTP GET
    ▼
/actuator/prometheus
```

Prometheus asks:

> "Give me your current metrics."

Spring Boot exposes them at `/actuator/prometheus`. 

### OTLP Collector setup

```text
Application
    │
    │ HTTP POST
    ▼
Collector
```

The application asks:

> "Here are my metric data points."

OTLP is a push protocol. For OTLP/HTTP, metrics normally go to:

```text
/v1/metrics
```

and OTLP/gRPC uses the Collector's OTLP gRPC endpoint. 

So:

```text
Prometheus architecture:
    Prometheus → application

OTLP architecture:
    application → Collector
```

That distinction is fundamental.

---

# 4. What does the Collector actually receive?

Suppose you have:

```java
Counter orders =
    registry.counter("orders.created");

orders.increment();
```

Micrometer maintains the meter locally.

At the configured export interval, the OTLP registry sends an OTLP metrics payload to:

```text
http://otel-collector:4318/v1/metrics
```

Conceptually:

```text
orders.created
    value = 1532
    type  = Sum
    attributes = ...
```

The exact wire representation is OTLP protobuf/HTTP rather than Prometheus text format.

The Collector receives it through:

```yaml
receivers:
  otlp:
    protocols:
      http:
```

and then sends it through its metrics pipeline:

```yaml
service:
  pipelines:
    metrics:
      receivers: [otlp]
      processors: [...]
      exporters: [...]
```

The Collector can therefore perform work such as:

```text
receive
  ↓
batch
  ↓
filter
  ↓
transform
  ↓
resource enrichment
  ↓
export
```

before anything reaches Prometheus.

---

# 5. The Collector does NOT necessarily convert to the `/metrics` scraping model

This is an easy misconception.

You might imagine:

```text
Micrometer
   ↓
Collector
   ↓
/metrics
   ↓
Prometheus scrape
```

That **can** be built, but it isn't the only, or usually the simplest, OTLP architecture.

A very common arrangement is:

```text
Micrometer
   │
   │ OTLP
   ▼
Collector
   │
   │ Prometheus Remote Write
   ▼
Prometheus
```

OpenTelemetry specifically documents this pattern with an `otlp` receiver and `prometheusremotewrite` exporter. 

Then the data flow is entirely push-based:

```text
App ──push──> Collector ──push──> Prometheus
```

---

# 6. There is another possible architecture

The Collector also has a **Prometheus receiver**.

That produces a different flow:

```text
Spring Boot
    │
    │ /actuator/prometheus
    ▼
Collector
    │
    │ scrape
    ▼
Collector
    │
    ▼
Prometheus
```

Here the Collector behaves somewhat like Prometheus's scraper.

The data flow becomes:

```text
Collector ──HTTP GET──> Spring Boot
```

rather than:

```text
Spring Boot ──OTLP──> Collector
```

This is useful when your application already exposes Prometheus metrics and you want the Collector to collect them.

But notice the consequence:

**Micrometer still isn't communicating with Prometheus through OTLP.**

It is:

```text
Micrometer
    ↓
Prometheus-format actuator endpoint
    ↓
Collector Prometheus receiver
```

This is a different integration strategy.

---

# 7. What happens to your custom metrics?

Suppose you write:

```java
Counter counter =
    registry.counter(
        "blog.posts.created",
        "type", "article"
    );

counter.increment();
```

With OTLP:

```text
Java code
   ↓
Micrometer Counter
   ↓
OtlpMeterRegistry
   ↓
OTLP metric
   ↓
Collector
   ↓
Prometheus
```

You don't need to change your instrumentation because of the Collector.

That's one of the main advantages of Micrometer:

```java
registry.counter(...)
registry.timer(...)
registry.gauge(...)
```

stays application code, while the registry determines how it gets exported.

Spring Boot explicitly recommends Micrometer for metrics, while its OpenTelemetry support allows those Micrometer metrics to be exported through OTLP. 

---

# 8. What happens to the reporting interval?

This is different from Prometheus scraping.

With ordinary Prometheus:

```text
Prometheus scrape interval = 15s
```

means:

```text
every 15 seconds:

Prometheus
   ↓
GET /actuator/prometheus
```

With Micrometer OTLP:

```text
Micrometer export interval = N seconds
```

means:

```text
every N seconds:

Spring Boot
   ↓
POST OTLP metrics
   ↓
Collector
```

The OTLP export interval is therefore an **application-side publishing interval**, not a Prometheus scrape interval.

For example:

```text
                 every 10 seconds
Spring Boot ─────────────────────────> Collector
```

Then the Collector might batch those measurements before exporting them onward.

The OpenTelemetry documentation notes a default OTLP metrics push interval of 60 seconds for its SDKs and explains that shorter intervals increase responsiveness at the cost of additional network and processing overhead. 

With Micrometer's OTLP registry, the relevant export configuration is provided by the registry itself/Spring Boot. 

---

# 9. How this affects the Gauge question from your previous message

This actually makes the previous gauge discussion more important.

Suppose:

```java
AtomicLong users = new AtomicLong();

Gauge.builder(
    "database.users",
    users,
    AtomicLong::get
).register(registry);
```

and you refresh it every 30 seconds:

```text
DB
 ↓
AtomicLong
 ↓
Gauge
 ↓
OTLP export every 10s
 ↓
Collector
 ↓
Prometheus
```

Then the same gauge value may be transmitted multiple times:

```text
DB refresh:       every 30s
OTLP export:      every 10s

12:00:00   DB → 1,000,000
12:00:10   export 1,000,000
12:00:20   export 1,000,000
12:00:30   DB → 1,000,004
12:00:40   export 1,000,004
```

That's fine.

The **database refresh frequency and metric export frequency are independent**.

This is one reason caching an expensive DB-derived gauge is preferable to querying the DB whenever metrics are exported.

---

# 10. Distributed application with Collector

Suppose Kubernetes runs three replicas:

```text
                    ┌── app-1 ──┐
                    │           │
                    ├── app-2 ──┼──> Collector
                    │           │
                    └── app-3 ──┘
```

Each instance has its own Micrometer registry.

So:

```text
app-1:
    requests = 500

app-2:
    requests = 700

app-3:
    requests = 300
```

Each sends its own metric stream.

The Collector does not inherently turn these into one application-level counter. It receives separate measurements with resource/attribute information identifying the source.

Your monitoring system can then aggregate:

```text
sum(rate(...))
```

or whatever query language your backend provides.

This is still fundamentally different from having a single global in-memory `Counter`.

---

# 11. Resource attributes become particularly important

When using OTLP, you generally want metrics to carry resource identity such as:

```text
service.name = blog-service
service.instance.id = ...
deployment.environment = production
```

This allows the backend to distinguish:

```text
blog-service / instance A
blog-service / instance B
blog-service / instance C
```

The OpenTelemetry documentation specifically recommends setting `service.name` and a unique `service.instance.id` when exporting metrics, because otherwise distinguishing instances and aggregating correctly becomes difficult. 

This is especially useful in Kubernetes.

---

# 12. One subtle but important point about Micrometer vs OpenTelemetry metrics

Spring Boot's normal metrics API remains Micrometer:

```java
MeterRegistry
Counter
Timer
Gauge
```

It does **not** mean that your Micrometer metrics suddenly become metrics managed by the OpenTelemetry SDK's `SdkMeterProvider`.

Spring Boot's current documentation explicitly says that metrics in the Spring ecosystem are collected/exported through Micrometer, and that Micrometer's OTLP registry is used to send them through OTLP. 

So you can think of it as:

```text
                  instrumentation API
                         │
                         ▼
                     Micrometer
                         │
                    OtlpMeterRegistry
                         │
                         ▼
                        OTLP
                         │
                         ▼
                   OTel Collector
```

rather than:

```text
Micrometer
    somehow becomes
OpenTelemetry SDK MeterProvider
```

Those are different things.

---

# 13. For your Grafana LGTM setup

Based on the architecture you were asking about previously, a typical development setup could be:

```text
                     ┌──────────────┐
                     │ Spring Boot  │
                     │              │
                     │ Micrometer   │
                     └──────┬───────┘
                            │
                       OTLP HTTP
                            │
                            ▼
                   ┌──────────────────┐
                   │ OTel Collector   │
                   ├──────────────────┤
                   │ OTLP receiver    │
                   │ processors       │
                   │                  │
                   │ OTLP → Tempo     │
                   │ metrics → Prom   │
                   └───────┬──────────┘
                           │
                ┌──────────┴──────────┐
                ▼                     ▼
          Prometheus/              Tempo
          metrics storage          traces
                │
                └──────────┬──────────┘
                           ▼
                         Grafana
```

The particularly useful conceptual split is:

```text
Micrometer
    = instrument application

OTLP
    = transport format/protocol

Collector
    = telemetry pipeline/router/processor

Prometheus
    = metrics storage/querying

Grafana
    = visualization
```

That separation is exactly why the Collector can sit in the middle without your application code knowing whether the eventual destination is Prometheus, another metrics backend, or several backends.

## One thing I would change from a simplistic "LGTM = Prometheus scrapes everything" mental model

Don't think of the Collector as merely a **fancy Prometheus scraper**.

With your Spring Boot + Micrometer setup, a more useful mental model is:

```text
                 application
                      │
              Micrometer metrics
                      │
                     OTLP
                      │
                      ▼
              OpenTelemetry Collector
               /        |        \
              /         |         \
         Prometheus    Tempo     another backend
```

The Collector is the **telemetry transport/processing layer**, while Micrometer is the **application instrumentation layer**.

And if you're using **Micrometer OTLP → Collector → Prometheus Remote Write**, Prometheus is no longer responsible for discovering and scraping those Spring Boot instances at all.



---
Powered by [ChatGPT Exporter](https://www.chatgptexporter.com)