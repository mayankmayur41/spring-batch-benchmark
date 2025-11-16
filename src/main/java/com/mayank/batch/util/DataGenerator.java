package com.mayank.batch.util;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Random;

@Component
public class DataGenerator implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0 && args[0].equals("generate-data")) {
            int recordCount = Integer.parseInt(args[1]);
            String filename = "data/" + recordCount + ".csv";

            Path dataDir = Paths.get("data");
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }

            generateCsvFile(recordCount, filename);
            System.out.println("Generated " + recordCount + " records in " + filename);
            System.exit(0);
        }
    }

    private void generateCsvFile(int recordCount, String filename) throws Exception {
        Random random = new Random(42); // Fixed seed for reproducibility

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename))) {
            for (int i = 1; i <= recordCount; i++) {
                String payload = String.format("{\"value\": %d, \"category\": \"%s\", \"active\": %b}",
                        random.nextInt(1000),
                        "CAT_" + random.nextInt(10),
                        random.nextBoolean()
                );

                LocalDateTime timestamp = LocalDateTime.now().minusDays(random.nextInt(365));

                writer.write(String.format("%d,\"%s\",%s\n",
                        i,
                        payload.replace("\"", "\"\""), // Escape quotes for CSV
                        timestamp.toString()
                ));

                // Progress indicator
                if (i % 10000 == 0) {
                    System.out.println("Generated " + i + " records...");
                }
            }
        }
    }
}
