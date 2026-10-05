package org.vader.core.server.workflow;

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
import org.vader.core.server.workflow.model.DeliveredFile;
import org.vader.core.server.workflow.model.TaskOutcome;
import org.vader.core.server.workflow.model.WorkflowSynthesisRequest;

class WorkflowSynthesisLlmExecutorTest {

    private ChatModel chatModel;
    private WorkflowSynthesisLlmExecutor executor;

    @BeforeEach
    void setUp() {
        this.chatModel = mock(ChatModel.class);
        when(this.chatModel.getDefaultOptions())
            .thenReturn(ToolCallingChatOptions.builder().build());

        this.executor = new WorkflowSynthesisLlmExecutor();
        ReflectionTestUtils.setField(
            this.executor, "chatClientBuilder", ChatClient.builder(this.chatModel));
    }

    private static ChatResponse responseWith(final String assistantText) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(assistantText))));
    }

    @Test
    void execute_sendsThePromptObjectiveAndEveryTaskOutcome() {
        when(this.chatModel.call(any(Prompt.class)))
            .thenReturn(responseWith("The spreadsheet tracks quarterly sales by region."));
        var request = new WorkflowSynthesisRequest(
            "What does this spreadsheet show?",
            "Analyze the spreadsheet",
            List.of(
                new TaskOutcome("Read the file", true, "Found 3 sheets."),
                new TaskOutcome("Chart the trend", false, "No charting library.")),
            List.of());

        var result = this.executor.execute(request);

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

    @Test
    void execute_showsDeliveredFilesContentAndAsksForItVerbatim() {
        when(this.chatModel.call(any(Prompt.class))).thenReturn(responseWith("Here it is."));
        var request = new WorkflowSynthesisRequest(
            "Write a hello world server",
            "Deliver a server script",
            List.of(new TaskOutcome("Write the server", true, "Uploaded server.py.")),
            List.of(
                new DeliveredFile("server.py", "/download/o1", "print('hello world')"),
                new DeliveredFile("chart.png", "/download/o2", null)));

        this.executor.execute(request);

        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(this.chatModel).call(promptCaptor.capture());
        var sentText = promptCaptor.getValue().getContents();
        assertThat(sentText)
            .contains("File server.py:\nprint('hello world')")
            .contains("File chart.png:\n(content not shown")
            .contains("exactly as")
            .doesNotContain("/download/o1");
    }

    @Test
    void execute_withNoDeliveredFiles_leavesOutTheFilesSection() {
        when(this.chatModel.call(any(Prompt.class))).thenReturn(responseWith("Done."));
        var request = new WorkflowSynthesisRequest(
            "What is 2 + 2?", "Answer the sum",
            List.of(new TaskOutcome("Add", true, "4")), List.of());

        this.executor.execute(request);

        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(this.chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getContents()).doesNotContain("saved these files");
    }
}
