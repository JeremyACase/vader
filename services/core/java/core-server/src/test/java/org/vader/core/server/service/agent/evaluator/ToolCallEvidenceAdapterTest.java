package org.vader.core.server.service.agent.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;
import org.vader.core.server.repository.TaskAttemptToolCallRepository;

class ToolCallEvidenceAdapterTest {

    private static final String ATTEMPT_ID = "aaaaaaaa-1111-2222-3333-444444444444";

    private TaskAttemptToolCallRepository repository;
    private ToolCallEvidenceAdapter adapter;

    @BeforeEach
    void setUp() {
        this.repository = mock(TaskAttemptToolCallRepository.class);
        this.adapter = new ToolCallEvidenceAdapter();
        ReflectionTestUtils.setField(
            this.adapter, "taskAttemptToolCallRepository", this.repository);
        ReflectionTestUtils.setField(this.adapter, "objectMapper", new ObjectMapper());
    }

    private void lastToolCall(final String toolName, final String resultJson) {
        var toolCall = new TaskAttemptToolCallEntity();
        toolCall.setToolName(toolName);
        toolCall.setResultJson(resultJson);
        when(this.repository.findFirstByTaskAttemptIdOrderByCreatedAtDesc(ATTEMPT_ID))
            .thenReturn(Optional.of(toolCall));
    }

    @Test
    void lastToolCallEvidence_isNullWhenTheAttemptMadeNoToolCalls() {
        when(this.repository.findFirstByTaskAttemptIdOrderByCreatedAtDesc(ATTEMPT_ID))
            .thenReturn(Optional.empty());

        assertThat(this.adapter.lastToolCallEvidence(ATTEMPT_ID)).isNull();
    }

    @Test
    void lastToolCallEvidence_reportsFailedRunWithItsLastStderrLine() {
        this.lastToolCall("run_python_code", """
            {"stdout":"",
             "stderr":"Traceback...\\n  File x\\nTypeError: can only concatenate str\\n",
             "exitCode":1,"timedOut":false}
            """);

        assertThat(this.adapter.lastToolCallEvidence(ATTEMPT_ID))
            .contains("run_python_code")
            .contains("FAILED with exit code 1")
            .contains("TypeError: can only concatenate str");
    }

    @Test
    void lastToolCallEvidence_reportsTimedOutRun() {
        this.lastToolCall("run_python_code", "{\"exitCode\":0,\"timedOut\":true}");

        assertThat(this.adapter.lastToolCallEvidence(ATTEMPT_ID)).contains("TIMED OUT");
    }

    @Test
    void lastToolCallEvidence_reportsSuccessfulRun() {
        this.lastToolCall("run_python_code", "{\"stdout\":\"ok\",\"exitCode\":0}");

        assertThat(this.adapter.lastToolCallEvidence(ATTEMPT_ID))
            .contains("completed without error");
    }

    @Test
    void lastToolCallEvidence_toleratesResultThatIsNotJson() {
        this.lastToolCall("get_object_content", "not json");

        assertThat(this.adapter.lastToolCallEvidence(ATTEMPT_ID))
            .contains("get_object_content")
            .contains("not interpretable");
    }
}
