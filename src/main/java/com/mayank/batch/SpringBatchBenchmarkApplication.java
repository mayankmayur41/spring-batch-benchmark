package com.mayank.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Arrays;

@SpringBootApplication
public class SpringBatchBenchmarkApplication {

    public static void main(String[] args) {
        // Check for data generation mode before starting Spring
        if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("generate-data"))) {
            System.out.println("--- Running in Standalone Data Generation Mode ---");
            try {
                String outputFile = "data/sample-10k.csv";
                int recordCount = 10000;

                // Simple arg parsing to find the number of records
                for (int i = 0; i < args.length; i++) {
                    if (args[i].equalsIgnoreCase("generate-data") && (i + 1) < args.length) {
                        try {
                            recordCount = Integer.parseInt(args[i + 1]);
                        } catch (NumberFormatException ignored) {}
                        break;
                    }
                }
                generateDataFile(outputFile, recordCount);
            } catch (Exception e) {
                System.err.println("Failed to generate data file: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
            System.out.println("--- Data Generation Complete. Exiting. ---");
            // Exit without starting the Spring context
            return;
        }

        // If not in data generation mode, run the full Spring Boot application
        SpringApplication.run(SpringBatchBenchmarkApplication.class, args);
    }

    private static void generateDataFile(String filePath, int recordCount) throws Exception {
        System.out.printf("Generating %d records to %s...%n", recordCount, filePath);
        File file = new File(filePath);
        // Ensure parent directory exists
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (long i = 1; i <= recordCount; i++) {
                String payload = String.format("{\"record_id\":%d, \"data\":\"payload-data-%d\"}", i, i);
                writer.printf("%d,\"%s\",%s%n", i, payload, LocalDateTime.now());
            }
        }
    }
}
