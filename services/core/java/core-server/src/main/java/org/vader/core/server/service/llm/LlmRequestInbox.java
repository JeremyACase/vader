package org.vader.core.server.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.OutboxMessageEnqueuedEvent;
import org.vader.core.server.repository.LlmRequestOutboxMessageRepository;
import org.vader.core.server.repository.OutboxMessageRepository;
import org.vader.core.server.service.io.AbstractInbox;

/**
 * Inbox for local-LLM requests: pops the oldest pending request and actually performs it, via
 * {@link InferenceTurnLlmExecutor} or {@link DecompositionLlmExecutor} depending on the message's
 * {@code kind}, then writes the response back onto the same row for {@link LlmRequestQueue}'s
 * blocked, polling caller to read.
 *
 * <p>{@link #maxOpenMessages} is hardcoded to {@code 1}, not configurable -- that ceiling, checked
 * against the database (not any in-process count), is the entire mechanism ensuring only one
 * request is ever in flight against Ollama at a time, regardless of how many {@code core-server}
 * replicas are running.</p>
 *
 * <p>Drains are triggered two ways, same as every other inbox in this package: on a fixed
 * schedule (a safety net -- also the only way a replica other than the one that enqueued a
 * request, or the one currently mid-{@code handle} for an earlier request, ever notices this one
 * is ready), and immediately after an enqueue commits on this replica.</p>
 */
@Service
public class LlmRequestInbox extends AbstractInbox<LlmRequestOutboxMessageEntity> {

    private static final String LLM_REQUEST = "LlmRequest";

    private static final TypeReference<List<ConversationMessage>> MESSAGES_TYPE =
        new TypeReference<>() {};

    @Autowired
    private LlmRequestOutboxMessageRepository messageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Lazy: this inbox is itself one of BackpressureRegistry's queues, and both executors depend
    // on McpToolCallbackRegistry, which -- in "local" mode -- drags in the whole Spring AI
    // tool-calling graph, wiring BackpressureTools back to BackpressureRegistry and closing a
    // cycle back through this bean. Same reasoning as ClientPromptInbox's lazy WorkflowService.
    @Autowired
    @Lazy
    private InferenceTurnLlmExecutor inferenceTurnExecutor;

    @Autowired
    @Lazy
    private DecompositionLlmExecutor decompositionExecutor;

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
        var responseJson = switch (message.getKind()) {
            case INFERENCE_TURN -> this.toJson(
                this.inferenceTurnExecutor.execute(this.readMessages(message.getRequestJson())));
            case DECOMPOSITION -> this.toJson(
                this.decompositionExecutor.execute(message.getRequestJson()));
        };
        message.setResponseJson(responseJson);
        this.messageRepository.save(message);
    }

    private List<ConversationMessage> readMessages(final String requestJson) {
        try {
            return this.objectMapper.readValue(requestJson, MESSAGES_TYPE);
        } catch (JsonProcessingException e) {
            throw new LlmRequestQueueException(
                "Could not deserialize inference-turn request", e);
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
     * Scheduled safety-net drain -- deliberately much more frequent than the other inboxes' (they
     * default to 1000ms): this is the only path by which a replica other than the enqueuing one
     * (or the one already busy with an earlier request) discovers a freshly-freed claim slot, so
     * its interval directly adds to how long every blocked {@link LlmRequestQueue} caller waits.
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
