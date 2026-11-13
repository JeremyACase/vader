package org.vader.core.server.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.llm.PromptTemplateRegistryObjectMother;
import org.vader.core.server.orchestration.model.AttachedFile;
import org.vader.core.server.orchestration.model.DecompositionRequest;

class DecompositionLlmExecutorTest {

    // The lean LlmTaskPlan shape the model is asked to produce (objective + tasks only).
    private static final String VALID_PLAN_JSON =
        "{\"objective\":\"ship it\",\"tasks\":"
            + "[{\"title\":\"design\",\"description\":\"draw it\"},"
            + "{\"title\":\"build\",\"description\":\"code it\"}]}";

    private static ChatResponse responseWith(final String assistantText) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(assistantText))));
    }

    private static DecompositionRequest request(final String clientPromptText) {
        return new DecompositionRequest(clientPromptText, List.of(), null);
    }

    private static Prompt capturedPrompt(final ChatModel chatModel) {
        var promptCaptor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        return promptCaptor.getValue();
    }

    private static DecompositionLlmExecutor executor(final ChatClient.Builder builder) {
        var executor = new DecompositionLlmExecutor();
        ReflectionTestUtils.setField(executor, "chatClientBuilder", builder);
        ReflectionTestUtils.setField(
            executor, "promptTemplateRegistry", PromptTemplateRegistryObjectMother.loaded());
        return executor;
    }

    @Test
    void execute_returnsTheParsedPlan() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(responseWith(VALID_PLAN_JSON));

        var plan = executor(ChatClient.builder(chatModel)).execute(request("Ship onboarding"));

        assertThat(plan.objective()).isEqualTo("ship it");
        assertThat(plan.tasks()).hasSize(2);
    }

    @Test
    void execute_offersThePlannerNoTools() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(responseWith(VALID_PLAN_JSON));

        executor(ChatClient.builder(chatModel)).execute(request("anything"));

        var prompt = capturedPrompt(chatModel);
        assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks())
            .isNullOrEmpty();
        assertThat(prompt.getSystemMessage().getText())
            .doesNotContain("You have been given a set of tools");
    }


    @Test
    void execute_onRevision_keepsUserTextIntactAndSendsGuidanceAsInstructions() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(responseWith(VALID_PLAN_JSON));

        executor(ChatClient.builder(chatModel)).execute(new DecompositionRequest(
            "Analyze this spreadsheet", List.of(), "the tasks are redundant"));

        var prompt = capturedPrompt(chatModel);
        // Spring AI's entity() appends its own JSON-format instructions after the user's text;
        // what matters is that the user's words come through first and the critique does not.
        assertThat(prompt.getUserMessage().getText())
            .startsWith("Analyze this spreadsheet")
            .doesNotContain("the tasks are redundant");
        assertThat(prompt.getSystemMessage().getText())
            .contains("the tasks are redundant")
            .contains("do not quote, restate, or respond to this feedback");
    }

    @Test
    void execute_withAttachedFiles_namesThemInTheInstructionsAndKeepsUserTextIntact() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(responseWith(VALID_PLAN_JSON));

        executor(ChatClient.builder(chatModel)).execute(new DecompositionRequest(
            "Analyze this spreadsheet",
            List.of(new AttachedFile("Finances.xlsx", "application/vnd.ms-excel")), null));

        var prompt = capturedPrompt(chatModel);
        assertThat(prompt.getUserMessage().getText())
            .startsWith("Analyze this spreadsheet")
            .doesNotContain("Finances.xlsx");
        assertThat(prompt.getSystemMessage().getText())
            .contains("- \"Finances.xlsx\" (type: application/vnd.ms-excel)")
            .contains("You cannot see their contents");
    }

    @Test
    void execute_withoutAttachedFiles_sendsNoAttachedFilesSection() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(responseWith(VALID_PLAN_JSON));

        executor(ChatClient.builder(chatModel)).execute(request("Plan a party"));

        assertThat(capturedPrompt(chatModel).getSystemMessage().getText())
            .doesNotContain("The user attached these files")
            .contains("No agent can ask the user anything");
    }

    @Test
    void execute_tellsThePlannerToEndWithAnUploadedDeliverable() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(responseWith(VALID_PLAN_JSON));

        executor(ChatClient.builder(chatModel)).execute(request("Write me a script"));

        assertThat(capturedPrompt(chatModel).getSystemMessage().getText())
            .contains("upload_object")
            .contains("the only\nfiles that carry over are those a task saves with upload_object")
            .contains("standard library");
    }

    @Test
    void execute_onFirstAttempt_sendsNoRevisionInstructions() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(responseWith(VALID_PLAN_JSON));

        executor(ChatClient.builder(chatModel)).execute(request("Analyze this spreadsheet"));

        assertThat(capturedPrompt(chatModel).getSystemMessage().getText())
            .doesNotContain("A reviewer rejected your previous plan");
    }

    /**
     * Regression test for spring-projects/spring-ai#3537: reusing one {@link ChatClient} across
     * calls corrupts its internal advisor-chain state under concurrency and intermittently fails
     * with {@code IllegalStateException: No CallAdvisors available to execute}.
     */
    @Test
    void execute_buildsFreshChatClientPerCall() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(responseWith(VALID_PLAN_JSON));
        var builder = spy(ChatClient.builder(chatModel));
        var executor = executor(builder);

        executor.execute(request("first"));
        executor.execute(request("second"));

        verify(builder, times(2)).build();
    }
}
