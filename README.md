# Spring Batch Benchmark – Cloud Run vs GKE

**Author:** Mayank Mayur  
**Project:** M.Tech Dissertation

---

## Project Overview

This repository packages a Spring Boot + Spring Batch workload to benchmark two deployment targets on Google Cloud:

- **Cloud Run Jobs** (fully managed serverless)
- **GKE Autopilot** (Kubernetes Job)

It includes automation to build/push images, deploy to each platform, run benchmark matrices, capture metrics, and analyse KPIs such as latency, throughput, elasticity, cost, and operability.

---

## 1. Build locally

- **Maven:** `mvn clean package -DskipTests`
- **Docker (multi-stage):** `docker build -t spring-batch-benchmark -f docker/Dockerfile .`

Jar output: `target/spring-batch-probe-1.0.0.jar`

---

## 2. Prerequisites & data

1. **GCP project** (free-tier or trial credits).
2. **gcloud CLI** authenticated (`gcloud auth login`, `gcloud config set project <PROJECT_ID>`).
3. **Cloud SQL (PostgreSQL) + GCS bucket:** follow `docs/gcp-setup.md` to provision:
   - Cloud SQL instance (`db-f1-micro`), database, and user.
   - GCS bucket for CSV datasets (upload via `scripts/upload_data_to_gcs.sh`).
4. **Service accounts & IAM:** create dedicated service accounts with the roles listed in `docs/gcp-setup.md` and pass them to the deployment scripts (`SERVICE_ACCOUNT_EMAIL`, `GCP_SA_KEY_PATH`).

> **Cloud SQL connection string**
>
> ```
> SPRING_DATASOURCE_URL="jdbc:postgresql:///${DB_NAME}?socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=${PROJECT_ID}:${REGION}:${INSTANCE}&user=${DB_USER}&password=${DB_PASS}"
> ```
>
> Export this before running the deployment scripts to force Cloud SQL connectivity.

---

## 3. Build & push image (Artifact Registry)

```bash
cd /Users/mayankmayur/Cursor/spring-batch-benchmark
PROJECT_ID="your-project-id" \
REGION="us-central1" \
REPO_NAME="spring-batch-repo" \
IMAGE_NAME="spring-batch-benchmark" \
IMAGE_TAG="latest" \
./scripts/build_and_push_image.sh
```

Outputs `us-central1-docker.pkg.dev/<PROJECT_ID>/spring-batch-repo/spring-batch-benchmark:latest`.

---

## 4. Deploy platforms

### 4.1 Cloud Run Job

```bash
PROJECT_ID="your-project-id" \
REGION="us-central1" \
IMAGE="us-central1-docker.pkg.dev/your-project-id/spring-batch-repo/spring-batch-benchmark:latest" \
SERVICE_ACCOUNT_EMAIL="batch-cloud-run-sa@your-project-id.iam.gserviceaccount.com" \
SPRING_DATASOURCE_URL="jdbc:postgresql:///..." \
INPUT_FILE="gs://batch-benchmark-data/datasets/sample-10k.csv" \
STACKDRIVER_METRICS_ENABLED=true \
./scripts/deploy_cloud_run_job.sh

gcloud run jobs execute spring-batch-benchmark-job \
  --region=us-central1 \
  --project=your-project-id
```

Key env vars (`deploy_cloud_run_job.sh` handles defaults):

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` or (`DB_HOST`, `DB_PORT`, etc.) | Cloud SQL connectivity |
| `INPUT_FILE` | CSV path (supports `gs://`) |
| `CHUNK_SIZE`, `PARTITION_GRID`, `RETRY_MAX_ATTEMPTS` | Performance tuning |
| `STACKDRIVER_METRICS_ENABLED` | Enables Cloud Monitoring export |

### 4.2 GKE Autopilot Job

