package org.vader.core.server.taskagent;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.InferenceToolCall;

/**
 * Measures how much text an inference turn sends the model: the whole conversation plus the tool
 * definitions offered alongside it, both of which occupy the context window. Characters, not
 * tokens -- the provider's token count can leave out a cached prefix, and this never does.
 */
@Component
public class PromptCharacterCounter {

    /**
     * Counts the characters one turn sends.
     *
     * @param messages the conversation sent
     * @param tools the tools offered with it
     * @return message contents, tool-call names and arguments, and tool definitions, in characters
     */
    public long count(final List<ConversationMessage> messages, final List<ToolCallback> tools) {
        var messageCharacters = messages.stream().mapToLong(PromptCharacterCounter::charactersOf);
        var toolCharacters = tools.stream().mapToLong(PromptCharacterCounter::charactersOf);
        return messageCharacters.sum() + toolCharacters.sum();
    }

    private static long charactersOf(final ConversationMessage message) {
        var toolCalls =
            Objects.requireNonNullElse(message.toolCalls(), List.<InferenceToolCall>of());
        var callCharacters = toolCalls.stream()
            .mapToLong(call -> lengthOf(call.name()) + lengthOf(call.argumentsJson()))
            .sum();
        return lengthOf(message.content()) + lengthOf(message.toolName()) + callCharacters;
    }

    private static long charactersOf(final ToolCallback tool) {
        var definition = tool.getToolDefinition();
        return Stream.of(definition.name(), definition.description(), definition.inputSchema())
            .mapToLong(PromptCharacterCounter::lengthOf)
            .sum();
    }

    private static long lengthOf(final String text) {
        return Objects.isNull(text) ? 0L : text.length();
    }
}
