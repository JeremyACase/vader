package org.vader.core.server.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
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
import org.springframework.web.client.ResourceAccessException;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.models.TaskPlanRefinementRequest;
import org.vader.core.server.models.TaskPlanRefinementVerdict;

class TaskPlanRefinementLlmExecutorTest {

    private static final TaskPlanRefinementRequest REQUEST;

    static {
        var taskPlan = new TaskPlan();
        taskPlan.setObjective("ship it");
        taskPlan.setTaskGraph(new TaskGraph());
        REQUEST = new TaskPlanRefinementRequest("do the thing", taskPlan);
    }

    private static ChatResponse responseWith(final String assistantText) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(assistantText))));
    }

    private static TaskPlanRefinementLlmExecutor executor(final ChatClient.Builder builder) {
        var executor = new TaskPlanRefinementLlmExecutor();
        ReflectionTestUtils.setField(executor, "chatClientBuilder", builder);
        return executor;
    }

    @Test
    void execute_returnsTheParsedVerdict() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
            responseWith("{\"needsRevision\":false,\"reasoning\":\"looks fine\"}"));

        var outcome = executor(ChatClient.builder(chatModel)).execute(REQUEST);

        assertThat(outcome.isUnreachable()).isFalse();
        assertThat(outcome.verdict().needsRevision()).isFalse();
        assertThat(outcome.verdict().reasoning()).isEqualTo("looks fine");
        // A model that found nothing missing may omit the list entirely.
        assertThat(outcome.verdict().missingDependencies()).isEmpty();
    }

    @Test
    void execute_parsesTheMissingDependenciesTheCriticNamed() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(responseWith(
            "{\"needsRevision\":false,\"reasoning\":\"reading must come first\","
                + "\"missingDependencies\":[{\"task\":\"Identify Key Data\","
                + "\"dependsOn\":[\"Read Spreadsheet\"]}]}"));

        var outcome = executor(ChatClient.builder(chatModel)).execute(REQUEST);

        assertThat(outcome.verdict().missingDependencies()).containsExactly(
            new TaskPlanRefinementVerdict.MissingDependency(
                "Identify Key Data", List.of("Read Spreadsheet")));
    }

    @Test
    void execute_showsTheCriticTitlesNotIds() {
        var read = new Task();
        read.setId(UUID.randomUUID().toString());
        read.setTitle("Read Spreadsheet");
        read.setDescription("Open it.");
        var identify = new Task();
        identify.setId(UUID.randomUUID().toString());
        identify.setTitle("Identify Key Data");
        identify.setDescription("Find patterns.");
        identify.setDependsOnTaskIds(List.of(read.getId()));
        var taskGraph = new TaskGraph();
        taskGraph.setTasks(List.of(read, identify));
        var taskPlan = new TaskPlan();
        taskPlan.setObjective("analyze it");
        taskPlan.setTaskGraph(taskGraph);
        var chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
            responseWith("{\"needsRevision\":false,\"reasoning\":\"fine\"}"));

        executor(ChatClient.builder(chatModel))
            .execute(new TaskPlanRefinementRequest("analyze this", taskPlan));

        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getUserMessage().getText())
            .contains("1. \"Read Spreadsheet\" -- Open it.\n   depends on: nothing")
            .contains("2. \"Identify Key Data\" -- Find patterns.\n"
                + "   depends on: \"Read Spreadsheet\"")
            .doesNotContain(read.getId());
    }

    @Test
    void execute_whenTheModelIsUnreachable_returnsAnUnreachableOutcomeInsteadOfThrowing() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
            .thenThrow(new ResourceAccessException("connection refused"));

        var outcome = executor(ChatClient.builder(chatModel)).execute(REQUEST);

        assertThat(outcome.isUnreachable()).isTrue();
        assertThat(outcome.unreachableReason()).contains("connection refused");
        assertThat(outcome.verdict()).isNull();
    }
}
