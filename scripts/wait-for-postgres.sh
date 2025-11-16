#!/usr/bin/env sh
# Wait-for script to pause until Postgres is ready
set -e

host="${DB_HOST:-postgres}"
port="${DB_PORT:-5432}"

echo "Waiting for postgres at $host:$port..."

for i in 1 2 3 4 5 6 7 8 9 10; do
  if pg_isready -h "$host" -p "$port" -U "${SPRING_DATASOURCE_USERNAME:-postgres}" >/dev/null 2>&1; then
    echo "Postgres is ready"
    exit 0
  fi
  echo "Waiting for postgres... ($i)"
  sleep 1
done

echo "Postgres did not become available in time"
exit 1

