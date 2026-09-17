package org.vader.core.server.service.strategies.inference;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.InferenceToolCall;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.service.registries.McpToolCallbackRegistry;

/**
 * Completes a turn against the in-cluster Ollama instance via Spring AI's {@link ChatClient}.
 * Active only when {@code vader.orchestrator.type} is {@code local}.
 *
 * <p>Every tool currently registered (via {@link McpToolCallbackRegistry}, exactly like the
 * decomposition orchestrator) is offered to the model on every turn -- but with Spring AI's
 * internal tool execution turned off. A tool call the model requests therefore comes back as-is,
 * in {@link InferenceTurn#toolCalls()}, instead of being silently resolved inside this call: the
 * harness is the one that actually invokes it (via {@code /vader/core-server/agent/tool-calls})
 * and folds the result back into the next turn's conversation. The harness's own action loop
 * lives in the harness process, not here; this stays a thin, stateless proxy to the model.</p>
 *
 * <p>Two Spring AI pitfalls had to be avoided here, both of which manifest as
 * {@code IllegalStateException: No CallAdvisors available to execute}:</p>
 * <ul>
 * <li>Calling a second terminal method (e.g. {@code chatResponse()}) on a
 * {@code CallResponseSpec} after already calling another one (e.g. {@code content()}) re-runs
 * the same, already-consumed advisor chain and fails deterministically, every time -- not
 * intermittently. Both the content and the token usage are read off a single
 * {@link ChatResponse} from one {@code chatResponse()} call instead.</li>
 * <li>A fresh {@link ChatClient} is still built per call rather than cached on the bean:
 * separately, concurrent {@code .call()} invocations against one shared instance can corrupt
 * Spring AI's internal advisor-chain state (spring-projects/spring-ai#3537, still open as of
 * 1.0.9). Building from {@link ChatClient.Builder} is cheap (it wraps an already-configured
 * {@code ChatModel}, no new network connection), so there is no real cost to paying it per call
 * instead of once at startup.</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalInferenceGatewayStrategy implements InterfaceInferenceGatewayStrategy {

    private static final Logger logger =
        LoggerFactory.getLogger(LocalInferenceGatewayStrategy.class);

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private McpToolCallbackRegistry toolCallbackRegistry;

    @Override
    public InferenceTurn complete(final List<ConversationMessage> messages) {
        var tools = this.toolCallbackRegistry.all();
        logger.info("Requesting a local LLM turn with {} tool(s) available", tools.size());

        try {
            var options = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .internalToolExecutionEnabled(false)
                .build();
            var chatResponse = this.chatClientBuilder.build().prompt()
                .messages(toSpringMessages(messages))
                .options(options)
                .call()
                .chatResponse();
            return this.toInferenceTurn(chatResponse);
        } catch (RuntimeException e) {
            logger.warn("Local LLM inference call failed: {}", e.getMessage());
            throw new OrchestratorUnavailableException("Could not reach the local LLM.", e);
        }
    }

    private InferenceTurn toInferenceTurn(final ChatResponse chatResponse) {
        var output = this.outputOf(chatResponse);
        var content = Objects.isNull(output) ? null : output.getText();
        var toolCalls = Objects.isNull(output) ? List.<InferenceToolCall>of() : toToolCalls(output);
        return new InferenceTurn(content, toolCalls, this.tokensSpent(chatResponse));
    }

    private AssistantMessage outputOf(final ChatResponse chatResponse) {
        return Objects.isNull(chatResponse) || Objects.isNull(chatResponse.getResult())
            ? null
            : chatResponse.getResult().getOutput();
    }

    private static List<InferenceToolCall> toToolCalls(final AssistantMessage output) {
        return output.getToolCalls().stream()
            .map(call -> new InferenceToolCall(call.id(), call.name(), call.arguments()))
            .toList();
    }

    private long tokensSpent(final ChatResponse chatResponse) {
        if (Objects.isNull(chatResponse) || Objects.isNull(chatResponse.getMetadata())) {
            return 0L;
        }
        var usage = chatResponse.getMetadata().getUsage();
        if (Objects.isNull(usage) || Objects.isNull(usage.getTotalTokens())) {
            return 0L;
        }
        return usage.getTotalTokens();
    }

    private static List<Message> toSpringMessages(final List<ConversationMessage> messages) {
        return messages.stream().map(LocalInferenceGatewayStrategy::toSpringMessage).toList();
    }

    private static Message toSpringMessage(final ConversationMessage message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> toAssistantMessage(message);
            case TOOL -> toToolResponseMessage(message);
        };
    }

    private static AssistantMessage toAssistantMessage(final ConversationMessage message) {
        var toolCalls = Objects.isNull(message.toolCalls()) ? List.<InferenceToolCall>of()
            : message.toolCalls();
        var springToolCalls = toolCalls.stream()
            .map(call -> new AssistantMessage.ToolCall(
                call.id(), "function", call.name(), call.argumentsJson()))
            .toList();
        return new AssistantMessage(message.content(), Map.of(), springToolCalls);
    }

    private static ToolResponseMessage toToolResponseMessage(final ConversationMessage message) {
        var response = new ToolResponseMessage.ToolResponse(
            message.toolCallId(), message.toolName(), message.content());
        return new ToolResponseMessage(List.of(response));
    }
}
