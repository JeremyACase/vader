package org.vader.core.server.taskagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.ConversationRole;
import org.vader.core.server.taskagent.model.InferenceToolCall;

class PromptCharacterCounterTest {

    private final PromptCharacterCounter counter = new PromptCharacterCounter();

    private static ToolCallback toolWith(
            final String name, final String description, final String inputSchema) {
        var tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(ToolDefinition.builder()
            .name(name).description(description).inputSchema(inputSchema).build());
        return tool;
    }

    @Test
    void count_addsEveryMessagesContent() {
        var first = Instancio.create(String.class);
        var second = Instancio.create(String.class);
        var messages = List.of(
            new ConversationMessage(ConversationRole.SYSTEM, first, null, null, null),
            new ConversationMessage(ConversationRole.USER, second, null, null, null));

        assertThat(this.counter.count(messages, List.of()))
            .isEqualTo(first.length() + second.length());
    }

    @Test
    void count_includesToolCallsAndToolResultsNames() {
        var call = new InferenceToolCall("call-1", "run_python_code", "{\"code\":\"x = 1\"}");
        var messages = List.of(
            new ConversationMessage(ConversationRole.ASSISTANT, null, List.of(call), null, null),
            new ConversationMessage(
                ConversationRole.TOOL, "ok", null, "call-1", "run_python_code"));

        var expected = call.name().length() + call.argumentsJson().length()
            + "ok".length() + "run_python_code".length();
        assertThat(this.counter.count(messages, List.of())).isEqualTo(expected);
    }

    @Test
    void count_includesEveryOfferedToolsDefinition() {
        var tool = toolWith("upload_object", "Uploads a file.", "{\"type\":\"object\"}");

        assertThat(this.counter.count(List.of(), List.of(tool))).isEqualTo(
            "upload_object".length() + "Uploads a file.".length()
                + "{\"type\":\"object\"}".length());
    }
}
