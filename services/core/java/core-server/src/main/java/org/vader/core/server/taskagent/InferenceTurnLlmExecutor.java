package org.vader.core.server.taskagent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.llm.LlmRequestInbox;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.interfaces.InterfaceLlmExecutor;
import org.vader.core.server.mcp.AgentToolAudience;
import org.vader.core.server.mcp.McpToolCallbackRegistry;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.InferenceToolCall;
import org.vader.core.server.taskagent.model.InferenceTurn;

/**
 * Completes one turn against the chat model via Spring AI's {@link ChatClient}. Called only from
 * inside {@link LlmRequestInbox#handle}, i.e. only by whichever replica currently holds the one
 * {@link LlmRequestQueue}-enforced claim system-wide; {@code InferenceGateway} submits the
 * request.
 *
 * <p>Offers every {@link AgentToolAudience#TASK_EXECUTION} tool (via
 * {@link McpToolCallbackRegistry#forAudience}) with Spring AI's internal tool execution off, so a
 * requested tool call comes back in {@link InferenceTurn#toolCalls()} for the harness to invoke
 * and fold into the next turn.</p>
 *
 * <p>Reads content, tool calls and token usage from a single {@code chatResponse()}: calling a
 * second terminal method on the same {@code CallResponseSpec} re-runs its consumed advisor chain
 * and fails with {@code No CallAdvisors available to execute}.</p>
 *
 * <p>Builds a fresh {@link ChatClient} per call: one shared instance corrupts its advisor chain
 * under concurrent calls (spring-projects/spring-ai#3537). Building one is cheap -- it wraps the
 * already-configured {@code ChatModel}.</p>
 */
@Service
public class InferenceTurnLlmExecutor
    implements InterfaceLlmExecutor<List<ConversationMessage>, InferenceTurn> {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private McpToolCallbackRegistry toolCallbackRegistry;

    @Override
    public LlmRequestKind kind() {
        return LlmRequestKind.INFERENCE_TURN;
    }

    /**
     * Completes one turn.
     *
     * @param messages the running conversation so far
     * @return the model's response: either a final answer, or a request to call tools
     */
    @Override
    public InferenceTurn execute(final List<ConversationMessage> messages) {
        var tools = this.toolCallbackRegistry.forAudience(AgentToolAudience.TASK_EXECUTION);
        var options = ToolCallingChatOptions.builder()
            .toolCallbacks(tools)
            .internalToolExecutionEnabled(false)
            .build();
        var chatResponse = this.chatClientBuilder.build().prompt()
            .messages(toSpringMessages(messages))
            .options(options)
            .call()
            .chatResponse();
        return toInferenceTurn(chatResponse);
    }

    private static InferenceTurn toInferenceTurn(final ChatResponse chatResponse) {
        var output = outputOf(chatResponse);
        var content = Objects.isNull(output) ? null : output.getText();
        var toolCalls = Objects.isNull(output) ? List.<InferenceToolCall>of() : toToolCalls(output);
        return new InferenceTurn(
            content, toolCalls, tokensSpent(chatResponse), finishReasonOf(chatResponse));
    }

    private static String finishReasonOf(final ChatResponse chatResponse) {
        return Objects.isNull(chatResponse) || Objects.isNull(chatResponse.getResult())
            ? null
            : chatResponse.getResult().getMetadata().getFinishReason();
    }

    private static AssistantMessage outputOf(final ChatResponse chatResponse) {
        return Objects.isNull(chatResponse) || Objects.isNull(chatResponse.getResult())
            ? null
            : chatResponse.getResult().getOutput();
    }

    private static List<InferenceToolCall> toToolCalls(final AssistantMessage output) {
        return output.getToolCalls().stream()
            .map(call -> new InferenceToolCall(call.id(), call.name(), call.arguments()))
            .toList();
    }

    private static long tokensSpent(final ChatResponse chatResponse) {
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
        return messages.stream().map(InferenceTurnLlmExecutor::toSpringMessage).toList();
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
