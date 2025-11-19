#!/usr/bin/env bash

set -euo pipefail

# Deploys a Kubernetes Job to an existing GKE cluster to run the Spring Batch benchmark.
#
# Assumes:
#   - kubectl is configured to point at the desired cluster (see create_gke_cluster.sh)
#   - Image is already in Artifact Registry
#
# Usage:
#   NAMESPACE="batch-benchmark" IMAGE="us-central1-docker.pkg.dev/PROJECT/REPO/spring-batch-benchmark:latest" \
#   DB_HOST="postgres" DB_PORT="5432" DB_NAME="batchdb" DB_USER="batchuser" DB_PASS="batchpass" \
#   ./scripts/deploy_gke_job.sh

NAMESPACE="${NAMESPACE:-batch-benchmark}"
JOB_NAME="${JOB_NAME:-spring-batch-benchmark-job}"
IMAGE="${IMAGE:-}"

DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-batchdb}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-postgres}"
INPUT_FILE="${INPUT_FILE:-data/sample-10k.csv}"
CHUNK_SIZE="${CHUNK_SIZE:-100}"
PARTITION_GRID="${PARTITION_GRID:-4}"
RETRY_MAX_ATTEMPTS="${RETRY_MAX_ATTEMPTS:-3}"
STACKDRIVER_METRICS_ENABLED="${STACKDRIVER_METRICS_ENABLED:-false}"
GCP_PROJECT_ID="${GCP_PROJECT_ID:-}"
CLOUD_SQL_CONNECTION="${CLOUD_SQL_CONNECTION:-}"
SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-}"
GCP_SA_KEY_PATH="${GCP_SA_KEY_PATH:-}"
GCP_SA_SECRET_NAME="${GCP_SA_SECRET_NAME:-gcp-sa-key}"

if [[ -z "${IMAGE}" ]]; then
  echo "ERROR: IMAGE is not set. Provide the full Artifact Registry image URL."
  exit 1
fi

if [[ -z "${SPRING_DATASOURCE_URL}" && -n "${CLOUD_SQL_CONNECTION}" ]]; then
  SPRING_DATASOURCE_URL="jdbc:postgresql:///${DB_NAME}?socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=${CLOUD_SQL_CONNECTION}&user=${DB_USER}&password=${DB_PASS}"
fi

echo "=== Creating namespace ${NAMESPACE} if it does not exist ==="
kubectl get namespace "${NAMESPACE}" >/dev/null 2>&1 || kubectl create namespace "${NAMESPACE}"

echo "=== Creating/Updating secret for DB credentials ==="
kubectl -n "${NAMESPACE}" apply -f - <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: spring-batch-db-secret
type: Opaque
stringData:
  SPRING_DATASOURCE_USERNAME: "${DB_USER}"
  SPRING_DATASOURCE_PASSWORD: "${DB_PASS}"
EOF

if [[ -n "${GCP_SA_KEY_PATH}" ]]; then
  echo "=== Creating/Updating secret for GCP service account credentials ==="
  kubectl -n "${NAMESPACE}" create secret generic "${GCP_SA_SECRET_NAME}" \
    --from-file=sa-key.json="${GCP_SA_KEY_PATH}" \
    --dry-run=client -o yaml | kubectl apply -f -
fi

EXTRA_ENVS=""
if [[ -n "${SPRING_DATASOURCE_URL}" ]]; then
  EXTRA_ENVS+="
            - name: SPRING_DATASOURCE_URL
              value: \"${SPRING_DATASOURCE_URL}\""
fi

if [[ -n "${CLOUD_SQL_CONNECTION}" ]]; then
  EXTRA_ENVS+="
            - name: CLOUD_SQL_CONNECTION
              value: \"${CLOUD_SQL_CONNECTION}\""
fi

if [[ -n "${GCP_PROJECT_ID}" ]]; then
  EXTRA_ENVS+="
            - name: GCP_PROJECT_ID
              value: \"${GCP_PROJECT_ID}\""
fi

VOLUME_MOUNTS=""
VOLUMES=""
if [[ -n "${GCP_SA_KEY_PATH}" ]]; then
  VOLUME_MOUNTS="
          volumeMounts:
            - name: gcp-sa-key
              mountPath: /var/secrets/google
              readOnly: true"
  VOLUMES="
      volumes:
        - name: gcp-sa-key
          secret:
            secretName: ${GCP_SA_SECRET_NAME}"
  EXTRA_ENVS+="
            - name: GOOGLE_APPLICATION_CREDENTIALS
              value: /var/secrets/google/sa-key.json"
fi

echo "=== Applying Kubernetes Job ${JOB_NAME} in namespace ${NAMESPACE} ==="
kubectl -n "${NAMESPACE}" apply -f - <<EOF
apiVersion: batch/v1
kind: Job
metadata:
  name: ${JOB_NAME}
spec:
  backoffLimit: 0
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: spring-batch-benchmark
          image: ${IMAGE}
          imagePullPolicy: IfNotPresent
          env:
            - name: DB_HOST
              value: "${DB_HOST}"
            - name: DB_PORT
              value: "${DB_PORT}"
            - name: DB_NAME
              value: "${DB_NAME}"
            - name: INPUT_FILE
              value: "${INPUT_FILE}"
            - name: CHUNK_SIZE
              value: "${CHUNK_SIZE}"
            - name: PARTITION_GRID
              value: "${PARTITION_GRID}"
            - name: RETRY_MAX_ATTEMPTS
              value: "${RETRY_MAX_ATTEMPTS}"
            - name: STACKDRIVER_METRICS_ENABLED
              value: "${STACKDRIVER_METRICS_ENABLED}"
            - name: EXIT_ON_COMPLETE
              value: "true"
            - name: SPRING_BATCH_JOB_ENABLED
              value: "true"${EXTRA_ENVS}
          envFrom:
            - secretRef:
                name: spring-batch-db-secret
${VOLUME_MOUNTS}
${VOLUMES}
EOF

echo "=== Kubernetes Job applied. To watch it run: ==="
echo "kubectl -n ${NAMESPACE} logs -f job/${JOB_NAME}"


