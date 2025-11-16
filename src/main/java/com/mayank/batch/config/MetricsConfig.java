package com.mayank.batch.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MetricsConfig {

    @Bean
    public Timer batchJobDurationTimer(MeterRegistry registry) {
        return Timer.builder("batch.job.duration.seconds")
                .description("Time taken for batch job execution")
                .register(registry);
    }

    @Bean
    public Timer batchStepDurationTimer(MeterRegistry registry) {
        return Timer.builder("batch.step.duration.seconds")
                .description("Time taken for batch step execution")
                .register(registry);
    }
}
