# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Core commands

### Build
- Maven wrapper (preferred): `./mvnw clean package -DskipTests`
- System Maven: `mvn clean package -DskipTests`
- Docker image (local): `docker build -t spring-batch-benchmark -f docker/Dockerfile .`

Jar output: `target/spring-batch-probe-1.0.0.jar`.

### Tests
- All tests: `./mvnw test`
- Single test class: `./mvnw -Dtest=EndToEndJobTest test`
- Single test method: `./mvnw -Dtest=TransformProcessorTest#testRecordTransformation test`

Tests use Testcontainers with PostgreSQL; Docker must be available for integration tests to pass.

### Local development
- End-to-end local run (build + Postgres via Docker + job execution + metrics check):
  - `./run_local.sh`
- Generate synthetic CSV data (standalone helper script):
  - `./tools/generate-data.sh 50000`
- Alternative containerized setup (Postgres + app):
  - `docker-compose up -d`

> Note: `run_local.sh` starts its own `postgres` container on port `5433`. Avoid running the Docker Compose `postgres` service at the same time on the same port.

### Cloud image build & deployment
For Google Cloud benchmarking flows (Cloud Run Jobs and GKE Autopilot), use the scripts under `scripts/` as orchestrators. Typical entrypoints (set env vars as in `README.md`):

- Build & push to Artifact Registry:
  - `PROJECT_ID=... REGION=... REPO_NAME=... IMAGE_NAME=... IMAGE_TAG=latest ./scripts/build_and_push_image.sh`
- Deploy Cloud Run Job:
  - `PROJECT_ID=... REGION=... IMAGE=... SERVICE_ACCOUNT_EMAIL=... SPRING_DATASOURCE_URL=... INPUT_FILE=... STACKDRIVER_METRICS_ENABLED=true ./scripts/deploy_cloud_run_job.sh`
- Deploy GKE Autopilot Job:
  - `PROJECT_ID=... REGION=... CLUSTER_NAME=... ./scripts/create_gke_cluster.sh`
  - `IMAGE=... SPRING_DATASOURCE_URL=... INPUT_FILE=... STACKDRIVER_METRICS_ENABLED=true GCP_SA_KEY_PATH=... ./scripts/deploy_gke_job.sh`
- Run benchmark matrix across Cloud Run + GKE (multiple datasets and tunings):
  - `PAYLOAD_FILES="..." CHUNK_SIZES="..." GRID_SIZES="..." RETRY_ATTEMPTS="..." RUNS_PER_COMBINATION=2 PROJECT_ID=... REGION=... ./scripts/run_benchmarks.sh`

See `README.md` and `docs/gcp-setup.md`, `docs/benchmarking-guide.md` for the authoritative combinations of environment variables and KPIs.

## High-level architecture

### Overall purpose
This application is a Spring Boot + Spring Batch probe used to benchmark batch-processing workloads across two Google Cloud deployment targets:
- Cloud Run Jobs
- GKE Autopilot Kubernetes Jobs

The core behavior is: read records from a CSV file, partition the workload, transform each record, write to PostgreSQL, and emit rich metrics and structured logs for benchmarking latency, throughput, and reliability.

### Entry point and execution modes
- `com.mayank.batch.SpringBatchBenchmarkApplication` is the main Spring Boot entry point.
- It supports two modes based on command-line arguments:
  - **Data generation mode** (`generate-data <count>`):
    - Runs a lightweight Java data generator without starting the full Spring context.
    - Writes a CSV (e.g., `data/sample-10k.csv`) with synthetic records.
  - **Batch job execution mode** (default):
    - Starts the Spring Boot context and triggers the configured Spring Batch job (`probeJob`) via `CommandLineRunner`, unless `spring.batch.job.enabled=false`.
    - Uses job parameters:
      - `inputFile` (from `--input.file=` CLI arg or `input.file` property, default `data/sample-10k.csv`).
      - `timestamp` for unique job instances.
    - Optionally exits the JVM after completion when `exit.on.complete=true`, which is important for Cloud Run/GKE Job semantics.

### Batch job pipeline
The pipeline is defined in `com.mayank.batch.config.BatchConfig` and related components under `com.mayank.batch`:

- **Job definition (`probeJob`)**
  - Name: `probeJob`.
  - Starts with `masterStep`.
  - Attaches `JobRunLoggingListener` and `BatchMetricsListener` for logging and metrics.

