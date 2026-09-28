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
import org.vader.core.server.service.operators.agentharness.AgentHarnessNaming;
import org.vader.core.server.service.operators.agentharness.AgentHarnessOperator;

/**
 * Deletes a settled attempt's agent-harness Job (and, by cascade, its Pod) as soon as the
 * settlement commits, rather than leaving cleanup solely to Kubernetes' own
 * {@code ttlSecondsAfterFinished} controller -- which defaults to an hour and, left as the only
 * mechanism, lets finished Jobs/Pods pile up in the namespace under any real load. The TTL stays
 * configured as a backstop for the rare case this explicit delete doesn't run (e.g. a transient
 * Kubernetes API failure).
 *
 * <p>Shares {@link AgentHarnessOperator}'s own enablement condition: unconditionally present
 * otherwise, this would fail to wire up when that operator bean does not exist.</p>
 */
@Service
@ConditionalOnProperty(
    name = {"vader.operators.enabled", "vader.operators.agent-harness.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class AgentHarnessJobCleanupListener {

    private static final Logger logger =
        LoggerFactory.getLogger(AgentHarnessJobCleanupListener.class);

    @Autowired
    private AgentHarnessOperator operator;

    @Autowired
    @Qualifier("agentHarnessJobCleanupExecutor")
    private TaskExecutor executor;

    /**
     * Deletes the Job backing a settled attempt. A no-op if dispatch itself failed and no Job
     * was ever created.
     *
     * @param event the settlement notification
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskAttemptSettled(final TaskAttemptSettledEvent event) {
        this.executor.execute(() -> this.deleteJob(event.assignmentId()));
    }

    private void deleteJob(final String assignmentId) {
        try {
            this.operator.delete(AgentHarnessNaming.resolve(assignmentId));
        } catch (RuntimeException e) {
            logger.warn(
                "Could not delete agent-harness Job for attempt {}: {} (it will still be "
                    + "cleaned up once its ttlSecondsAfterFinished elapses)",
                assignmentId, e.getMessage());
        }
    }
}
