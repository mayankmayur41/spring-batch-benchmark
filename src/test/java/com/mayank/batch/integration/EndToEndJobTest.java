package com.mayank.batch.integration;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class EndToEndJobTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("batchdb")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Ensure SQL initialization runs against Testcontainers Postgres during tests
        registry.add("spring.sql.init.mode", () -> "always");
        // Disable auto job launching; we'll run the job explicitly in the test
        registry.add("spring.batch.job.enabled", () -> "false");
    }

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job probeJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void runJob_endToEnd_writesProcessedRecords() throws Exception {
        // Copy resource path to filesystem and pass the absolute path to the job
        java.io.File projectFile = new java.io.File("data/sample-10k.csv");
        String inputFilePath;
        if (projectFile.exists()) {
            inputFilePath = projectFile.getAbsolutePath();
        } else {
            // Fallback to classpath resource if the file is packaged into the test classpath
            inputFilePath = new ClassPathResource("data/sample-10k.csv").getFile().getAbsolutePath();
        }

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("inputFile", inputFilePath)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(probeJob, jobParameters);

        assertNotNull(execution);
        assertEquals(BatchStatus.COMPLETED, execution.getStatus(), "Job should complete successfully");

        Integer processedCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_record", Integer.class);
        assertNotNull(processedCount, "Processed record count should not be null");
        assertTrue(processedCount > 0, "There should be at least one processed record");
    }
}
