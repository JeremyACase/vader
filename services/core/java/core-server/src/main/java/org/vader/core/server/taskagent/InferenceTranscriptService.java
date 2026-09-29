package org.vader.core.server.taskagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;
import org.vader.common.model.vader.entity.TaskAttemptTranscriptEntity;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.InferenceTurn;

/**
 * Completes an assignment's inference turns and logs each to the attempt's transcript -- this,
 * not a direct model call, is the only path a harness has to any LLM.
 */
@Service
public class InferenceTranscriptService {

    @Autowired
    private TaskAttemptLifecycleService lifecycleService;

    @Autowired
    private InferenceGateway inferenceGateway;

    @Autowired
    private TaskAttemptTranscriptRepository transcriptRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Completes one inference turn on behalf of an assignment and logs it to the transcript.
     *
     * @param assignmentId the calling harness's assignment id
     * @param messages the running conversation so far
     * @return the model's response: either a final answer, or a request to call tools
     */
    @Transactional
    public InferenceTurn recordInferenceTurn(
            final String assignmentId, final List<ConversationMessage> messages) {
        var attempt = this.lifecycleService.requireOpen(assignmentId);
        var turn = this.inferenceGateway.complete(messages);
        this.recordTranscript(assignmentId, attempt, messages, turn);
        return turn;
    }

    /**
     * Persists this turn's transcript row, storing only the messages newly appended since the
     * previous turn -- not the whole running conversation the harness resends on every call.
     * That full history is already durable elsewhere: each prior turn's own {@code response} and
     * the {@link TaskAttemptToolCallEntity} audit rows {@code TaskToolInvocationService} writes
     * immediately. Re-persisting it here on every turn would mean storing the same tool-call
     * content, in plaintext, once per remaining turn of the run.
     */
    private void recordTranscript(
            final String assignmentId, final TaskAttemptEntity attempt,
            final List<ConversationMessage> messages, final InferenceTurn turn) {
        var previousMessageCount = this.transcriptRepository
            .findFirstByTaskAttemptIdOrderByTurnIndexDesc(assignmentId)
            .map(TaskAttemptTranscriptEntity::getMessageCount)
            .orElse(0);
        var newMessages = messages.subList(previousMessageCount, messages.size());

        var transcript = new TaskAttemptTranscriptEntity();
        transcript.setTaskAttempt(attempt);
        transcript.setTurnIndex((int) this.transcriptRepository.countByTaskAttemptId(assignmentId));
        transcript.setPrompt(this.toJson(newMessages));
        transcript.setMessageCount(messages.size());
        transcript.setResponse(this.responseTextFor(turn));
        transcript.setTokensSpent(turn.tokensSpent());
        transcript.setFinishReason(turn.finishReason());
        this.transcriptRepository.save(transcript);
    }

    /**
     * What the transcript shows as the model's reply: its text, its tool calls, or both. Tool
     * calls are recorded whenever there are any, since a tool-call turn's content may be an empty
     * string rather than {@code null}.
     */
    private String responseTextFor(final InferenceTurn turn) {
        var text = Objects.requireNonNullElse(turn.content(), "").strip();
        var toolCalls = Objects.requireNonNullElse(turn.toolCalls(), List.of());
        var toolCallsJson = toolCalls.isEmpty() ? "" : this.toJson(toolCalls);
        return Stream.of(text, toolCallsJson)
            .filter(part -> !part.isEmpty())
            .collect(Collectors.joining("\n\n"));
    }

    private String toJson(final Object value) {
        String result;
        try {
            result = this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize transcript content", e);
        }
        return result;
    }
}
