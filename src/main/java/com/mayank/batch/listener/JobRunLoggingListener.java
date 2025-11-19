package com.mayank.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class JobRunLoggingListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobRunLoggingListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        MDC.put("jobInstanceId", String.valueOf(jobExecution.getJobInstance().getInstanceId()));
        MDC.put("jobExecutionId", String.valueOf(jobExecution.getId()));
        MDC.put("jobName", jobExecution.getJobInstance().getJobName());
        log.info("Starting job {} with parameters {}", jobExecution.getJobInstance().getJobName(), jobExecution.getJobParameters().toString());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long durationSeconds = 0L;
        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();
        if (startTime != null && endTime != null) {
            durationSeconds = Duration.between(startTime, endTime).getSeconds();
        }

        long totalRead = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount)
                .sum();
        long totalWritten = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();
        long totalFailures = jobExecution.getStepExecutions().stream()
                .mapToLong(se -> se.getFailureExceptions() == null ? 0 : se.getFailureExceptions().size())
                .sum();

        log.info("Job {} finished with status {} (duration={}s, read={}, written={}, failures={})",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus(),
                durationSeconds,
                totalRead,
                totalWritten,
                totalFailures);
        MDC.clear();
    }
}
