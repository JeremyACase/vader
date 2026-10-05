package org.vader.core.server.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.review.model.EvaluationRequest;
import org.vader.core.server.review.model.RemainingSubtask;
import org.vader.core.server.workflow.model.PlanStep;

class EvaluationLlmExecutorTest {

    private static final EvaluationRequest REQUEST = new EvaluationRequest(
        "the user request", "the objective", List.of(), "title", "description",
        TaskAttemptStatus.SUCCEEDED, "the result", null, List.of(), null);

    private static ChatResponse responseWith(final String assistantText) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(assistantText))));
    }

    private static EvaluationLlmExecutor executor(final ChatClient.Builder builder) {
        var executor = new EvaluationLlmExecutor();
        ReflectionTestUtils.setField(executor, "chatClientBuilder", builder);
        return executor;
    }

    @Test
    void execute_returnsTheParsedVerdict() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
            responseWith("{\"passed\":true,\"reasoning\":\"looks correct\"}"));

        var verdict = executor(ChatClient.builder(chatModel)).execute(REQUEST);

        assertThat(verdict.passed()).isTrue();
        assertThat(verdict.reasoning()).isEqualTo("looks correct");
    }

    @Test
    void execute_parsesRemainingSubtasksAndDefaultsThemToEmptyWhenOmitted() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
            responseWith("{\"passed\":false,\"reasoning\":\"code never ran\","
                + "\"remainingSubtasks\":[{\"title\":\"Run the fix\","
                + "\"description\":\"Run the corrected code.\"}]}"),
            responseWith("{\"passed\":false,\"reasoning\":\"no progress\"}"));
        var executor = executor(ChatClient.builder(chatModel));

        var decomposable = executor.execute(REQUEST);
        var plainFailure = executor.execute(REQUEST);

        assertThat(decomposable.remainingSubtasks())
            .containsExactly(new RemainingSubtask("Run the fix", "Run the corrected code."));
        assertThat(plainFailure.remainingSubtasks()).isEmpty();
    }

    @Test
    void execute_includesTheRequestPlanAndToolCallEvidenceInThePrompt() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
            responseWith("{\"passed\":false,\"reasoning\":\"last run failed\"}"));
        var request = new EvaluationRequest(
            "Can this spreadsheet work in fiscal quarters?", "Assess a fiscal-quarter conversion",
            List.of(
                new PlanStep("Analyze", "Describe the spreadsheet.", 0, false),
                new PlanStep("title", "description", 1, true),
                new PlanStep("Assess feasibility", "Decide if it can work.", 0, false)),
            "title", "description",
            TaskAttemptStatus.SUCCEEDED, "Let's proceed.", null,
            List.of(), "The agent's last tool call was run_python_code: it FAILED.");

        executor(ChatClient.builder(chatModel)).execute(request);

        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getContents())
            .contains("run_python_code: it FAILED")
            .contains("The user's request: Can this spreadsheet work in fiscal quarters?")
            .contains("(objective: Assess a fiscal-quarter conversion)")
            .contains("- Analyze: Describe the spreadsheet.\n"
                + "  - title: description   <-- the task under review\n"
                + "- Assess feasibility: Decide if it can work.")
            .contains("remainingSubtasks");
    }
}
