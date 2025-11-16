package com.mayank.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class LoggingStepExecutionListener implements StepExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(LoggingStepExecutionListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        logger.info("Starting step: {} | Partition: {}",
                stepExecution.getStepName(),
                stepExecution.getExecutionContext().getString("partitionId", "main"));
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        logger.info("Completed step: {} | Read: {} | Processed: {} | Written: {} | Failures: {}",
                stepExecution.getStepName(),
                stepExecution.getReadCount(),
                stepExecution.getProcessSkipCount(),
                stepExecution.getWriteCount(),
                stepExecution.getFailureExceptions().size());

        return stepExecution.getExitStatus();
    }
}
