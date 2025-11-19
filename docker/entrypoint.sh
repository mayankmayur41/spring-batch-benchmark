#!/usr/bin/env bash
set -euo pipefail

PROXY_PID=""

cleanup() {
  if [[ -n "${PROXY_PID}" ]]; then
    if kill -0 "${PROXY_PID}" >/dev/null 2>&1; then
      kill "${PROXY_PID}" || true
    fi
  fi
}

trap cleanup EXIT

JAVA_OPTS="${JAVA_OPTS:-"-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"}"

if [[ -n "${CLOUD_SQL_CONNECTION:-}" ]]; then
  PROXY_PORT="${CLOUD_SQL_PROXY_PORT:-5432}"
  echo "Starting Cloud SQL Proxy for ${CLOUD_SQL_CONNECTION} on port ${PROXY_PORT}..."
  /app/cloud-sql-proxy --port "${PROXY_PORT}" "${CLOUD_SQL_CONNECTION}" &
  PROXY_PID=$!

  # Wait for proxy port to become available (max ~30 seconds)
  for i in $(seq 1 30); do
    if nc -z localhost "${PROXY_PORT}" >/dev/null 2>&1; then
      break
    fi
    sleep 1
    if [[ "${i}" == "30" ]]; then
      echo "Cloud SQL Proxy did not become ready in time" >&2
      exit 1
    fi
  done

  : "${DB_NAME:=batchdb}"
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:${PROXY_PORT}/${DB_NAME}}"
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-${DB_USER:-postgres}}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-${DB_PASS:-postgres}}"
fi

exec java ${JAVA_OPTS} -jar /app/app.jar

