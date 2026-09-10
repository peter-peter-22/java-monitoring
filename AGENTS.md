# Project guide

This is a learning project for Spring Boot observability using Grafana's all-in-one
LGTM Docker image (Grafana, Prometheus, Loki, Tempo, and an OpenTelemetry
Collector). Keep examples small and favour current Spring Boot/Micrometer/OTLP
configuration over custom infrastructure.

## Layout

- `blog/` — Spring Boot 4 / Java 25 blog application, PostgreSQL Compose files,
  and topic guides.
- `k6/` — k6 load scripts. `blog.js` simulates authenticated, CSRF-protected
  user activity and writes `result.json`.
- `blog/compose-observation.yaml` — full stack: blog, PostgreSQL, LGTM, and k6.
- `blog/compose-dev.yaml` — only PostgreSQL and LGTM for running the app locally.

## Common workflow

From `blog/`, start the development dependencies with
`docker compose -f compose-dev.yaml up`. Run the application locally with
`./mvnw spring-boot:run`, or start the complete demonstration with
`docker compose -f compose-observation.yaml up`.

- Blog: `http://localhost:8080`
- Grafana: `http://localhost:3000`
- Run tests: `cd blog && ./mvnw test`
- Run k6 independently: follow `k6/how_to_use.md`; it must reach the blog at
  `localhost:8080`.

## Observability conventions

- OTLP endpoints are configured in `blog/src/main/resources/application.yaml`;
  Compose overrides them with service-host URLs. Preserve both local and Compose
  paths when changing telemetry configuration.
- Traces, metrics, and logs are exported through the LGTM collector on port
  `4318`. Sampling is deliberately 100% for demonstrations.
- The Logback OTLP appender is initialized by
  `OpenTelemetryAppenderInitializer`; `AuthenticatedUserMdcFilter` supplies the
  `user.id` MDC attribute captured by `logback-spring.xml`.
- Put domain metrics/observations near `blog/configuration/BlogPostMetrics` and
  use the guides in `blog/guides/` as the teaching material for each signal.

## Guardrails

- This setup is development/learning only: credentials, schema recreation, and
  all-in-one LGTM are intentional and must not be presented as production design.
- Keep Docker image versions and ports aligned across both Compose files.
- Do not hand-edit generated load-test output (`k6/result.json`) or build output.
