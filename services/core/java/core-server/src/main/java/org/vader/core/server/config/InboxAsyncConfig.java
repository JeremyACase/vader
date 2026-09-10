package org.vader.core.server.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor backing the "drain on enqueue" nudge: when the outbox commits a message, the inbox's
 * after-commit listener hands a drain to this pool so the request thread returns immediately.
 *
 * <p>Single-threaded with a one-slot queue and a discard policy: at most one drain runs and one
 * waits. Extra nudges are dropped because any in-progress or queued drain already picks up every
 * pending row. Always registered (independent of {@code vader.scheduling.enabled}), since the
 * inbox depends on it whether or not the scheduled poll is active.</p>
 */
@Configuration
public class InboxAsyncConfig {

    /**
     * The dedicated single-thread executor for enqueue-triggered inbox drains.
     *
     * @return the executor
     */
    @Bean("clientPromptInboxExecutor")
    public TaskExecutor clientPromptInboxExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("client-prompt-inbox-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
