package org.vader.core.server.service.strategies.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.exceptions.OrchestratorResponseException;
import org.vader.core.exceptions.OrchestratorUnavailableException;

class LocalLlmOrchestrationStrategyTest {

    // The lean LlmTaskPlan shape the model is asked to produce (objective + tasks only).
    private static final String VALID_PLAN_JSON =
        "{\"objective\":\"ship it\",\"tasks\":"
            + "[{\"title\":\"design\",\"description\":\"draw it\"},"
            + "{\"title\":\"build\",\"description\":\"code it\"}]}";

    private final ObjectMapper objectMapper =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        this.chatModel = mock(ChatModel.class);
        when(this.chatModel.getDefaultOptions())
            .thenReturn(ToolCallingChatOptions.builder().build());
    }

    private LocalLlmOrchestrationStrategy strategy(
        final boolean fallbackToStatic, final ToolCallbackProvider... providers) {

        @SuppressWarnings("unchecked")
        ObjectProvider<ToolCallbackProvider> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.stream()).thenReturn(Stream.of(providers));

        var strategy = new LocalLlmOrchestrationStrategy();
        ReflectionTestUtils.setField(
            strategy, "chatClientBuilder", ChatClient.builder(this.chatModel));
        ReflectionTestUtils.setField(strategy, "toolCallbackProviders", objectProvider);
        ReflectionTestUtils.setField(strategy, "objectMapper", this.objectMapper);
        ReflectionTestUtils.setField(strategy, "fallbackToStatic", fallbackToStatic);
        strategy.wire();
        return strategy;
    }

    private static ChatResponse responseWith(final String assistantText) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(assistantText))));
    }

    private static ClientPrompt promptOf(final String text) {
        var prompt = new ClientPrompt();
        prompt.setText(text);
        return prompt;
    }

    @Test
    void orchestrate_sendsPromptAndReturnsSchemaValidTaskPlanJson() throws Exception {
        when(this.chatModel.call(any(Prompt.class))).thenReturn(responseWith(VALID_PLAN_JSON));

        var result = this.strategy(true).orchestrate(promptOf("Ship onboarding"));

        var plan = this.objectMapper.readValue(result, TaskPlan.class);
        assertThat(plan.getObjective()).isEqualTo("ship it");
        assertThat(plan.getTaskGraph().getTasks()).hasSize(2);
        assertThat(plan.getTaskGraph().getTasks().get(0).getTitle()).isEqualTo("design");
        // The lean LlmTaskPlan shape means the model is never asked to invent an id/timestamps,
        // so the built TaskPlan leaves them null and passes bean validation downstream.
        assertThat(plan.getId()).isNull();

        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(this.chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getContents())
            .contains("Ship onboarding")
            .contains("planning assistant");
    }

    @Test
    void orchestrate_whenModelReturnsNoTasks_throwsOrchestratorResponse() {
        when(this.chatModel.call(any(Prompt.class)))
            .thenReturn(responseWith("{\"objective\":\"ship it\",\"tasks\":[]}"));

        assertThatThrownBy(() -> this.strategy(true).orchestrate(promptOf("plan a thing")))
            .isInstanceOf(OrchestratorResponseException.class)
            .hasMessageContaining("usable task plan");
    }

    @Test
    void orchestrate_attachesEveryRegisteredToolCallback() {
        when(this.chatModel.call(any(Prompt.class))).thenReturn(responseWith(VALID_PLAN_JSON));
        var provider = MethodToolCallbackProvider.builder()
            .toolObjects(new DummyTools())
            .build();

        this.strategy(true, provider).orchestrate(promptOf("anything"));

        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(this.chatModel).call(promptCaptor.capture());
        var options = (ToolCallingChatOptions) promptCaptor.getValue().getOptions();
        assertThat(options.getToolCallbacks())
            .anyMatch(callback -> callback.getToolDefinition().name().equals("dummy_tool"));
    }

    @Test
    void orchestrate_withNoToolProviders_stillWorks() {
        when(this.chatModel.call(any(Prompt.class))).thenReturn(responseWith(VALID_PLAN_JSON));

        var result = this.strategy(true).orchestrate(promptOf("plan a thing"));

        assertThat(result).contains("\"objective\":\"ship it\"");
    }

    @Test
    void orchestrate_whenModelUnreachableAndFallbackEnabled_returnsStaticPlan() throws Exception {
        when(this.chatModel.call(any(Prompt.class)))
            .thenThrow(new ResourceAccessException("connection refused"));

        var result = this.strategy(true).orchestrate(promptOf("plan a thing"));

        var plan = this.objectMapper.readValue(result, TaskPlan.class);
        assertThat(plan.getObjective()).isNotBlank();
        assertThat(plan.getTaskGraph().getTasks()).isNotEmpty();
        assertThat(result).isEqualTo(StaticTaskPlan.JSON);
    }

    @Test
    void orchestrate_whenModelUnreachableAndFallbackDisabled_throwsOrchestratorUnavailable() {
        when(this.chatModel.call(any(Prompt.class)))
            .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> this.strategy(false).orchestrate(promptOf("plan a thing")))
            .isInstanceOf(OrchestratorUnavailableException.class)
            .hasMessageContaining("local LLM");
    }

    @Test
    void orchestrate_whenResponseIsNotUsablePlan_throwsOrchestratorResponse() {
        when(this.chatModel.call(any(Prompt.class)))
            .thenReturn(responseWith("Sure! Here is your plan: do the thing."));

        assertThatThrownBy(() -> this.strategy(true).orchestrate(promptOf("plan a thing")))
            .isInstanceOf(OrchestratorResponseException.class);
    }

    static final class DummyTools {

        @Tool(name = "dummy_tool", description = "A tool that does nothing, for tests.")
        public String doNothing() {
            return "ok";
        }
    }
}
