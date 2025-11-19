# Benchmarking & KPI Guide

**Author:** Mayank Mayur  
**Project:** M.Tech Dissertation

---

Use this guide to design experiments, capture runtime metrics, and compare Cloud Run Jobs versus GKE for the Spring Batch workload.

### 1. KPI definitions

| KPI | Description | Metric sources |
| --- | ----------- | -------------- |
| Latency | End-to-end job runtime (p50/p95) | `benchmark-results.csv`, `run.googleapis.com/job/execution_times`, `batch.job.duration.seconds` |
| Throughput | Records processed per second | `batch.records.processed` counter ÷ runtime |
| Cost per run | Cloud Run job execution cost vs. GKE pod minutes | Cloud Billing export, `cost` BigQuery dataset |
| Elasticity | Time to scale from zero to steady-state | Cold-start logs, Cloud Run metrics `run.googleapis.com/container/startup_latencies` |
| Operability | Effort to configure, observe, and recover failures | Qualitative notes, incident logs |
| Resource efficiency | CPU/memory utilization | Cloud Monitoring: `run.googleapis.com/container/cpu/allocation_time`, `kubernetes.io/container/memory/bytes_used` |

Document target values or pass/fail thresholds in your dissertation plan so trade-offs are explicit.

### 2. Preparing datasets

1. Generate CSVs locally (`tools/generate-data.sh`) or ingest real workloads.
2. Upload each dataset to GCS with semantic names (e.g., `datasets/sample-10k.csv`, `datasets/sample-100k.csv`).
3. Record metadata (rows, size, schema) in a spreadsheet to map to KPI analysis later.

### 3. Running the benchmark matrix

1. Ensure Cloud Run Job & GKE scripts are configured with:
   - `PROJECT_ID`, `REGION`
   - `IMAGE` (Artifact Registry URL)
   - Cloud SQL / DB credentials
   - `SERVICE_ACCOUNT_EMAIL` (Cloud Run) and `GCP_SA_KEY_PATH` or Workload Identity (GKE)
2. Export matrix parameters (example):
   ```bash
   PAYLOAD_FILES="gs://batch-benchmark-data/datasets/sample-10k.csv gs://batch-benchmark-data/datasets/sample-100k.csv"
   CHUNK_SIZES="100 500"
   GRID_SIZES="2 4"
   RETRY_ATTEMPTS="1 3"
   RUNS_PER_COMBINATION=2
   ```
3. Execute the harness:
   ```bash
   PROJECT_ID="your-project" \
   REGION="us-central1" \
   IMAGE="us-central1-docker.pkg.dev/your-project/spring-batch-repo/spring-batch-benchmark:latest" \
   SPRING_DATASOURCE_URL="jdbc:postgresql:///..." \
   ./scripts/run_benchmarks.sh
   ```
4. Inspect `benchmark-results.csv`. Each row captures:
   - Platform (cloud-run/gke)
   - Dataset path
   - Chunk size / grid size / retry attempts
   - Start & end timestamps
   - Duration (seconds)
   - Success/failure status

### 4. Capturing Cloud Monitoring metrics

Enable Stackdriver export by setting `STACKDRIVER_METRICS_ENABLED=true` and `GCP_PROJECT_ID` when deploying. Then use either Looker Studio dashboards or CLI queries:

```bash
# Cloud Run execution latency (p95) over past 1h
gcloud monitoring time-series list \
  --filter='metric.type="run.googleapis.com/job/execution_times" AND resource.labels.service_name="spring-batch-benchmark-job"' \
  --project="${PROJECT_ID}" \
  --format=json > cloud_run_latency.json

# GKE CPU utilization for the job pod
gcloud monitoring time-series list \
  --filter='metric.type="kubernetes.io/container/cpu/core_usage_time" AND resource.labels.container_name="spring-batch-benchmark"' \
  --project="${PROJECT_ID}" \
  --format=json > gke_cpu.json
```

For Micrometer counters (`batch.records.processed`, `batch.job.duration.seconds`), Cloud Monitoring stores them under the custom metric namespace `custom.googleapis.com`. Query via:
```bash
gcloud monitoring time-series list \
  --filter='metric.type="custom.googleapis.com/batch.records.processed"' \
  --project="${PROJECT_ID}" \
  --format=json > records_processed.json
```

### 5. Cost tracking

1. Enable Billing export to BigQuery (`docs/gcp-setup.md`).
2. Create views per platform, e.g.:
   ```sql
   SELECT
     service.description AS service,
     project.name,
     SUM(cost) AS usd_cost,
     SUM(usage.amount) AS usage_qty
   FROM `${PROJECT_ID}.billing_export.gcp_billing_export_v1_*`
   WHERE usage_start_time BETWEEN @start AND @end
     AND project.name = @projectId
   GROUP BY service, project.name;
   ```
3. Join cost data with `benchmark-results.csv` by timestamp to derive cost per run.

### 6. Analysis workflow

1. Load CSV + metric exports into a notebook:
   ```python
   import pandas as pd
   df = pd.read_csv("benchmark-results.csv", parse_dates=["start_time","end_time"])
   ```
2. Compute derived metrics:
   - Throughput = records_processed / duration
   - Cost per 1k records = cost / (records_processed / 1000)
3. Plot comparisons (Seaborn):
   ```python
   import seaborn as sns
   sns.boxplot(data=df, x="platform", y="duration_seconds", hue="dataset_path")
   ```
4. Summarize trade-offs:
   - Cloud Run: minimal ops, higher cold-start impact, pay-per-execution.
   - GKE: greater control/autoscaling, but cluster management and potential idle cost.
5. Feed findings back into workload tuning (chunk size, partition grid, retry). Update `RUNS_PER_COMBINATION` and rerun targeted tests after each change.

### 7. Reporting checklist

- Attach `benchmark-results.csv`, Cloud Monitoring exports, and cost queries to the dissertation appendix.
- Include dashboard screenshots (Cloud Monitoring, Looker Studio).
- Document parameter sets that led to best performance and note any anomalies/failures captured in logs.

Following this guide ensures reproducible experiments, rich KPI coverage, and clear trade-off analysis between Cloud Run Jobs and GKE.

---
*This project was developed by Mayank Mayur as part of an M.Tech dissertation.*
