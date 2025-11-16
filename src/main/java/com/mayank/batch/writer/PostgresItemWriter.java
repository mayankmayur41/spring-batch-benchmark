package com.mayank.batch.writer;

import com.mayank.batch.model.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

@Component
public class PostgresItemWriter implements ItemWriter<Record> {

    private static final Logger log = LoggerFactory.getLogger(PostgresItemWriter.class);

    private final JdbcTemplate jdbcTemplate;

    public PostgresItemWriter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    @Transactional
    @Retryable(retryFor = {SQLException.class})
    public void write(@NonNull Chunk<? extends Record> items) {
        if (items == null || items.isEmpty()) {
            log.debug("No items to write");
            return;
        }

        String sql = """
            INSERT INTO processed_record (id, payload, processed_at, status)
            VALUES (?, ?::jsonb, ?, ?)
            ON CONFLICT (id)
            DO UPDATE SET
                payload = EXCLUDED.payload,
                processed_at = EXCLUDED.processed_at,
                status = EXCLUDED.status
            """;

        try {
            int batchSize = items.size();
            log.debug("Writing batch of size {} to processed_record", batchSize);

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(@NonNull PreparedStatement ps, int i) throws SQLException {
                    Record record = items.getItems().get(i);
                    if (record.getId() == null) {
                        throw new SQLException("Record id is null for item index " + i);
                    }
                    ps.setLong(1, record.getId());
                    ps.setString(2, record.getPayload());
                    ps.setTimestamp(3, Timestamp.from(Instant.now()));
                    ps.setString(4, "PROCESSED");
                }

                @Override
                public int getBatchSize() {
                    return items.size();
                }
            });
        } catch (Exception e) {
            log.error("Failed to write batch to processed_record: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to write batch to processed_record", e);
        }
    }
}
