package org.vader.core.server.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;
import org.vader.core.server.taskagent.TaskAttemptToolCallRepository;

/**
 * Adapts an attempt's logged tool calls into one line of evidence an evaluator can read: what the
 * last call was and whether it errored.
 *
 * <p>The agent's final text can read as success right after its code raised; the tool-call log
 * says so deterministically. Only the {@code run_python_code} result shape ({@code exitCode},
 * {@code timedOut}, {@code stderr}) is interpreted; other tools are reported by name alone.</p>
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
