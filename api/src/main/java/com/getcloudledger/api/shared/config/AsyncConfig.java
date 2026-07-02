package com.getcloudledger.api.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} and backs the projection-rebuild worker with a small, bounded pool.
 * A tiny core/max keeps concurrent rebuilds serialised (they compete for SQS/DB anyway); the
 * bounded queue plus CallerRuns policy applies natural back-pressure instead of unbounded growth.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("rebuildExecutor")
    public TaskExecutor rebuildExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("rebuild-");
        executor.initialize();
        return executor;
    }
}