```bash
PROJECT_ID="your-project-id" \
REGION="us-central1" \
CLUSTER_NAME="spring-batch-benchmark" \
./scripts/create_gke_cluster.sh

IMAGE="us-central1-docker.pkg.dev/your-project-id/spring-batch-repo/spring-batch-benchmark:latest" \
SPRING_DATASOURCE_URL="jdbc:postgresql:///..." \
INPUT_FILE="gs://batch-benchmark-data/datasets/sample-10k.csv" \
STACKDRIVER_METRICS_ENABLED=true \
GCP_SA_KEY_PATH="/path/to/batch-gke-sa-key.json" \
./scripts/deploy_gke_job.sh

kubectl -n batch-benchmark logs -f job/spring-batch-benchmark-job
```

The script:

- Creates namespace + DB credentials secret.
- Optionally mounts a service account key (`GCP_SA_KEY_PATH`) for Cloud SQL access.
- Injects tuning/env vars identical to the Cloud Run deployment.

> Delete the Autopilot cluster when finished:
> `gcloud container clusters delete spring-batch-benchmark --region us-central1 --project your-project-id`

---

## 5. Benchmark matrix & KPIs

Use `docs/benchmarking-guide.md` for detailed methodology. Quick start:

```bash
PAYLOAD_FILES="gs://batch-benchmark-data/datasets/sample-10k.csv gs://batch-benchmark-data/datasets/sample-100k.csv" \
CHUNK_SIZES="100 500" \
GRID_SIZES="2 4" \
RETRY_ATTEMPTS="1 3" \
RUNS_PER_COMBINATION=2 \
PROJECT_ID="your-project-id" \
REGION="us-central1" \
./scripts/run_benchmarks.sh
```

The harness:

1. Redeploys Cloud Run + GKE jobs per combination (dataset, chunk size, partition grid, retry attempts).
2. Executes each platform, waits for completion, and records:
   - Platform, dataset, tuning parameters.
   - Start/end timestamps, duration, success/failure.
3. Outputs `benchmark-results.csv` ready for Pandas/Looker Studio analysis.

KPIs to compute (see guide):

- Latency (duration columns, or Cloud Monitoring metric `run.googleapis.com/job/execution_times`).
- Throughput (join `benchmark-results.csv` with the `batch.records.processed` custom metric).
- Cost (Billing export to BigQuery).
- Elasticity (startup latency metrics).
- Operability (log review, failure counts).

---

## 6. Metrics, logging, and observability

- Micrometer exposes Prometheus + Stackdriver registries. Enable Google export via:
  - `STACKDRIVER_METRICS_ENABLED=true`
  - `GCP_PROJECT_ID=<project>`
- Key custom metrics:
  - `batch.job.duration.seconds`
  - `batch.step.duration.seconds`
  - `batch.records.processed`
  - `batch.failure.count`
- Query Cloud Monitoring for platform metrics (examples in `docs/benchmarking-guide.md`).
- Logging:
  - JSON Logback appender (with MDC fields `jobInstanceId`, `jobExecutionId`, `jobName`).
  - `LoggingStepExecutionListener` + `JobRunLoggingListener` provide structured start/finish logs per step/job.
  - Filter logs in Cloud Logging with:
    ```
    resource.type="cloud_run_job"
    labels.job_name="spring-batch-benchmark-job"
    ```

---

## 7. Local development & testing

- Run Postgres & the app locally: `docker-compose up -d && ./run_local.sh`
- Generate synthetic data: `./tools/generate-data.sh 50000`
- Tests: `mvn test`

---

## 8. Cleanup (stay within free tier)

- Delete GKE cluster:
  ```bash
  gcloud container clusters delete spring-batch-benchmark \
    --region=us-central1 --project=your-project-id
  ```
- Delete Cloud Run Job:
  ```bash
  gcloud run jobs delete spring-batch-benchmark-job \
    --region=us-central1 --project=your-project-id
  ```
- Remove unused Cloud SQL databases, images, and GCS objects once experiments conclude.

---

## 9. Reference docs

- `docs/gcp-setup.md` – Cloud SQL, GCS, IAM, cost controls.
- `docs/benchmarking-guide.md` – KPI definitions, monitoring queries, analysis workflow.

These documents, plus the scripts under `scripts/`, provide the end-to-end workflow needed for the MTech dissertation and reproducible benchmarking between Cloud Run Jobs and GKE.

---
*This project was developed by Mayank Mayur as part of an M.Tech dissertation.*
