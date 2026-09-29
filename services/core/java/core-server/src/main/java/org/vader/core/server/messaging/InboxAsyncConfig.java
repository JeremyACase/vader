package org.vader.core.server.messaging;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executors for "after commit, hand off to a fresh thread": an {@code AFTER_COMMIT} listener runs
 * on the committing thread with its stale {@code EntityManager} still bound, so a
 * {@code @Transactional} method called directly from it joins that finished transaction and its
 * writes are lost. Running the follow-up on one of these executors starts a clean transaction.
 *
 * <p>Always registered, independent of {@code vader.scheduling.enabled}: the handoff is needed
 * whether or not scheduled polling is on.</p>
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
     * The dedicated single-thread executor for enqueue-triggered attempt-review inbox drains. A
     * discarded nudge here is harmless -- the review pipeline's own scheduled poll is a tight
     * enough safety net on its own, same reasoning as {@code llmRequestInboxExecutor}.
     *
     * @return the executor
     */
    @Bean("taskAttemptReviewInboxExecutor")
    public TaskExecutor taskAttemptReviewInboxExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("task-attempt-review-inbox-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }

    /**
     * The dedicated single-thread executor for enqueue-triggered LLM-request inbox drains. A
     * discarded nudge here is harmless -- {@code LlmRequestInbox}'s own much shorter scheduled
     * poll (default 200ms, versus the other inboxes' 1000ms) is a tight enough safety net on its
     * own.
     *
     * @return the executor
     */
    @Bean("llmRequestInboxExecutor")
    public TaskExecutor llmRequestInboxExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("llm-request-inbox-");
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

    /**
     * The dedicated executor {@code AgentHarnessJobCleanupListener} hands Job deletion off to.
     *
     * <p>Deliberately separate from {@code taskGraphSchedulerExecutor}: a slow or stuck
     * Kubernetes delete call must never delay task-graph progression, which is the more urgent
     * of the two. Unlike that executor's unbounded queue, dropping a queued cleanup here under a
     * genuine burst is an acceptable degradation -- the Job's own {@code ttlSecondsAfterFinished}
     * still cleans it up eventually, just later than the common case.</p>
     *
     * @return the executor
     */
    @Bean("agentHarnessJobCleanupExecutor")
    public TaskExecutor agentHarnessJobCleanupExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("agent-harness-job-cleanup-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }

    /**
     * The dedicated executor {@code TaskAttemptSandboxCleanupListener} hands sandbox deletion off
     * to.
     *
     * <p>Unlike {@code agentHarnessJobCleanupExecutor}, a full queue runs the delete on the
     * caller's thread rather than discarding it: a sandbox has no {@code ttlSecondsAfterFinished}
     * backstop, so a dropped delete would leak its pod indefinitely. Briefly slowing the thread
     * that committed a settlement is the cheaper failure.</p>
     *
     * @return the executor
     */
    @Bean("taskAttemptSandboxCleanupExecutor")
    public TaskExecutor taskAttemptSandboxCleanupExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("task-attempt-sandbox-cleanup-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