- **Master step (`masterStep`)**
  - Implements a partitioned step using `RangePartitioner`.
  - Uses `gridSize` (configurable via `partition.grid`, default `4`) to determine how many partitions to create.
  - Delegates to `slaveStep` for actual processing, running partitions concurrently via a `SimpleAsyncTaskExecutor` configured with `gridSize` as concurrency limit.

- **Partitioner (`RangePartitioner`)**
  - Reads the configured `inputFile` once to infer the overall ID range:
    - Parses the first column of each non-empty CSV line as a numeric ID.
    - Tracks `maxId` and assumes `minId = 1`.
  - Computes partition ranges based on `gridSize` and total ID range.
  - For each partition, creates an `ExecutionContext` containing:
    - `startAt` – zero-based line offset to skip in the CSV.
    - `itemCount` – number of records for this partition.
    - `inputFile` – propagated for the reader.
    - `partitionId` – identifier used by logging.
  - Uses Spring’s `ResourceLoader` with a fallback to `FileSystemResource` so that both classpath and filesystem paths work. This is important for local vs containerized vs GCS-mounted file paths.

- **Slave step (`slaveStep`)**
  - Chunk-oriented step with type `<Record, Record>`.
  - Configured with:
    - `chunkSize` via `chunk.size` (default `100`).
    - `csvPartitionItemReader` as reader.
    - `transformProcessor` (a `TransformProcessor` bean) as processor.
    - `postgresItemWriter` as writer.
  - Fault-tolerant configuration:
    - Retry policy via `SimpleRetryPolicy` with `maxRetryAttempts` from `retry.maxAttempts` (default `3`).
    - `ExponentialBackOffPolicy` for backoff between retries.
    - `LoggingStepExecutionListener` for per-step logging.

- **Reader (`csvPartitionItemReader`)**
  - Step-scoped `FlatFileItemReader<Record>`.
  - Uses `startAt` and `itemCount` from the partition `ExecutionContext` to read only its slice of the CSV.
  - Resolves `inputFile` via `ResourceLoader` (classpath or filesystem path) with filesystem fallback.
  - Maps each CSV line to `com.mayank.batch.model.Record` with fields `id`, `payload`, `createdAt`.
  - Defensive parsing: on any parsing error, individual fields are set to `null` rather than failing the entire chunk.

- **Processors**
  - `RecordProcessor`: simple pass-through processor (currently not central to the benchmark pipeline but available for simpler scenarios).
  - `TransformProcessor`:
    - Core processor used in the main pipeline.
    - `@Retryable` over `RuntimeException` to integrate with `@EnableRetry` from `RetryConfig`.
    - Produces a new `Record` with:
      - Same `id` and `createdAt`.
      - `payload` wrapped in a JSON structure indicating it has been processed and stamped with a `timestamp` (current `Instant`).
    - Explicitly handles `null` inputs by returning `null` to avoid `NullPointerException`.

- **Writer (`PostgresItemWriter`)**
  - Batch writer to a PostgreSQL `processed_record` table, using `JdbcTemplate` created from the injected `DataSource`.
  - Uses a single upsert SQL statement:
    - Inserts `(id, payload::jsonb, processed_at, status)`.
    - `ON CONFLICT (id) DO UPDATE` to keep `processed_record` idempotent across reruns.
  - Annotated with `@Transactional` and `@Retryable(SQLException.class)`, so transient DB issues will be retried.
  - Validates that `record.getId()` is non-null; otherwise throws to surface data issues.

### Metrics, logging, and observability

- **Metrics configuration (`MetricsConfig`)**
  - Defines two `Timer` beans registered in Micrometer:
    - `batch.job.duration.seconds`
    - `batch.step.duration.seconds`
  - The `BatchMetricsListener` uses these to capture execution durations.

- **Metrics listener (`BatchMetricsListener`)**
  - Injects the above timers and a `MeterRegistry`.
  - On each job and step:
    - Tracks start times via `beforeJob` / `beforeStep`.
    - Records durations in timers via `afterJob` / `afterStep`.
    - Maintains counters:
      - `batch.records.processed` – total write count across steps.
      - `batch.failure.count` – failure exceptions aggregated across steps.

- **Logging listeners**
  - `JobRunLoggingListener`:
    - Uses SLF4J MDC to attach `jobInstanceId`, `jobExecutionId`, and `jobName` to log entries.
    - Logs job start with parameters and job completion with status, duration, total read/written, and failures.
  - `LoggingStepExecutionListener`:
    - Logs step start including the `partitionId` from the execution context.
    - Logs step completion with read/processed/written counts and number of failures.

