package com.mayank.batch.config;

import com.mayank.batch.listener.BatchMetricsListener;
import com.mayank.batch.listener.JobRunLoggingListener;
import com.mayank.batch.listener.LoggingStepExecutionListener;
import com.mayank.batch.model.Record;
import com.mayank.batch.partitioner.RangePartitioner;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.*;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.lang.NonNull;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@SuppressWarnings("unused")
public class BatchConfig {

    private final ResourceLoader resourceLoader;

    public BatchConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Value("${chunk.size:100}")
    private int chunkSize;

    @Value("${partition.grid:4}")
    private int gridSize;

    @Value("${retry.maxAttempts:3}")
    private int maxRetryAttempts;

    @Bean
    public Job probeJob(JobRepository jobRepository,
                        Step masterStep,
                        JobRunLoggingListener jobRunLoggingListener,
                        BatchMetricsListener batchMetricsListener) {
        return new JobBuilder("probeJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(masterStep)
                .listener(jobRunLoggingListener)
                .listener(batchMetricsListener)
                .build();
    }

    @Bean
    public Step masterStep(JobRepository jobRepository,
                           RangePartitioner partitioner,
                           Step slaveStep) {
        return new StepBuilder("masterStep", jobRepository)
                .partitioner(slaveStep.getName(), partitioner)
                .step(slaveStep)
                .gridSize(gridSize)
                .taskExecutor(taskExecutor())
                .build();
    }

    @Bean
    public Step slaveStep(JobRepository jobRepository,
                          PlatformTransactionManager transactionManager,
                          ItemReader<Record> itemReader,
                          @Qualifier("transformProcessor") ItemProcessor<Record, Record> itemProcessor,
                          @Qualifier("postgresItemWriter") ItemWriter<Record> itemWriter,
                          LoggingStepExecutionListener loggingStepExecutionListener) {
        return new StepBuilder("slaveStep", jobRepository)
                .<Record, Record>chunk(chunkSize, transactionManager)
                .reader(itemReader)
                .processor(itemProcessor)
                .writer(itemWriter)
                .faultTolerant()
                .retryPolicy(new SimpleRetryPolicy(maxRetryAttempts))
                .backOffPolicy(new ExponentialBackOffPolicy())
                .listener(loggingStepExecutionListener)
                .build();
    }

    @Bean
    @StepScope
    public org.springframework.batch.item.file.FlatFileItemReader<Record> csvPartitionItemReader(
            @Value("#{stepExecutionContext['startAt']}") int startAt,
            @Value("#{stepExecutionContext['itemCount']}") int itemCount,
            @Value("#{stepExecutionContext['inputFile'] ?: jobParameters['inputFile']}") @NonNull String inputFile) {

        Resource resource = resourceLoader.getResource(inputFile);
        if (resource == null || !resource.exists()) {
            resource = new FileSystemResource(inputFile);
        }

        return new FlatFileItemReaderBuilder<Record>()
                .name("csvItemReader")
                .resource(resource)
                // startAt is the zero-based number of data lines to skip (no header handled by file)
                .linesToSkip(startAt)
                .maxItemCount(itemCount)
                .delimited()
                .names("id", "payload", "createdAt")
                .fieldSetMapper(fieldSet -> {
                    Record r = new Record();
                    try {
                        r.setId(fieldSet.readLong("id"));
                    } catch (Exception e) {
                        r.setId(null);
                    }
                    try {
                        r.setPayload(fieldSet.readString("payload"));
                    } catch (Exception e) {
                        r.setPayload(null);
                    }
                    try {
                        r.setCreatedAt(java.time.LocalDateTime.parse(fieldSet.readString("createdAt")));
                    } catch (Exception e) {
                        r.setCreatedAt(null);
                    }
                    return r;
                })
                .build();
    }

    @Bean
    public TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor();
        taskExecutor.setConcurrencyLimit(gridSize);
        return taskExecutor;
    }
}
