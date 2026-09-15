# Separating LGTM

`compose-observation-prod.yaml` replaces the all-in-one image with five
explicit services: Grafana, Prometheus, Loki, Tempo, and an OpenTelemetry
Collector. It is a single-node reference topology, not a complete production
deployment.

## Data flow

```text
blog --OTLP/HTTP--> OpenTelemetry Collector --OTLP/HTTP--> Tempo
                                             --OTLP/HTTP--> Loki
                                             --Remote Write--> Prometheus

Grafana -------------------------------------> Tempo, Loki, Prometheus
```

The application exports all three signals to `otel-collector:4318`. The
Collector configuration in `observability/otel-collector.yaml` fans them out:
traces to Tempo, logs to Loki's native OTLP endpoint, and metrics to
Prometheus's Remote Write receiver. Grafana data sources are provisioned from
`observability/grafana/provisioning`, using Docker service names rather than
`localhost`.

Start the reference stack from `blog/` with a non-default Grafana password:

```bash
GRAFANA_ADMIN_PASSWORD='use-a-secret-manager-in-real-deployments' \
  docker compose -f compose-observation-prod.yaml up
```

Only the blog and Grafana ports are published. Keep Collector ingestion and
the three backends on a private network; expose them only through an
authenticated TLS gateway when external access is required.

## S3-compatible storage for Loki and Tempo

Create separate private buckets, for example `loki-data` and `tempo-traces`,
and give each service credentials scoped only to its own bucket. The endpoint
must be reachable from the containers (for example, `object-storage:9000`),
not `localhost`. Use HTTPS in production; S3-compatible stores commonly also
need path-style requests.

In `observability/loki.yaml`, remove `common.storage.filesystem`, change the
existing schema entry's `object_store` to `s3`, and add this root-level block:

```yaml
storage_config:
  tsdb_shipper:
    active_index_directory: /loki/index
    cache_location: /loki/index_cache
  aws:
    endpoint: ${LOKI_S3_ENDPOINT}
    bucketnames: ${LOKI_S3_BUCKET}
    access_key_id: ${LOKI_S3_ACCESS_KEY}
    secret_access_key: ${LOKI_S3_SECRET_KEY}
    s3forcepathstyle: true
    insecure: false
```

Replace the `storage.trace` block in `observability/tempo.yaml` with:

```yaml
storage:
  trace:
    backend: s3
    s3:
      endpoint: ${TEMPO_S3_ENDPOINT}
      bucket: ${TEMPO_S3_BUCKET}
      access_key: ${TEMPO_S3_ACCESS_KEY}
      secret_key: ${TEMPO_S3_SECRET_KEY}
      forcepathstyle: true
      insecure: false
    wal:
      path: /var/tempo/wal
```

Pass those variables via Docker secrets, workload identity, or the platform's
secret manager—never commit them to Compose or these files. Add
`-config.expand-env=true` to the Loki and Tempo commands so their configuration
files resolve the variables. Configure the object store's CA when it uses a
private certificate. Loki writes log chunks and TSDB indexes; Tempo writes
trace blocks and their metadata. Keep backend retention enabled and do not use
an unscoped bucket lifecycle deletion rule, because it can remove required
index or cluster-state objects.

## Prometheus limits and replacing it with Mimir

The Prometheus service in this example is suitable for a small, single-node
installation. Its TSDB uses a local filesystem for the write-ahead log, active
head, and compacted blocks; it is neither clustered nor replicated. Do not set
`storage.tsdb.path` to an S3 bucket or a mounted object-store filesystem.

For high availability, long retention, or horizontally scalable queries,
replace standalone Prometheus with Grafana Mimir. Mimir accepts Prometheus
Remote Write, stores compacted Prometheus-compatible TSDB blocks in object
storage, and separates ingest, query, and compaction workloads. It still needs
fast local disk for transient state, while S3-compatible storage holds the
durable blocks, recording rules, and Alertmanager state.

In this topology, point the Collector's `prometheusremotewrite` exporter at
the Mimir gateway (commonly `http://mimir-gateway/api/v1/push`) instead of
`http://prometheus:9090/api/v1/write`. Point Grafana's existing `prometheus`
data source at that gateway as well—the data source type and PromQL queries do
not change. A production Mimir deployment additionally needs tenant-aware
authentication, object-store credentials, and its supported distributed
deployment configuration.

## Production requirements

The Compose file makes the routing and persistent volumes visible, but a real
deployment must also use pinned image digests, a secret manager, TLS and
authentication between every trust boundary, resource limits, health checks,
backups, retention policies, and monitoring for the monitoring stack itself.

The included Loki and Tempo configurations use local volumes so the example is
self-contained. For a scalable or highly available deployment, use the
supported Helm/Tanka deployment mode and shared object storage (for example
S3, GCS, or Azure Blob), then size and load-test each signal independently.
Do not use the sample database credentials or 100% trace sampling in
production.

Keep labels bounded: the Loki configuration indexes only stable service and
environment attributes, leaving fields such as user IDs and instance IDs as
structured metadata. This avoids unbounded index cardinality while preserving
those fields for queries.

## References

- [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [Loki native OTLP ingestion](https://grafana.com/docs/loki/latest/send-data/otel/)
- [Tempo deployment guidance](https://grafana.com/docs/tempo/latest/set-up-for-tracing/setup-tempo/deploy/)
- [Prometheus Remote Write receiver](https://prometheus.io/docs/prometheus/latest/storage/)
- [Grafana Mimir architecture](https://grafana.com/docs/mimir/latest/get-started/about-grafana-mimir-architecture/)
