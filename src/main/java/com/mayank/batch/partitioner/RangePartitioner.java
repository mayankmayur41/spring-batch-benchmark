package com.mayank.batch.partitioner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@StepScope
public class RangePartitioner implements Partitioner {

    private static final Logger log = LoggerFactory.getLogger(RangePartitioner.class);

    @Value("#{jobParameters['inputFile']}")
    private String inputFile;

    private final ResourceLoader resourceLoader;

    public RangePartitioner(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        long minId = 1L;
        long maxId = 0L;

        Resource resource = resourceLoader.getResource(inputFile);

        // If resourceLoader didn't resolve an existing resource (e.g., absolute filesystem path
        // is interpreted as ServletContextResource), fall back to FileSystemResource
        if (resource == null || !resource.exists()) {
            resource = new org.springframework.core.io.FileSystemResource(inputFile);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    long currentId = Long.parseLong(line.split(",")[0]);
                    if (maxId == 0L || currentId > maxId) {
                        maxId = currentId;
                    }
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    // Log malformed lines and continue
                    log.warn("Skipping malformed line in input file {}: {}", inputFile, line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input file: " + inputFile, e);
        }

        if (maxId == 0L) {
            throw new IllegalStateException("Input file is empty or contains no valid IDs.");
        }

        long total = maxId - minId + 1;
        long targetSize = total / gridSize + (total % gridSize == 0 ? 0 : 1);

        Map<String, ExecutionContext> result = new HashMap<>();
        long number = 0;
        long start = minId;
        long end = start + targetSize - 1;

        while (start <= maxId) {
            ExecutionContext value = new ExecutionContext();
            result.put("partition" + number, value);

            if (end >= maxId) {
                end = maxId;
            }

            // For the reader we provide 'startAt' (lines to skip) and 'itemCount' values
            int startAt = (int) (start - 1); // zero-based line offset for data without header
            int itemCount = (int) (end - start + 1);

            value.putInt("startAt", startAt);
            value.putInt("itemCount", itemCount);
            value.putString("inputFile", inputFile);
            value.putString("partitionId", "partition" + number);

            log.info("Created partition {} -> startId={}, endId={}, startAt={}, itemCount={}", number, start, end, startAt, itemCount);

            start += targetSize;
            end += targetSize;
            number++;
        }

        return result;
    }
}