Combined with the Cloud Monitoring configuration described in `README.md` and `docs/benchmarking-guide.md`, these components emit the metrics used for cross-platform comparisons (latency, throughput, elasticity).

### Data model and resources

- **Domain model**
  - `com.mayank.batch.model.Record` is a simple POJO with `id`, `payload` (usually JSON), and `createdAt` (`LocalDateTime`).
  - CSV lines are mapped directly into this model; transformations and DB writes operate on `Record` instances.

- **Database schema and init scripts**
  - `src/main/resources/db/schema.sql` – application schema including `processed_record` table.
  - `src/main/resources/db/spring-batch-schema-postgres.sql` – Spring Batch metadata schema tailored for PostgreSQL.
  - `docker-entrypoint-initdb.d/` is mounted by `docker-compose.yml` into the Postgres container to initialize the DB schema automatically for local runs.

- **Sample and generated data**
  - `src/main/resources/data/sample-10k.csv` – bundled sample dataset used in tests and as a default input.
  - `data/` directory – runtime-generated CSVs stored here by data generation modes (`SpringBatchBenchmarkApplication` and `DataGenerator`).

### Testing strategy

- Tests live under `src/test/java/com/mayank/batch` and `src/test/resources`.
- Integration tests:
  - `BatchIntegrationTest`:
    - Spins up a PostgreSQL Testcontainers instance.
    - Binds Spring datasource properties to the container.
    - Ensures `spring.sql.init.mode=always` so that schema scripts run against the container DB.
    - Disables auto job launch for the test context (`spring.batch.job.enabled=false`).
  - `EndToEndJobTest`:
    - Also uses PostgreSQL Testcontainers and the same property configuration.
    - Explicitly resolves the input CSV path (prefers `data/sample-10k.csv` on the filesystem, falls back to classpath resource).
    - Launches `probeJob` via `JobLauncher` and verifies:
      - Job completes with `BatchStatus.COMPLETED`.
      - `processed_record` table has at least one row.
- Unit-level tests:
  - `RangePartitionerTest` verifies partition map structure and basic expectations (number of partitions, presence of partition keys, reasonable ranges) against `data/sample-10k.csv` using a mocked `ResourceLoader`.
  - `TransformProcessorTest` validates JSON payload enrichment and null-safety behavior in `TransformProcessor`.

These tests provide coverage for both the partitioning logic and the end-to-end batch job behavior, and are the primary safety net when modifying the batch configuration or job structure.

### Deployment and benchmarking orchestration

Beyond the core Spring application, deployment and benchmarking are orchestrated via scripts and documentation:

- **Scripts (`scripts/` directory)**
  - `build_and_push_image.sh` – builds the Docker image and pushes it to Artifact Registry, parameterized by `PROJECT_ID`, `REGION`, `REPO_NAME`, `IMAGE_NAME`, `IMAGE_TAG`.
  - `deploy_cloud_run_job.sh` – deploys the application as a Cloud Run Job with environment variables for datasource, input file (typically `gs://...`), tuning parameters (`CHUNK_SIZE`, `PARTITION_GRID`, `RETRY_MAX_ATTEMPTS`), and optional Stackdriver metrics.
  - `create_gke_cluster.sh` – provisions a GKE Autopilot cluster configured for running the benchmark job.
  - `deploy_gke_job.sh` – deploys a Kubernetes Job on GKE with similar environment variables and optional mounting of a service account key for Cloud SQL.
  - `run_benchmarks.sh` – main harness for running a benchmark matrix over combinations of datasets, chunk sizes, grid sizes, and retry attempts, redeploying and executing jobs on both Cloud Run and GKE, and collating results into `benchmark-results.csv`.
  - `upload_data_to_gcs.sh` – helper to upload CSV datasets to GCS buckets for use as `INPUT_FILE` sources.

- **Documentation (`docs/` directory)**
  - `gcp-setup.md` – canonical guide for provisioning GCP resources (Cloud SQL, GCS, service accounts/roles) and setting environment variables such as `SPRING_DATASOURCE_URL` for Cloud SQL socket factory usage.
  - `benchmarking-guide.md` – describes KPI definitions, monitoring queries (Cloud Monitoring + custom metrics), and analysis workflows for the generated benchmark data.

`README.md` ties these together into a linear workflow for building the app, deploying to Cloud Run Jobs and GKE Autopilot, running the benchmark matrix, and analyzing metrics. Future changes to deployment flow should remain consistent with the roles and parameters of these scripts.
