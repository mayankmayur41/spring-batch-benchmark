package com.mayank.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Arrays;

@SpringBootApplication
public class SpringBatchBenchmarkApplication implements CommandLineRunner {

    @Autowired(required = false) // Not required for data generation
    private JobLauncher jobLauncher;

    @Autowired(required = false) // Not required for data generation
    private Job probeJob;

    @Autowired(required = false) // Not required for data generation
    private ConfigurableApplicationContext context;

    @Value("${input.file:data/sample-10k.csv}")
    private String inputFile;

    @Value("${exit.on.complete:false}")
    private boolean exitOnComplete;

    @Value("${spring.batch.job.enabled:true}")
    private boolean batchJobEnabled;

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

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- Running in Batch Job Execution Mode ---");
        // If batch jobs are disabled in configuration or required beans are missing, skip launching
        if (!batchJobEnabled) {
            System.out.println("--- Batch jobs are disabled (spring.batch.job.enabled=false). Skipping job launch. ---");
            return;
        }

        if (jobLauncher == null || probeJob == null) {
            System.out.println("--- JobLauncher or Job bean not available. Skipping job launch. ---");
            return;
        }

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("inputFile", inputFile)
                .addLong("timestamp", System.currentTimeMillis()) // Add timestamp for unique job instances
                .toJobParameters();

        jobLauncher.run(probeJob, jobParameters);

        if (exitOnComplete) {
            System.out.println("--- Job Complete. Exiting application. ---");
            System.exit(SpringApplication.exit(context));
        }
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
