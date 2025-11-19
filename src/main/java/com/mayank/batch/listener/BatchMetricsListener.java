package com.mayank.batch.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterJob;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.annotation.BeforeJob;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class BatchMetricsListener implements JobExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchMetricsListener.class);

    private final Counter recordsProcessedCounter;
    private final Counter failureCounter;
    private final Timer jobDurationTimer;
    private final Timer stepDurationTimer;

    private long jobStartTime;
    private long stepStartTime;

    public BatchMetricsListener(MeterRegistry registry,
                                @Qualifier("batchJobDurationTimer") Timer jobDurationTimer,
                                @Qualifier("batchStepDurationTimer") Timer stepDurationTimer) {
        this.recordsProcessedCounter = Counter.builder("batch.records.processed")
                .description("Number of records processed")
                .register(registry);

        this.failureCounter = Counter.builder("batch.failure.count")
                .description("Number of processing failures")
                .register(registry);

        this.jobDurationTimer = jobDurationTimer;
        this.stepDurationTimer = stepDurationTimer;
    }

    @BeforeJob
    public void beforeJob(JobExecution jobExecution) {
        jobStartTime = System.currentTimeMillis();
    }

    @AfterJob
    public void afterJob(JobExecution jobExecution) {
        long duration = System.currentTimeMillis() - jobStartTime;
        jobDurationTimer.record(duration, TimeUnit.MILLISECONDS);
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            LOGGER.info("Mayank Success");
        }
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        stepStartTime = System.currentTimeMillis();
    }

    @AfterStep
    public void afterStep(StepExecution stepExecution) {
        long duration = System.currentTimeMillis() - stepStartTime;
        stepDurationTimer.record(duration, TimeUnit.MILLISECONDS);

        // Update metrics
        recordsProcessedCounter.increment(stepExecution.getWriteCount());
        if (stepExecution.getFailureExceptions() != null && !stepExecution.getFailureExceptions().isEmpty()) {
            failureCounter.increment(stepExecution.getFailureExceptions().size());
        }
    }
}
