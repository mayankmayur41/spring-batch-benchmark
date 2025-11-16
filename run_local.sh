#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Starting local Spring Batch probe setup...${NC}"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

# Define the named volume for PostgreSQL data
PG_VOLUME_NAME="spring-batch-benchmark-pg-data"

# --- Clean up previous runs ---
echo -e "${YELLOW}Stopping and removing any existing PostgreSQL container and its data...${NC}"
docker stop postgres > /dev/null 2>&1 || true
docker rm -v postgres > /dev/null 2>&1 || true

# --- Start new PostgreSQL container ---
echo -e "${YELLOW}Starting a new PostgreSQL container on port 5433 with fresh data...${NC}"
docker run -d --name postgres \
    -e POSTGRES_DB=batchdb \
    -e POSTGRES_USER=postgres \
    -e POSTGRES_PASSWORD=postgres \
    -p 5433:5432 \
    -v "${PG_VOLUME_NAME}":/var/lib/postgresql/data \
    postgres:15-alpine

# Wait for PostgreSQL to be ready
echo -e "${YELLOW}Waiting for PostgreSQL to be ready...${NC}"
sleep 20

# Create data directory if it doesn't exist
mkdir -p data

# Generate sample data if needed, using the standalone data generation mode
if [ ! -f "data/sample-10k.csv" ]; then
    echo -e "${YELLOW}Generating 10k sample records...${NC}"
    # This now runs a plain Java method, not a full Spring application
    ./mvnw compile exec:java -Dexec.mainClass="com.mayank.batch.SpringBatchBenchmarkApplication" \
        -Dexec.args="generate-data 10000" -q
fi

# Build the application
echo -e "${YELLOW}Building the application...${NC}"
./mvnw clean package -DskipTests

# Run the batch job (this is where the schema will be initialized)
echo -e "${YELLOW}Running batch job...${NC}"
java -jar target/spring-batch-probe-1.0.0.jar \
    --input.file=data/sample-10k.csv \
    --partition.grid=4 \
    --chunk.size=100 \
    --exit.on.complete=true

echo -e "${GREEN}Batch job completed!${NC}"

# Optional: Check metrics endpoint
echo -e "${YELLOW}Checking metrics endpoint...${NC}"
curl -s http://localhost:8080/actuator/prometheus | grep "batch_"

echo -e "${GREEN}Local setup completed successfully!${NC}"
