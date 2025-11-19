#!/usr/bin/env bash

set -euo pipefail

# Deploys or updates a Cloud Run Job for the Spring Batch benchmark.
#
# Prerequisites:
#   - gcloud CLI installed and authenticated
#   - Cloud Run API enabled
#   - Image already pushed to Artifact Registry (see build_and_push_image.sh)

PROJECT_ID="${PROJECT_ID:-}"
REGION="${REGION:-us-central1}"
JOB_NAME="${JOB_NAME:-spring-batch-benchmark-job}"
IMAGE="${IMAGE:-}"
SERVICE_ACCOUNT_EMAIL="${SERVICE_ACCOUNT_EMAIL:-}"
CLOUD_SQL_CONNECTION="${CLOUD_SQL_CONNECTION:-}"

# Database connectivity configuration. For Cloud Run + Cloud SQL, the
# recommended pattern for this repo is:
#   - Use the Cloud SQL connector (via --set-cloudsql-instances)
#   - Let Spring Cloud GCP build the JDBC URL via instance-connection-name
#   - Provide DB_USER / DB_PASS / DB_NAME as env vars
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-postgres}"
DB_NAME="${DB_NAME:-batchdb}"

# Default input file for Cloud Run benchmarks: GCS object
# You can override this by exporting INPUT_FILE before invoking the script.
INPUT_FILE="${INPUT_FILE:-gs://mayank_bucket-271999/datasets/sample-10k.csv}"
CHUNK_SIZE="${CHUNK_SIZE:-100}"
PARTITION_GRID="${PARTITION_GRID:-4}"
RETRY_MAX_ATTEMPTS="${RETRY_MAX_ATTEMPTS:-3}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "ERROR: PROJECT_ID is not set."
  exit 1
fi

if [[ -z "${IMAGE}" ]]; then
  echo "ERROR: IMAGE is not set. Provide the full Artifact Registry image URL."
  exit 1
fi

if [[ -z "${CLOUD_SQL_CONNECTION}" ]]; then
  echo "ERROR: CLOUD_SQL_CONNECTION is not set."
  exit 1
fi

echo "=== Enabling required APIs (Run, SQL Admin, Monitoring, Storage) ==="
gcloud services enable run.googleapis.com sqladmin.googleapis.com monitoring.googleapis.com storage.googleapis.com --project "${PROJECT_ID}"

# Set environment variables for the Cloud Run job
# The 'cloudrun' profile will configure the database and metrics correctly
# using Spring Cloud GCP. We pass in:
#   - SPRING_PROFILES_ACTIVE=cloudrun
#   - DB_USER / DB_PASS / DB_NAME for DB credentials
#   - CLOUD_SQL_CONNECTION for both the connector and Spring Cloud GCP
#   - gcp.project.id so Spring Cloud GCP knows the project
ENV_VARS=(
  "SPRING_PROFILES_ACTIVE=cloudrun"
  "SPRING_DATASOURCE_USERNAME=${DB_USER}"
  "SPRING_DATASOURCE_PASSWORD=${DB_PASS}"
  "DB_NAME=${DB_NAME}"
  "CLOUD_SQL_CONNECTION=${CLOUD_SQL_CONNECTION}"
  "gcp.project.id=${PROJECT_ID}"
  "INPUT_FILE=${INPUT_FILE}"
  "CHUNK_SIZE=${CHUNK_SIZE}"
  "PARTITION_GRID=${PARTITION_GRID}"
  "RETRY_MAX_ATTEMPTS=${RETRY_MAX_ATTEMPTS}"
  "EXIT_ON_COMPLETE=true"
)

ENV_VARS_STR=$(IFS=','; echo "${ENV_VARS[*]}")

CMD=(gcloud run jobs deploy "${JOB_NAME}"
  --project "${PROJECT_ID}"
  --region "${REGION}"
  --image "${IMAGE}"
  --max-retries=0
  --memory=1Gi
  --cpu=1
  --task-timeout=3600s
  --set-env-vars "${ENV_VARS_STR}"
  # Connect the job to the Cloud SQL instance
#  --set-cloudsql-instances "${CLOUD_SQL_CONNECTION}"
  --quiet)

if [[ -n "${SERVICE_ACCOUNT_EMAIL}" ]]; then
  CMD+=(--service-account "${SERVICE_ACCOUNT_EMAIL}")
fi

echo "=== Deploying Cloud Run Job ${JOB_NAME} in ${REGION} ==="
"${CMD[@]}"

echo "=== Cloud Run Job deployed. Execute with: ==="
echo "gcloud run jobs execute ${JOB_NAME} --region=${REGION} --project=${PROJECT_ID}"
