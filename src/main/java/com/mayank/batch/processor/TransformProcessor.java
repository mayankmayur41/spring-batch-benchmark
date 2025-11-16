package com.mayank.batch.processor;

import com.mayank.batch.model.Record;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class TransformProcessor implements ItemProcessor<Record, Record> {

    @Override
    @Retryable(retryFor = {RuntimeException.class})
    public Record process(Record record) {
        // Safely handle null input records to prevent NullPointerException
        if (record == null) {
            return null;
        }

        // Simulate business transformation
        Record transformed = new Record();
        transformed.setId(record.getId());

        // Add processing metadata to payload
        String transformedPayload = String.format(
                "{\"original\": %s, \"processed\": true, \"timestamp\": \"%s\"}",
                record.getPayload(),
                java.time.Instant.now().toString()
        );

        transformed.setPayload(transformedPayload);
        transformed.setCreatedAt(record.getCreatedAt());

        return transformed;
    }
}
