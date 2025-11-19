# GCP Setup Guide (Cloud SQL + GCS + IAM)

**Author:** Mayank Mayur  
**Project:** M.Tech Dissertation

---

Use this guide to configure the minimum Google Cloud resources needed for benchmarking the Spring Batch workload on Cloud Run Jobs and GKE while staying within free-tier credits.

### 1. Cloud SQL for PostgreSQL

1. **Create the instance (shared-core keeps cost minimal):**
   ```bash
   PROJECT_ID="your-project-id"
   REGION="us-central1"
   INSTANCE_NAME="batch-benchmark-sql"
   DB_NAME="batchdb"
   DB_USER="batchuser"
   DB_PASS="batchpass"

   gcloud sql instances create "${INSTANCE_NAME}" \
     --project="${PROJECT_ID}" \
     --database-version=POSTGRES_14 \
     --tier=db-f1-micro \
     --region="${REGION}" \
     --storage-size=10 \
     --root-password="${DB_PASS}"

   gcloud sql databases create "${DB_NAME}" --instance="${INSTANCE_NAME}" --project="${PROJECT_ID}"
   gcloud sql users create "${DB_USER}" --instance="${INSTANCE_NAME}" --password="${DB_PASS}" --project="${PROJECT_ID}"
   ```

2. **Allow access:**
   - For **Cloud Run**, you only need the Cloud SQL Admin API and the Cloud SQL Java socket factory (already added in `pom.xml`). No public IP/firewall changes required.
   - For **GKE**, either:
     - Enable Workload Identity and grant the Kubernetes service account the `roles/cloudsql.client` role.
     - Or mount a service-account key (see `deploy_gke_job.sh`) and ensure the service account has `roles/cloudsql.client`.

3. **Datasource URL for Cloud SQL:**
   ```
   SPRING_DATASOURCE_URL="jdbc:postgresql:///${DB_NAME}?socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=${PROJECT_ID}:${REGION}:${INSTANCE_NAME}&user=${DB_USER}&password=${DB_PASS}"
   ```

   Export this value before running the deployment scripts so both Cloud Run and GKE pods connect via the secure socket factory.

### 2. GCS bucket for input CSVs

1. Create a storage bucket:
   ```bash
   BUCKET_NAME="batch-benchmark-data"
   gcloud storage buckets create "gs://${BUCKET_NAME}" \
     --project="${PROJECT_ID}" \
     --location="us-central1" \
     --uniform-bucket-level-access
   ```

2. Upload datasets (or use `scripts/upload_data_to_gcs.sh`):
   ```bash
   FILE_PATH="data/sample-10k.csv"
   OBJECT_NAME="datasets/sample-10k.csv"
   ./scripts/upload_data_to_gcs.sh
   # Bucket, project, and file inputs are controlled by environment variables.
   ```

3. Reference the uploaded file via `INPUT_FILE=gs://${BUCKET_NAME}/datasets/sample-10k.csv` when deploying jobs.

### 3. Service accounts & IAM

| Component        | Service Account Example           | Required Roles                                                                                                  |
|------------------|-----------------------------------|------------------------------------------------------------------------------------------------------------------|
| Cloud Run Job    | `batch-cloud-run-sa@PROJECT.iam.gserviceaccount.com` | `roles/run.invoker` (optional), `roles/cloudsql.client`, `roles/storage.objectViewer`, `roles/logging.logWriter`, `roles/monitoring.metricWriter` |
| GKE Workload     | `batch-gke-sa@PROJECT.iam.gserviceaccount.com`       | `roles/cloudsql.client`, `roles/storage.objectViewer`, `roles/logging.logWriter`, `roles/monitoring.metricWriter`                                    |
| Terraform/CI (optional) | `batch-infra-sa@PROJECT.iam.gserviceaccount.com` | `roles/editor` or granular IAM for Artifact Registry, Cloud Run, GKE, Cloud SQL                                  |

Commands:
```bash
gcloud iam service-accounts create batch-cloud-run-sa --project="${PROJECT_ID}"
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:batch-cloud-run-sa@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"
# Repeat for other roles listed above.
```

When deploying Cloud Run Jobs pass `SERVICE_ACCOUNT_EMAIL="batch-cloud-run-sa@${PROJECT_ID}.iam.gserviceaccount.com"` to `deploy_cloud_run_job.sh`.  
For GKE, either bind the service account using Workload Identity or export a key and set `GCP_SA_KEY_PATH=/path/to/key.json` before calling `deploy_gke_job.sh`.

### 4. Monitoring & Logging APIs

Enable the following APIs once per project (deployment scripts already attempt this, but running manually avoids surprises):
```bash
gcloud services enable run.googleapis.com container.googleapis.com sqladmin.googleapis.com \
  monitoring.googleapis.com logging.googleapis.com storage.googleapis.com \
  --project "${PROJECT_ID}"
```

### 5. Cost control checklist

- Use **Autopilot** GKE and delete the cluster (`gcloud container clusters delete ...`) right after benchmarks.
- Stop Cloud Run Jobs and delete them when not in use.
- Export billing data to BigQuery (optional) to attribute cost per run:
  ```bash
  BILLING_ACCOUNT="XXXXXX-XXXXXX-XXXXXX"
  DATASET="billing_export"
  gcloud beta billing accounts \
    links create "billingAccounts/${BILLING_ACCOUNT}" \
    --dataset="${PROJECT_ID}:${DATASET}"
  ```
- Store datasets in a single-region bucket and clean up once reporting is complete.

Following these steps ensures Cloud SQL + GCS + IAM are configured securely while staying within free-tier or trial credit limits.

---
*This project was developed by Mayank Mayur as part of an M.Tech dissertation.*
