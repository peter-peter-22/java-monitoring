# Grafana alerts

Dashboards help us notice a problem while we are looking at Grafana. An alert
rule checks a metric at a regular interval and changes state when its condition
is true for long enough. It can warn about an exhausted resource, but it is
also useful for user-visible failures, such as an elevated HTTP 5xx rate.

This project uses the Grafana Alerting built into the `grafana/otel-lgtm`
container. It is intentionally a local learning setup, not a production
notification system.

## Where alerts appear

Start the development services and application as described in the
[project README](../../readme.md#how-to-start), then sign in to Grafana at
`http://localhost:3000` with `admin` / `admin`.

An alert is always visible in **Alerts & IRM -> Alerting -> Alert rules**.
That is sufficient to learn rule evaluation: its state is `Normal`, `Pending`,
or `Firing`.

To actively notify a developer outside Grafana, create a **contact point** at
**Alerts & IRM -> Alerting -> Notification configuration -> Contact points**.
Grafana supports, among others, email, Slack, and webhooks. Test the contact
point from its edit page, then select it in the rule or route a rule to it with
a notification policy. Without a contact point, a firing rule is not an email
or chat message; it is only shown in Grafana.

For this example project, it is usually safer to use a webhook receiver made
for development or a test Slack channel. Do not commit webhook URLs, SMTP
passwords, or chat tokens to this repository.

## Create a disk-space alert

The dashboard's **Used disk space** panel already calculates the disk-use
ratio. We can turn the same signal into an alert:

1. Open **Alerts & IRM -> Alerting -> Alert rules**, then select **+ New alert
   rule**.
2. Name it `Blog disk usage is high` and select the `prometheus` data source.
3. Use this PromQL query:

   ```promql
   (disk_total_bytes - disk_free_bytes) / disk_total_bytes
   ```

4. Reduce the query to its **Last** value, then make the condition **Is above
   0.90**. Grafana represents a percentage ratio as a value from `0` to `1`.
5. Set the evaluation interval to `1m` and the pending period (**For**) to
   `5m`. The value must stay above 90% for five minutes before the rule fires;
   short-lived spikes do not page the developer.
6. Put the rule in a folder such as `blog`, and add labels
   `service=blog` and `severity=warning`. Labels are useful for filtering and
   notification routing.
7. Add a summary such as `Blog disk usage has exceeded 90% for 5 minutes` and
   link the rule to the dashboard's disk panel. Save the rule.

The **Alert rules** page now shows the result. A value over the threshold is
first `Pending`, then `Firing` after the configured pending period. When disk
use drops below the threshold, it returns to `Normal`. The rule is evaluated
by Grafana; no Spring Boot code change is necessary.

## Other useful rules

Use thresholds that match the service's capacity and its normal baseline. The
following are starting points for this demonstration, not universal production
values.

| Concern | PromQL query | Example condition |
| --- | --- | --- |
| JVM heap pressure | `sum(jvm_memory_used_bytes) / sum(jvm_memory_max_bytes)` | Above `0.85` for `10m` |
| Process CPU pressure | `process_cpu_usage` | Above `0.80` for `10m` |
| HTTP server errors | `sum(increase(http_server_requests_milliseconds_count{outcome="SERVER_ERROR"}[5m]))` | Above `5` for `5m` |

The metric names and labels come from the same data source used by
[the dashboard](dashboard.md) and [metrics guide](metrics.md). Confirm a query
in **Explore** before using it in a rule: metric labels can differ after a
framework or instrumentation upgrade. For more than one application instance,
aggregate intentionally and keep labels such as `service` or `instance` when
you need the alert to identify the affected instance.

## Choose the notification route

For one local rule, select a contact point directly in the alert rule. For
several rules, create notification policies instead. For example, give every
blog rule `service=blog`, then route `severity=warning` to a development
channel and `severity=critical` to the on-call contact point. Policies also
group similar alert instances and control repeat timing, which prevents a
single incident from producing a flood of messages.

## Test and keep the rule

Test a notification separately with the contact point's **Test** action. To
test the condition, temporarily use a harmless lower threshold, wait for the
evaluation interval and pending period, and confirm that the rule becomes
`Firing`. Restore the real threshold immediately afterwards.

Rules created through the UI live in Grafana's local state. The Compose files
do not mount a persistent Grafana data directory, so treat UI-created rules as
disposable when the `lgtm` container is removed (for example by
`docker compose down`). For reproducible shared or production alerting,
provision alert rules, contact points, and notification policies as managed
configuration, and use a persistent Grafana database. Grafana documents both
[creating Grafana-managed alert rules](https://grafana.com/docs/grafana/latest/alerting/unified-alerting/alerting-rules/create-grafana-managed-rule/)
and [provisioning alerting resources](https://grafana.com/docs/grafana/latest/alerting/set-up/provision-alerting-resources/file-provisioning/).

## Further reading

- [Grafana alert-rule concepts](https://grafana.com/docs/grafana/latest/alerting/fundamentals/alert-rules/)
- [Grafana contact points and notification testing](https://grafana.com/docs/grafana/latest/alerting/configure-notifications/manage-contact-points/)
- [Grafana notification policies](https://grafana.com/docs/grafana/latest/alerting/configure-notifications/create-notification-policy/)
