# 监控观测指南

本文档说明 nexus-plus 的健康检查、Prometheus 指标、Grafana 仪表盘和常用告警建议。

## 暴露端口

nexus-plus 使用 Spring Boot Actuator 暴露健康检查和指标。

| 场景 | 服务端口 | 管理端口 | 说明 |
| --- | --- | --- | --- |
| 默认运行 | `8080` | `8081` | 管理端口由 `NEXUS_PLUS_MANAGEMENT_PORT` 覆盖 |
| 本地 `dev` profile | `18090` | `18091` | `scripts/dev.sh` 使用的默认端口 |

常用端点：

```text
/actuator/health
/actuator/metrics
/actuator/prometheus
```

本地验证：

```bash
curl -sS http://127.0.0.1:8081/actuator/health
curl -sS http://127.0.0.1:8081/actuator/prometheus | head
```

如果使用开发脚本启动，把端口换成 `18091`。

## Prometheus 接入

Prometheus 抓取管理端口的 `/actuator/prometheus`：

```yaml
scrape_configs:
  - job_name: nexus-plus
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - nexus-plus:8081
```

Kubernetes 环境可以给 Pod 或 Service 增加 Prometheus 抓取注解：

```yaml
prometheus.io/scrape: "true"
prometheus.io/port: "8081"
prometheus.io/path: "/actuator/prometheus"
```

如果使用 Prometheus Operator，可以用 `ServiceMonitor` 抓取管理端口：

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: nexus-plus
spec:
  selector:
    matchLabels:
      app: nexus-plus
  endpoints:
    - port: management
      path: /actuator/prometheus
      interval: 30s
