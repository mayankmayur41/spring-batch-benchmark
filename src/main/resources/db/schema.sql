-- Source table
CREATE TABLE IF NOT EXISTS source_record (
    id BIGINT PRIMARY KEY,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Processed records table
CREATE TABLE IF NOT EXISTS processed_record (
    id BIGINT PRIMARY KEY,
    payload JSONB NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL
);

-- Index for better query performance
CREATE INDEX IF NOT EXISTS idx_processed_record_status ON processed_record(status);
CREATE INDEX IF NOT EXISTS idx_processed_record_timestamp ON processed_record(processed_at);
