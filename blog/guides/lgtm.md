# Grafana LGTM

The grafana LGTM docker image is a single container that contains multiple
pre-configured observation services for development convenience.
The behavior is the same as using the containers separately with the
correct configuration.

The image contains the following services:
- Prometheus
- Loki
- Tempo
- Grafana UI
- Open telemetry collector

It is not intended for production use because it is not scalable.

For the scalable container setup, see:
[separating LGTM guide](separate_lgtm.md)

## Links
- [Grafana docker LGTM home page](https://grafana.com/docs/opentelemetry/docker-lgtm/)
- [Grafana docker OTEL LGTM github](https://github.com/grafana/docker-otel-lgtm)