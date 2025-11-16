package com.mayank.batch.processor;

import com.mayank.batch.model.Record;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TransformProcessorTest {

    private final TransformProcessor processor = new TransformProcessor();

    @Test
    void testRecordTransformation() {
        // Given
        Record input = new Record();
        input.setId(1L);
        input.setPayload("{\"data\": \"sample\"}");
        input.setCreatedAt(LocalDateTime.now());

        // When
        Record transformed = processor.process(input);

        // Then
        assertNotNull(transformed);
        assertEquals(input.getId(), transformed.getId());
        assertTrue(transformed.getPayload().contains("\"processed\": true"));
        assertTrue(transformed.getPayload().contains("original"));
    }

    @Test
    void testNullInput() {
        // When
        Record result = processor.process(null);

        // Then
        // Verify that the processor correctly returns null for a null input.
        assertNull(result);
    }
}
