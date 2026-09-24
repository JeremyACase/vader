package org.vader.core.server.service.agent.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.service.operators.pythonsandbox.TaskAttemptSandboxService;

/**
 * Deletes a settled attempt's own Python sandbox as soon as the settlement commits -- however it
 * settled: a harness-reported result, a dispatch failure, or {@code TaskAttemptReaper} timing it
 * out, since all three publish the same {@link TaskAttemptSettledEvent}.
 *
 * <p>Mirrors {@link AgentHarnessJobCleanupListener}, with one difference: a sandbox has no
 * Kubernetes-side TTL to fall back on, so a missed delete here leaks a pod until someone removes
 * it by hand. Its executor therefore runs a delete on the caller's thread under a burst rather
 * than discarding it (see {@code InboxAsyncConfig#taskAttemptSandboxCleanupExecutor}). Deleting
 * the sandbox of an attempt that never ran code -- and so never had one -- is harmless.</p>
 */
@Service
@ConditionalOnProperty(
    name = {"vader.operators.enabled", "vader.operators.python-sandbox.enabled"},
    havingValue = "true",
    matchIfMissing = false)
public class TaskAttemptSandboxCleanupListener {

    private static final Logger logger =
        LoggerFactory.getLogger(TaskAttemptSandboxCleanupListener.class);

    @Autowired
    private TaskAttemptSandboxService sandboxService;

    @Autowired
    @Qualifier("taskAttemptSandboxCleanupExecutor")
    private TaskExecutor executor;

    /**
     * Deletes the sandbox owned by a settled attempt.
     *
     * @param event the settlement notification
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskAttemptSettled(final TaskAttemptSettledEvent event) {
        this.executor.execute(() -> this.deleteSandbox(event.assignmentId()));
    }

    private void deleteSandbox(final String assignmentId) {
        try {
            this.sandboxService.delete(assignmentId);
        } catch (RuntimeException e) {
            logger.warn(
                "Could not delete the Python sandbox for attempt {}: {} (it must now be removed "
                    + "by hand, e.g. with delete_sandbox)",
                assignmentId, e.getMessage());
        }
    }
}