```

指标默认带 `application` 标签，默认值来自 `spring.application.name`，也就是 `nexus-plus`。如果部署多个环境，建议在 Prometheus 侧保留 `namespace`、`pod`、`instance` 等标签，便于按环境和副本排查。

## Grafana 仪表盘

内置 Grafana dashboard 文件：

```text
docs/resources/grafana/dashboard.json
```

导入方式：

1. 打开 Grafana。
2. 进入 `Dashboards` -> `New` -> `Import`。
3. 上传 `docs/resources/grafana/dashboard.json`。
4. 选择任意 Prometheus 数据源。
5. 在 dashboard 顶部选择 `application`，默认匹配 `nexus-plus` 系列应用标签。

仪表盘已使用通用 Prometheus 数据源变量，不绑定特定的 `Prometheus-DEV` 数据源名称。

### 仪表盘预览

总体 HTTP 请求、QPS、延迟和错误概览：

![Grafana 总览](../img/grafna/img.png)

仓库请求、代理回源和 blob 存储指标：

![Grafana 仓库和存储指标](../img/grafna/img_1.png)

后台任务、重建队列、GC 和限流指标：

![Grafana 后台任务指标](../img/grafna/img_2.png)

## 关键指标

### HTTP 和仓库请求

| 指标 | 类型 | 说明 |
| --- | --- | --- |
| `http_server_requests_seconds_*` | Spring Boot timer | HTTP 请求延迟和数量 |
| `nexus_plus_repository_requests_total` | counter | 仓库协议请求数 |
| `nexus_plus_repository_request_duration_seconds_*` | timer | 仓库协议请求延迟 |
| `nexus_plus_repository_upload_bytes_total` | counter | 上传请求体字节数 |
| `nexus_plus_repository_response_bytes_total` | counter | 响应字节数 |

常用标签：

- `repo`：仓库名
- `format`：制品格式，例如 `maven2`、`npm`、`pypi`
- `type`：仓库类型，例如 `hosted`、`proxy`、`group`
- `method`：HTTP method
- `operation`：协议操作
- `status`：HTTP 状态码
- `outcome`：请求结果，例如 `success`、`client_error`、`server_error`、`error`

### Proxy 回源

| 指标 | 类型 | 说明 |
| --- | --- | --- |
| `nexus_plus_proxy_remote_requests_total` | counter | proxy 仓库回源请求数 |
| `nexus_plus_proxy_remote_duration_seconds_*` | timer | proxy 仓库回源延迟 |
| `nexus_plus_proxy_cache_events_total` | counter | proxy cache 命中、未命中、negative cache 等事件 |

重点关注 `remote_host`、`status` 和 `outcome`。如果 proxy 仓库拉包变慢，优先看回源延迟和回源错误。

### Blob 存储

| 指标 | 类型 | 说明 |
| --- | --- | --- |
| `nexus_plus_blob_storage_operations_total` | counter | blob 存储操作数 |
| `nexus_plus_blob_storage_operation_duration_seconds_*` | timer | blob 存储操作延迟 |
| `nexus_plus_blob_storage_bytes_total` | counter | blob 读写字节数 |

常用标签：

- `store`：blob store 名称
- `type`：blob store 类型
- `engine`：存储引擎，例如 `oss-native`、`s3`、`file`
- `op`：操作，例如 `put`、`get`、`get_range`、`delete`
- `outcome`：`success`、`miss`、`error`

### 后台任务和队列

| 指标 | 类型 | 说明 |
| --- | --- | --- |
| `nexus_plus_worker_items_total` | counter | 后台 worker 处理的 item 数 |
| `nexus_plus_worker_item_duration_seconds_*` | timer | 单个 item 处理耗时 |
| `nexus_plus_worker_batch_duration_seconds_*` | timer | worker batch 处理耗时 |
| `nexus_plus_metadata_rebuild_backlog` | gauge | Maven metadata 重建积压 |
| `nexus_plus_metadata_rebuild_oldest_age_seconds` | gauge | 最老 Maven metadata 重建任务等待时长 |
| `nexus_plus_metadata_rebuild_failures` | gauge | Maven metadata 重建失败积压 |
| `nexus_plus_repository_index_rebuild_backlog` | gauge | 仓库索引重建积压 |
| `nexus_plus_repository_index_rebuild_oldest_age_seconds` | gauge | 最老仓库索引重建任务等待时长 |
| `nexus_plus_repository_index_rebuild_failures` | gauge | 仓库索引重建失败积压 |

如果 browse/search 或协议 metadata 长时间不更新，优先查看 backlog、oldest age 和 failures。

### Blob GC

| 指标 | 类型 | 说明 |
| --- | --- | --- |
| `nexus_plus_blob_gc_backlog` | gauge | 等待 GC 的软删除 blob 数 |
| `nexus_plus_blob_gc_deleted_bytes_total` | counter | GC 删除的 blob 字节数 |
| `nexus_plus_blob_gc_reconcile_scanned_total` | counter | 孤儿 blob reconcile 扫描行数 |
| `nexus_plus_blob_gc_reconcile_marked_total` | counter | reconcile 标记的孤儿 blob 数 |
| `nexus_plus_blob_unreferenced_reconcile_cursor` | gauge | 孤儿 blob reconcile 扫描游标 |

### 共享缓存和限流

| 指标 | 类型 | 说明 |
| --- | --- | --- |
| `nexus_plus_cache_requests_total` | counter | 共享 TTL cache 请求数 |
| `nexus_plus_cache_operation_duration_seconds_*` | timer | cache 操作延迟 |
| `nexus_plus_cache_scan_deleted_keys_total` | counter | prefix scan 删除的 cache key 数 |
| `nexus_plus_rate_limit_blocked_total` | counter | 被限流拦截的请求数 |

## 告警建议

以下 PromQL 仅作为起点，生产阈值需要按业务流量和仓库规模调整。

### 实例不可抓取

```promql
up{job="nexus-plus"} == 0
```

### 仓库请求 5xx

```promql
sum(rate(nexus_plus_repository_requests_total{outcome="server_error"}[5m])) > 0
```

### 仓库请求延迟过高

```promql
histogram_quantile(
  0.95,
  sum(rate(nexus_plus_repository_request_duration_seconds_bucket[5m])) by (le, repo, method, operation)
) > 2
```

### Proxy 回源异常

```promql
sum(rate(nexus_plus_proxy_remote_requests_total{outcome=~"server_error|error"}[5m])) by (repo, remote_host) > 0
```

### Blob 存储错误

```promql
sum(rate(nexus_plus_blob_storage_operations_total{outcome="error"}[5m])) by (store, engine, op) > 0
```

### 后台队列积压

```promql
nexus_plus_metadata_rebuild_oldest_age_seconds > 300
or
nexus_plus_repository_index_rebuild_oldest_age_seconds > 300
```

### 限流拦截

```promql
sum(rate(nexus_plus_rate_limit_blocked_total[5m])) by (type) > 0
```

## 日志排查

仓库请求的非成功状态默认会记录日志，可通过以下配置控制：

```properties
nexus-plus.repository.log-non-success-requests=true
nexus-plus.repository.log-non-success-request-excluded-statuses=477,488
```

对应环境变量：

```bash
NEXUS_PLUS_REPOSITORY_LOG_NON_SUCCESS_REQUESTS=true
NEXUS_PLUS_REPOSITORY_LOG_NON_SUCCESS_REQUEST_EXCLUDED_STATUSES=477,488
```

排查建议：

- 客户端拉包慢：先看 `Repository Request Latency`，再看 `Proxy Remote Latency` 和 `Blob Storage Latency`。
- proxy 仓库失败：看 `nexus_plus_proxy_remote_requests_total` 的 `remote_host`、`status`、`outcome`。
- 上传慢或失败：看 `nexus_plus_repository_upload_bytes_total`、blob `put` 延迟和 blob `error`。
- browse/search 不更新：看 metadata/index rebuild backlog 和 failures。
- 大量 401/403：结合安全审计和仓库权限配置排查。

## 常见问题

### `/actuator/prometheus` 返回 404

确认访问的是管理端口，不是服务端口。默认管理端口是 `8081`，本地 `dev` profile 是 `18091`。

### Grafana 没有数据

先确认 Prometheus target 是 `UP`，再检查 dashboard 顶部的数据源和 `application` 变量。nexus-plus 默认 `application` 标签是 `nexus-plus`。

### 多副本只看到部分数据

确认 Prometheus 抓取了所有副本的管理端口，并保留 `instance`、`pod` 或等价标签。聚合查询通常需要按业务标签聚合，同时保留副本维度用于排查单点异常。
