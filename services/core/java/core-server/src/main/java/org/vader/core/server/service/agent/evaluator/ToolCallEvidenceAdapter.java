package org.vader.core.server.service.agent.evaluator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;
import org.vader.core.server.repository.TaskAttemptToolCallRepository;

/**
 * Adapts an attempt's logged tool calls into one line of evidence an evaluator can read: what the
 * last call was and whether it errored.
 *
 * <p>An evaluator otherwise sees only the agent's own final text, and a small model reading "here
 * is the corrected approach" cannot tell that the code before it raised. The tool-call log can,
 * deterministically. Only the {@code run_python_code} result shape ({@code exitCode},
 * {@code timedOut}, {@code stderr}) is interpreted; any other tool's result is reported by name
 * alone.</p>
 */
@Service
public class ToolCallEvidenceAdapter {

    @Autowired
    private TaskAttemptToolCallRepository taskAttemptToolCallRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Summarizes one attempt's last tool call.
     *
     * @param attemptId the attempt's id
     * @return a one-line summary, or {@code null} if the attempt made no tool calls
     */
    public String lastToolCallEvidence(final String attemptId) {
        return this.taskAttemptToolCallRepository
            .findFirstByTaskAttemptIdOrderByCreatedAtDesc(attemptId)
            .map(this::describe)
            .orElse(null);
    }

    private String describe(final TaskAttemptToolCallEntity toolCall) {
        var result = this.parse(toolCall.getResultJson());
        var outcome = Objects.isNull(result) ? "result not interpretable" : outcomeOf(result);
        return "The agent's last tool call was " + toolCall.getToolName() + ": " + outcome + ".";
    }

    private JsonNode parse(final String resultJson) {
        JsonNode result = null;
        if (Objects.nonNull(resultJson)) {
            try {
                result = this.objectMapper.readTree(resultJson);
            } catch (JsonProcessingException e) {
                result = null;
            }
        }
        return result;
    }

    private static String outcomeOf(final JsonNode result) {
        String outcome;
        if (result.path("timedOut").asBoolean(false)) {
            outcome = "it TIMED OUT";
        } else if (result.path("exitCode").asInt(0) != 0) {
            outcome = "it FAILED with exit code " + result.path("exitCode").asInt()
                + lastStderrLine(result);
        } else {
            outcome = "it completed without error";
        }
        return outcome;
    }

    private static String lastStderrLine(final JsonNode result) {
        return Arrays.stream(result.path("stderr").asText("").split("\n"))
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .reduce((first, second) -> second)
            .map(line -> " (" + line + ")")
            .orElse("");
    }
}
