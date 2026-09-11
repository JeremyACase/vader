package org.vader.core.server.service.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executors backing "after-commit, hand off to a fresh thread" nudges: an
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} method runs synchronously on the
 * thread whose transaction just committed, and Spring has not yet cleared that thread's
 * bound {@code EntityManager} at that point. A {@code @Transactional} method called directly
 * from such a listener does not open a fresh transaction -- it finds that stale, already-
 * committed resource and "participates" in it instead, so its own writes are silently lost
 * when the connection is later closed with nothing left to commit them. Handing the follow-up
 * work to a separate thread via one of these executors is what actually starts a clean
 * transaction.
 *
 * <p>Always registered (independent of {@code vader.scheduling.enabled}), since the inbox and
 * scheduler need this handoff whether or not the scheduled poll is also active.</p>
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

    /**
     * The dedicated single-thread executor for enqueue-triggered task-assignment inbox drains.
     *
     * @return the executor
     */
    @Bean("taskAssignmentInboxExecutor")
    public TaskExecutor taskAssignmentInboxExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("task-assignment-inbox-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }

    /**
     * The dedicated single-thread executor {@code TaskGraphSchedulerListener} hands
     * {@code TaskGraphScheduler#evaluate} off to.
     *
     * <p>Unlike the inbox drains above, a queued-up {@code evaluate()} call is <em>not</em> safe
     * to discard under load: {@code evaluate()} for a given workflow only ever gets re-triggered
     * by that workflow's own future events, so dropping the one call in flight could stall it
     * forever with nothing left to nudge it again. The queue is therefore left unbounded rather
     * than given a discard policy.</p>
     *
     * @return the executor
     */
    @Bean("taskGraphSchedulerExecutor")
    public TaskExecutor taskGraphSchedulerExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("task-graph-scheduler-");
        return executor;
    }
}
