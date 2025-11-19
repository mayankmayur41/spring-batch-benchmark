package com.mayank.batch.runner;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.stackdriver.StackdriverMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.batch.JobLauncherApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A custom ApplicationRunner that ensures a graceful shutdown for the StackdriverMeterRegistry
 * after a Spring Batch job completes. This prevents race conditions where the application
 * shuts down before all metrics have been published.
 */
@Component
@Order(0) // Ensure this runner executes before the default JobLauncherApplicationRunner
public class GracefulShutdownApplicationRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GracefulShutdownApplicationRunner.class);

    private final JobLauncherApplicationRunner jobLauncherApplicationRunner;
    private final MeterRegistry meterRegistry;
    private final ConfigurableApplicationContext context;

    public GracefulShutdownApplicationRunner(
            JobLauncherApplicationRunner jobLauncherApplicationRunner,
            MeterRegistry meterRegistry,
            ConfigurableApplicationContext context) {
        this.jobLauncherApplicationRunner = jobLauncherApplicationRunner;
        this.meterRegistry = meterRegistry;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int exitCode = 0;
        try {
            // Execute the Spring Batch job using the default runner
            this.jobLauncherApplicationRunner.run(args);
        } catch (Exception e) {
            LOGGER.error("Job execution failed.", e);
            exitCode = 1;
        } finally {
            // This block will always execute after the job finishes (or fails)
            gracefulShutdown(exitCode);
        }
    }

    private void gracefulShutdown(int exitCode) {
        LOGGER.info("Job finished. Starting graceful shutdown of metrics registry...");

        findStackdriverMeterRegistry(this.meterRegistry).ifPresentOrElse(stackdriverRegistry -> {
            try {
                LOGGER.info("Closing StackdriverMeterRegistry to flush pending metrics...");
                // Calling close() on the registry flushes pending metrics and shuts down its resources.
                // This is a blocking call.
                stackdriverRegistry.close();
                LOGGER.info("StackdriverMeterRegistry closed successfully.");

                // Optional: A brief delay to ensure async network operations fully complete.
                TimeUnit.SECONDS.sleep(5);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Shutdown delay was interrupted.", e);
            } catch (Exception e) {
                LOGGER.error("Error during graceful shutdown of metrics registry:", e);
            }
        }, () -> LOGGER.warn("StackdriverMeterRegistry not found. Skipping graceful shutdown for metrics."));

        // Initiate the shutdown of the Spring application context and exit.
        LOGGER.info("Initiating application exit with code {}.", exitCode);
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /**
     * Finds the StackdriverMeterRegistry, which might be nested inside a CompositeMeterRegistry.
     * @param registry The top-level MeterRegistry.
     * @return An Optional containing the StackdriverMeterRegistry if found.
     */
    private Optional<StackdriverMeterRegistry> findStackdriverMeterRegistry(MeterRegistry registry) {
        if (registry instanceof StackdriverMeterRegistry) {
            return Optional.of((StackdriverMeterRegistry) registry);
        }
        if (registry instanceof CompositeMeterRegistry) {
            return ((CompositeMeterRegistry) registry).getRegistries()
                    .stream()
                    .filter(StackdriverMeterRegistry.class::isInstance)
                    .map(StackdriverMeterRegistry.class::cast)
                    .findFirst();
        }
        return Optional.empty();
    }
}
