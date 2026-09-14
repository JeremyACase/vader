package org.vader.core.server.service.strategies.synthesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.models.TaskOutcome;
import org.vader.core.server.models.WorkflowSynthesisRequest;

class LocalWorkflowSynthesisStrategyTest {

    private ChatModel chatModel;
    private LocalWorkflowSynthesisStrategy strategy;

    @BeforeEach
    void setUp() {
        this.chatModel = mock(ChatModel.class);
        when(this.chatModel.getDefaultOptions())
            .thenReturn(ToolCallingChatOptions.builder().build());

        this.strategy = new LocalWorkflowSynthesisStrategy();
        ReflectionTestUtils.setField(
            this.strategy, "chatClientBuilder", ChatClient.builder(this.chatModel));
    }

    private static ChatResponse responseWith(final String assistantText) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(assistantText))));
    }

    @Test
    void synthesize_sendsThePromptObjectiveAndEveryTaskOutcome() {
        when(this.chatModel.call(any(Prompt.class)))
            .thenReturn(responseWith("The spreadsheet tracks quarterly sales by region."));
        var request = new WorkflowSynthesisRequest(
            "What does this spreadsheet show?",
            "Analyze the spreadsheet",
            List.of(
                new TaskOutcome("Read the file", true, "Found 3 sheets."),
                new TaskOutcome("Chart the trend", false, "No charting library.")));

        var result = this.strategy.synthesize(request);

        assertThat(result).isEqualTo("The spreadsheet tracks quarterly sales by region.");
        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(this.chatModel).call(promptCaptor.capture());
        var sentText = promptCaptor.getValue().getContents();
        assertThat(sentText)
            .contains("What does this spreadsheet show?")
            .contains("Analyze the spreadsheet")
            .contains("Read the file")
            .contains("Found 3 sheets.")
            .contains("Chart the trend")
            .contains("No charting library.");
    }
}
