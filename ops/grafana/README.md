# BookSearch Grafana assets

## booksearch-logs-dashboard.json

Pre-built Grafana dashboard for BookSearch logs collected via Promtail + Loki.

Expects Loki labels:
- `stack="booksearch"` (added in `docker-compose.yml` via Docker labels `logging.stack=booksearch`)
- `job` one of: `booksearch-backend`, `booksearch-flaresolverr`, `booksearch-mailpit`

Panels:
- Error rate / Warn rate / Backend req/s / Refresh rotations per hour (stats)
- Log volume by level (time series)
- Errors by job (time series)
- Live logs (tail)

### Import

Grafana → Dashboards → Import → paste `booksearch-logs-dashboard.json` or
upload the file. Pick the existing Loki datasource.

### Promtail expectations

The shared monitor-stack Promtail should have `docker_sd_configs` with a
relabel rule that maps Docker labels to Loki labels. Minimal example:

```yaml
scrape_configs:
  - job_name: docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        refresh_interval: 15s
    relabel_configs:
      - source_labels: [__meta_docker_container_label_logging_stack]
        target_label: stack
      - source_labels: [__meta_docker_container_label_logging_job]
        target_label: job
      - source_labels: [__meta_docker_container_name]
        target_label: container
    pipeline_stages:
      - docker: {}
      - json:
          expressions:
            level: level
            x_request_id: x-request-id
            logger: logger_name
      - labels:
          level:
          logger:
```

Then in Grafana Explore: `{stack="booksearch"}`.
