-- Initialize application schema: processed_record and source_record
-- This file will be executed by Postgres docker-entrypoint on first container startup

-- Use the repository schema.sql if present, otherwise recreate minimal tables
CREATE TABLE IF NOT EXISTS source_record (
    id BIGINT PRIMARY KEY,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS processed_record (
    id BIGINT PRIMARY KEY,
    payload JSONB NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL
);

-- Create Spring Batch metadata tables (simple subset) - include the supplied script if available
-- You can replace below with a copy of src/main/resources/db/spring-batch-schema-postgres.sql

CREATE TABLE IF NOT EXISTS BATCH_JOB_INSTANCE  (
	JOB_INSTANCE_ID BIGINT  NOT NULL PRIMARY KEY ,
	VERSION BIGINT ,
	JOB_NAME VARCHAR(100) NOT NULL,
	JOB_KEY VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION  (
	JOB_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY ,
	VERSION BIGINT  ,
	JOB_INSTANCE_ID BIGINT NOT NULL,
	CREATE_TIME TIMESTAMP NOT NULL,
	START_TIME TIMESTAMP DEFAULT NULL ,
	END_TIME TIMESTAMP DEFAULT NULL ,
	STATUS VARCHAR(10),
	EXIT_CODE VARCHAR(2500),
	EXIT_MESSAGE VARCHAR(2500),
	LAST_UPDATED TIMESTAMP
);

-- Indexes for processed_record
CREATE INDEX IF NOT EXISTS idx_processed_record_status ON processed_record(status);
CREATE INDEX IF NOT EXISTS idx_processed_record_timestamp ON processed_record(processed_at);

