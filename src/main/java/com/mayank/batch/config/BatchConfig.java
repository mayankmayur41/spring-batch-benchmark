package com.mayank.batch.config;

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
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.lang.NonNull;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@SuppressWarnings("unused")
public class BatchConfig {

    @Value("${chunk.size:100}")
    private int chunkSize;

    @Value("${partition.grid:4}")
    private int gridSize;

    @Bean
    public Job probeJob(JobRepository jobRepository, Step masterStep) {
        return new JobBuilder("probeJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(masterStep)
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
                          @Qualifier("postgresItemWriter") ItemWriter<Record> itemWriter) {
        return new StepBuilder("slaveStep", jobRepository)
                .<Record, Record>chunk(chunkSize, transactionManager)
                .reader(itemReader)
                .processor(itemProcessor)
                .writer(itemWriter)
                .faultTolerant()
                .retryPolicy(new SimpleRetryPolicy(3))
                .backOffPolicy(new ExponentialBackOffPolicy())
                .build();
    }

    @Bean
    @StepScope
    public org.springframework.batch.item.file.FlatFileItemReader<Record> csvPartitionItemReader(
            @Value("#{stepExecutionContext['startAt']}") int startAt,
            @Value("#{stepExecutionContext['itemCount']}") int itemCount,
            @Value("#{stepExecutionContext['inputFile'] ?: jobParameters['inputFile']}") @NonNull String inputFile) {

        return new FlatFileItemReaderBuilder<Record>()
                .name("csvItemReader")
                .resource(new FileSystemResource(inputFile))
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
