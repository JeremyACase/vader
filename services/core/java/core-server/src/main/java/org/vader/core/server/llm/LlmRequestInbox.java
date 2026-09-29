package org.vader.core.server.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.ResourceAccessException;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;
import org.vader.core.server.llm.interfaces.InterfaceLlmExecutor;
import org.vader.core.server.messaging.AbstractInbox;
import org.vader.core.server.messaging.OutboxMessageEnqueuedEvent;
import org.vader.core.server.messaging.OutboxMessageRepository;

/**
 * Inbox for local-LLM requests: pops the oldest pending request and performs it with the
 * {@link InterfaceLlmExecutor} registered for the message's {@code kind}, then writes an
 * {@link LlmResponseEnvelope} back onto the same row for {@link LlmRequestQueue}'s blocked,
 * polling caller to read.
 *
 * <p>{@link #maxOpenMessages} is fixed at {@code 1}: that database-checked ceiling is what keeps
 * one request in flight against the LLM across all replicas.</p>
 *
 * <p>Drains on a schedule and immediately after an enqueue commits on this replica. The schedule
 * is how other replicas notice a newly freed slot.</p>
 */
@Service
public class LlmRequestInbox extends AbstractInbox<LlmRequestOutboxMessageEntity> {

    private static final String LLM_REQUEST = "LlmRequest";

    @Autowired
    private LlmRequestOutboxMessageRepository messageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LlmExecutorRegistry executorRegistry;

    @Autowired
    @Qualifier("llmRequestInboxExecutor")
    private TaskExecutor executor;

    @Override
    public String queuedModelType() {
        return LLM_REQUEST;
    }

    @Override
    public int maxOpenMessages() {
        return 1;
    }

    @Override
    protected OutboxMessageRepository<LlmRequestOutboxMessageEntity> repository() {
        return this.messageRepository;
    }

    @Override
    protected void handle(final LlmRequestOutboxMessageEntity message) {
        var executor = this.executorRegistry.forKind(message.getKind());
        var envelope = this.execute(executor, message.getRequestJson());
        message.setResponseJson(this.toJson(envelope));
        this.messageRepository.save(message);
    }

    /**
     * Runs one call, reporting a connectivity failure as an unreachable envelope rather than
     * letting it fail the message. Any other exception propagates, settling the message
     * {@code FAILED}.
     */
    private <Q> LlmResponseEnvelope execute(
            final InterfaceLlmExecutor<Q, ?> executor, final String requestJson) {
        Q request = this.readValue(requestJson, LlmExecutorRegistry.requestTypeOf(executor));
        try {
            return LlmResponseEnvelope.of(
                this.objectMapper.valueToTree(executor.execute(request)));
        } catch (ResourceAccessException | TransientAiException e) {
            return LlmResponseEnvelope.unreachable(e.getMessage());
        }
    }

    private <T> T readValue(final String requestJson, final Type type) {
        try {
            return this.objectMapper.readValue(requestJson, this.objectMapper.constructType(type));
        } catch (JsonProcessingException e) {
            throw new LlmRequestQueueException("Could not deserialize " + type + " request", e);
        }
    }

    private String toJson(final Object value) {
        try {
            return this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new LlmRequestQueueException("Could not serialize LLM response", e);
        }
    }

    /**
     * Scheduled drain, much more frequent than other inboxes': it is how other replicas notice a
     * freed slot, so its interval adds directly to every blocked caller's wait.
     */
    @Scheduled(fixedDelayString = "${vader.llm.request-queue.drain-poll-interval-ms:200}")
    public void scheduledDrain() {
        this.drain();
    }

    /**
     * Drains immediately once a newly enqueued request message has committed.
     *
     * @param event the enqueue notification
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnqueued(final OutboxMessageEnqueuedEvent event) {
        if (LLM_REQUEST.equals(event.modelType())) {
            this.executor.execute(this::drain);
        }
    }
}
