#!/usr/bin/env bash

set -euo pipefail

# Simple helper script to build the Spring Batch image and push it to Artifact Registry.
# This is designed for low GCP experience and to minimise manual steps.
#
# Prerequisites:
#   - gcloud CLI installed and authenticated
#   - Docker installed and configured to work with gcloud
#   - A GCP project with Artifact Registry API enabled
#
# Usage:
#   PROJECT_ID="my-project" REGION="us-central1" REPO_NAME="spring-batch-repo" ./scripts/build_and_push_image.sh

PROJECT_ID="${PROJECT_ID:-}"
REGION="${REGION:-us-central1}"
REPO_NAME="${REPO_NAME:-spring-batch-repo}"
IMAGE_NAME="${IMAGE_NAME:-spring-batch-benchmark}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "ERROR: PROJECT_ID is not set. Please export PROJECT_ID or pass it inline."
  exit 1
fi

FULL_REPO="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO_NAME}"
FULL_IMAGE="${FULL_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"

echo "=== Building Spring Batch jar with Maven (skip tests) ==="
mvn -B -DskipTests clean package

echo "=== Building Docker image ${FULL_IMAGE} ==="
docker build -t "${FULL_IMAGE}" -f docker/Dockerfile .

echo "=== Configuring Docker to use gcloud credentials ==="
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet

echo "=== Ensuring Artifact Registry API is enabled ==="
gcloud services enable artifactregistry.googleapis.com --project "${PROJECT_ID}"

echo "=== Creating Artifact Registry repo if it does not exist ==="
gcloud artifacts repositories create "${REPO_NAME}" \
  --repository-format=docker \
  --location="${REGION}" \
  --description="Spring Batch benchmark images" \
  --project="${PROJECT_ID}" \
  --quiet || echo "Repository ${REPO_NAME} may already exist, continuing..."

echo "=== Pushing image to Artifact Registry ==="
docker push "${FULL_IMAGE}"

echo "=== Done. Image pushed: ${FULL_IMAGE} ==="
echo "You can now use this image for Cloud Run Jobs and GKE deployments."


