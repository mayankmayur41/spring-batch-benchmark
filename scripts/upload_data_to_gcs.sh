#!/usr/bin/env bash

set -euo pipefail

# Helper script to upload local CSV datasets to a GCS bucket for use by Cloud Run and GKE jobs.
#
# Usage:
#   PROJECT_ID="my-project" BUCKET_NAME="batch-benchmark-data" \
#   FILE_PATH="data/sample-10k.csv" OBJECT_NAME="datasets/sample-10k.csv" \
#   ./scripts/upload_data_to_gcs.sh

PROJECT_ID="${PROJECT_ID:-}"
BUCKET_NAME="${BUCKET_NAME:-}"
REGION="${REGION:-us-central1}"
FILE_PATH="${FILE_PATH:-data/sample-10k.csv}"
OBJECT_NAME="${OBJECT_NAME:-$(basename "${FILE_PATH}")}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "ERROR: PROJECT_ID is not set."
  exit 1
fi

if [[ -z "${BUCKET_NAME}" ]]; then
  echo "ERROR: BUCKET_NAME is not set."
  exit 1
fi

if [[ ! -f "${FILE_PATH}" ]]; then
  echo "ERROR: FILE_PATH ${FILE_PATH} does not exist."
  exit 1
fi

echo "=== Enabling GCS API (if needed) ==="
gcloud services enable storage.googleapis.com --project "${PROJECT_ID}"

echo "=== Creating bucket gs://${BUCKET_NAME} (if it does not exist) ==="
gcloud storage buckets create "gs://${BUCKET_NAME}" \
  --project="${PROJECT_ID}" \
  --location="${REGION}" \
  --uniform-bucket-level-access \
  --quiet || echo "Bucket may already exist, continuing..."

echo "=== Uploading ${FILE_PATH} to gs://${BUCKET_NAME}/${OBJECT_NAME} ==="
gcloud storage cp "${FILE_PATH}" "gs://${BUCKET_NAME}/${OBJECT_NAME}"

echo "=== Upload complete. Reference this file via INPUT_FILE=gs://${BUCKET_NAME}/${OBJECT_NAME} ==="


