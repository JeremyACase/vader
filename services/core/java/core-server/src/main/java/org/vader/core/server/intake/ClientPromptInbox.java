package org.vader.core.server.intake;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.ClientPromptOutboxMessageEntity;
import org.vader.core.server.messaging.AbstractInbox;
import org.vader.core.server.messaging.OutboxMessageEnqueuedEvent;
import org.vader.core.server.messaging.OutboxMessageRepository;
import org.vader.core.server.orchestration.OrchestratorAgentService;

/**
 * Inbox for client prompts: pops pending prompt messages and runs
 * {@link OrchestratorAgentService#decompose} on each, turning it into a persisted workflow.
 *
 * <p>Drains are triggered two ways: on a fixed schedule (a safety net that also catches messages
 * left behind by a restart), and immediately after an enqueue commits (the common path, so a
 * submitted prompt starts decomposing without waiting for the next tick).</p>
 */
@Service
public class ClientPromptInbox extends AbstractInbox<ClientPromptOutboxMessageEntity> {

    private static final String CLIENT_PROMPT = "ClientPrompt";

    @Autowired
    private ClientPromptOutboxMessageRepository messageRepository;

    // Lazy: this inbox is one of BackpressureRegistry's queues, and the orchestrator service
    // reaches the Spring AI tool-calling graph, whose MCP tools lead back to that registry.
    @Autowired
    @Lazy
    private OrchestratorAgentService orchestratorAgentService;

    @Autowired
    @Qualifier("clientPromptInboxExecutor")
    private TaskExecutor executor;

    @Value("${vader.inbox.client-prompt.max-concurrency:1}")
    private int maxConcurrency;

    @Override
    public String queuedModelType() {
        return CLIENT_PROMPT;
    }

    @Override
    public int maxOpenMessages() {
        return this.maxConcurrency;
    }

    @Override
    protected OutboxMessageRepository<ClientPromptOutboxMessageEntity> repository() {
        return this.messageRepository;
    }

    @Override
    protected void handle(final ClientPromptOutboxMessageEntity message) {
        this.orchestratorAgentService.decompose(message.getClientPrompt().getId());
    }

    /**
     * Scheduled safety-net drain.
     */
    @Scheduled(fixedDelayString = "${vader.inbox.client-prompt.poll-interval-ms:1000}")
    public void scheduledDrain() {
        this.drain();
    }

    /**
     * Drains immediately once a newly enqueued prompt message has committed.
     *
     * @param event the enqueue notification
     */
    @EventListener
    public void onEnqueued(final OutboxMessageEnqueuedEvent event) {
        if (CLIENT_PROMPT.equals(event.modelType())) {
            this.executor.execute(this::drain);
        }
    }
}
