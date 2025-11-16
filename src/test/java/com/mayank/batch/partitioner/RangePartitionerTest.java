package com.mayank.batch.partitioner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RangePartitionerTest {

    @Mock
    private DataSource dataSource; // Mocked, as it's not used in the current partition logic

    @Mock
    private ResourceLoader resourceLoader;

    @InjectMocks
    private RangePartitioner partitioner;

    @Test
    void testPartitioning() {
        // Given
        int gridSize = 4;

        // Provide a valid input file path to the partitioner (test runs from project root)
        ReflectionTestUtils.setField(partitioner, "inputFile", "data/sample-10k.csv");

        // Configure the mocked ResourceLoader to return a FileSystemResource pointing to the test data
        Resource res = new FileSystemResource("data/sample-10k.csv");
        when(resourceLoader.getResource("data/sample-10k.csv")).thenReturn(res);

        // When
        Map<String, ExecutionContext> partitions = partitioner.partition(gridSize);

        // Then
        assertEquals(gridSize, partitions.size());

        // Verify the contents of each partition
        for (int i = 0; i < gridSize; i++) {
            String partitionKey = "partition" + i;
            assertTrue(partitions.containsKey(partitionKey));

            ExecutionContext context = partitions.get(partitionKey);
            assertTrue(context.containsKey("minValue") || context.containsKey("startAt"));
            assertTrue(context.containsKey("itemCount") || context.containsKey("maxValue"));
            assertTrue(context.containsKey("inputFile"));

            assertEquals("data/sample-10k.csv", context.getString("inputFile"));
        }

        // Verify the ranges are reasonable (based on 10000 records)
        ExecutionContext p0 = partitions.get("partition0");
        // startAt based partition: expect first partition to start at 0
        assertTrue(p0.getInt("startAt") == 0 || p0.getLong("minValue") == 1L);

        ExecutionContext p3 = partitions.get("partition3");
        assertTrue(p3.getInt("itemCount") > 0 || p3.getLong("maxValue") == 10000L);
    }
}
