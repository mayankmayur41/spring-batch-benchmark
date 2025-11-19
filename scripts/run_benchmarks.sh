#!/usr/bin/env bash

set -euo pipefail

# Simple benchmark harness that:
#   1. Executes the Cloud Run Job once
#   2. Executes the GKE Job once (by re-applying manifest)
#   3. Measures wall-clock time for each run
#   4. Writes a small CSV file with the results
#
# This is intentionally simple and relies on gcloud + kubectl.
# You can extend it later or call it in loops for more rigorous experiments.

RESULTS_FILE="${RESULTS_FILE:-benchmark-results.csv}"
PROJECT_ID="${PROJECT_ID:-}"
REGION="${REGION:-us-central1}"
CLOUD_RUN_JOB_NAME="${CLOUD_RUN_JOB_NAME:-spring-batch-benchmark-job}"
GKE_NAMESPACE="${GKE_NAMESPACE:-batch-benchmark}"
GKE_JOB_NAME="${GKE_JOB_NAME:-spring-batch-benchmark-job}"
PAYLOAD_FILES="${PAYLOAD_FILES:-data/sample-10k.csv}"
CHUNK_SIZES="${CHUNK_SIZES:-100}"
GRID_SIZES="${GRID_SIZES:-4}"
RETRY_ATTEMPTS="${RETRY_ATTEMPTS:-3}"
RUNS_PER_COMBINATION="${RUNS_PER_COMBINATION:-1}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "ERROR: PROJECT_ID is not set."
  exit 1
fi

echo "run_id,platform,dataset_path,chunk_size,grid_size,retry_attempts,start_time,end_time,duration_seconds,status" > "${RESULTS_FILE}"

deploy_cloud_run_variant() {
  local dataset="$1"
  local chunk="$2"
  local grid="$3"
  local retry="$4"
  INPUT_FILE="${dataset}" \
  CHUNK_SIZE="${chunk}" \
  PARTITION_GRID="${grid}" \
  RETRY_MAX_ATTEMPTS="${retry}" \
  ./scripts/deploy_cloud_run_job.sh
}

deploy_gke_variant() {
  local dataset="$1"
  local chunk="$2"
  local grid="$3"
  local retry="$4"
  INPUT_FILE="${dataset}" \
  CHUNK_SIZE="${chunk}" \
  PARTITION_GRID="${grid}" \
  RETRY_MAX_ATTEMPTS="${retry}" \
  ./scripts/deploy_gke_job.sh
}

run_cloud_run() {
  local run_id="$1"
  local dataset="$2"
  local chunk="$3"
  local grid="$4"
  local retry="$5"

  echo "=== Executing Cloud Run Job (${CLOUD_RUN_JOB_NAME}) dataset=${dataset} chunk=${chunk} grid=${grid} retry=${retry} ==="
  local start end duration status
  start=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  local start_epoch
  start_epoch=$(date +%s)

  if gcloud run jobs execute "${CLOUD_RUN_JOB_NAME}" --project "${PROJECT_ID}" --region "${REGION}" --quiet; then
    status="success"
  else
    status="failed"
  fi

  end=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  local end_epoch
  end_epoch=$(date +%s)
  duration=$((end_epoch - start_epoch))

  echo "${run_id},cloud-run,${dataset},${chunk},${grid},${retry},${start},${end},${duration},${status}" >> "${RESULTS_FILE}"
}

run_gke() {
  local run_id="$1"
  local dataset="$2"
  local chunk="$3"
  local grid="$4"
  local retry="$5"

  echo "=== Executing GKE Job (${GKE_JOB_NAME}) dataset=${dataset} chunk=${chunk} grid=${grid} retry=${retry} ==="
  local start end duration status
  start=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  local start_epoch
  start_epoch=$(date +%s)

  kubectl -n "${GKE_NAMESPACE}" delete job "${GKE_JOB_NAME}" --ignore-not-found
  deploy_gke_variant "${dataset}" "${chunk}" "${grid}" "${retry}"

  if kubectl -n "${GKE_NAMESPACE}" wait --for=condition=complete "job/${GKE_JOB_NAME}" --timeout=3600s; then
    status="success"
  else
    status="failed"
  fi

  end=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  local end_epoch
  end_epoch=$(date +%s)
  duration=$((end_epoch - start_epoch))

  echo "${run_id},gke,${dataset},${chunk},${grid},${retry},${start},${end},${duration},${status}" >> "${RESULTS_FILE}"
}

iteration=1
for dataset in ${PAYLOAD_FILES}; do
  for chunk in ${CHUNK_SIZES}; do
    for grid in ${GRID_SIZES}; do
      for retry in ${RETRY_ATTEMPTS}; do
        for ((run=1; run<=RUNS_PER_COMBINATION; run++)); do
          run_id="$(date -u +"%Y%m%dT%H%M%SZ")-${iteration}"
          deploy_cloud_run_variant "${dataset}" "${chunk}" "${grid}" "${retry}"
          run_cloud_run "${run_id}" "${dataset}" "${chunk}" "${grid}" "${retry}"

          run_id="$(date -u +"%Y%m%dT%H%M%SZ")-${iteration}"
          run_gke "${run_id}" "${dataset}" "${chunk}" "${grid}" "${retry}"

          iteration=$((iteration + 1))
        done
      done
    done
  done
done

echo "=== Benchmark matrix complete. Results written to ${RESULTS_FILE} ==="


