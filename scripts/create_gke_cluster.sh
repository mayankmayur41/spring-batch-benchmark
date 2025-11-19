#!/usr/bin/env bash

set -euo pipefail

# Creates a minimal GKE Autopilot cluster intended for short-lived benchmarking.
# This keeps costs low by:
#   - Using Autopilot (pay-per-pod, auto-scaling down)
#   - Encouraging you to delete the cluster after benchmarks
#
# Usage:
#   PROJECT_ID="my-project" REGION="us-central1" CLUSTER_NAME="spring-batch-benchmark" ./scripts/create_gke_cluster.sh

PROJECT_ID="${PROJECT_ID:-}"
REGION="${REGION:-us-central1}"
CLUSTER_NAME="${CLUSTER_NAME:-spring-batch-benchmark}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "ERROR: PROJECT_ID is not set."
  exit 1
fi

echo "=== Enabling GKE API (if not already enabled) ==="
gcloud services enable container.googleapis.com --project "${PROJECT_ID}"

echo "=== Creating Autopilot cluster ${CLUSTER_NAME} in ${REGION} ==="
gcloud container clusters create-auto "${CLUSTER_NAME}" \
  --project "${PROJECT_ID}" \
  --region "${REGION}" \
  --release-channel=stable \
  --quiet

echo "=== Getting kubectl credentials for the new cluster ==="
gcloud container clusters get-credentials "${CLUSTER_NAME}" \
  --region "${REGION}" \
  --project "${PROJECT_ID}"

echo "=== Cluster created and kubectl configured. Remember to delete it when done: ==="
echo "gcloud container clusters delete ${CLUSTER_NAME} --region ${REGION} --project ${PROJECT_ID}"


