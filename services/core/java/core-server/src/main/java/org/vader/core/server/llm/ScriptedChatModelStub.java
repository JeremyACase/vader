package org.vader.core.server.llm;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.vader.core.server.config.VaderMode;

/**
 * A stand-in chat model for the devops test pipeline: every executor, prompt and response parser
 * runs for real, but the model's reply is scripted, so no Ollama is needed and the result is
 * deterministic. Active only with {@code vader.orchestrator.type=scripted}, which is refused at
 * startup outside {@code vader.mode=TEST} -- a real user must never be served a scripted answer.
 *
 * <p>Structured calls are recognized by the JSON schema Spring AI appends to the prompt, i.e. by
 * the output contract the reply has to satisfy. An inference turn is recognized by its
 * caller-executed tools and plays a script of tool calls, one per turn, then gives the final
 * answer: by default a single {@value #SCRIPTED_TOOL_NAME} call so the harness exercises its tool
 * loop. Anything else (synthesis) gets plain text.</p>
 *
 * <p>A prompt containing {@value #UPLOAD_SCRIPT_MARKER} is planned as one task whose script
 * writes {@value #UPLOADED_REPORT_FILENAME} in the sandbox and uploads it with
 * {@code upload_object}, so the Helm system test can check the upload path end to end without
 * giving every other scripted task a sandbox pod.</p>
 */
@Component
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "scripted")
public class ScriptedChatModelStub implements ChatModel {

    /** The final answer to every inference turn that follows a tool result. */
    public static final String INFERENCE_ANSWER =
        "Scripted inference response (vader.orchestrator.type=scripted; no LLM was called).";

    /** The tool every first inference turn asks to call, unless the upload script is playing. */
    public static final String SCRIPTED_TOOL_NAME = "list_queryable_entities";

    /** A client prompt containing this plays the upload script instead of the default one. */
    public static final String UPLOAD_SCRIPT_MARKER = "[scripted:upload-object]";

    /** The file the upload script writes in the sandbox and then uploads. */
    public static final String UPLOADED_REPORT_FILENAME = "scripted-report.md";

    /** The content the upload script writes to {@link #UPLOADED_REPORT_FILENAME}. */
    public static final String UPLOADED_REPORT_CONTENT = "# Scripted report";

    private static final String SYNTHESIS_ANSWER = "Scripted synthesis (no LLM was called).";

    private static final String DECOMPOSITION_REPLY = """
        {
          "reasoning": "Scripted plan (no LLM was called).",
          "objective": "Plan and run a small birthday party for a friend.",
          "tasks": [
            {"title": "Set the date and guest list",
             "description": "Pick a date and invite a handful of close friends.", "dependsOn": []},
            {"title": "Arrange food and cake",
             "description": "Order a cake and decide on snacks for the headcount.",
             "dependsOn": []},
            {"title": "Handle venue and decorations",
             "description": "Prepare the space and buy simple decorations.", "dependsOn": []},
            {"title": "Coordinate the day-of schedule",
             "description": "Confirm timings and assign setup and cleanup helpers.",
             "dependsOn": []}
          ]
        }
        """;

    private static final String UPLOAD_DECOMPOSITION_REPLY = """
        {
          "reasoning": "Scripted plan (no LLM was called).",
          "objective": "Write a short report and upload it to object storage.",
          "tasks": [
            {"title": "Write and upload the report",
             "description": "Write a short Markdown report and upload it to object storage.",
             "dependsOn": []}
          ]
        }
        """;

    private static final String REFINEMENT_REPLY = """
        {"needsRevision": false, "reasoning": "Scripted critique: approved as-is.",
         "missingDependencies": []}
        """;

    private static final String REATTEMPT_REPLY = """
        {"shouldReattempt": true,
         "reasoning": "Scripted reattempt policy: retry while attempts remain."}
        """;

    private static final String EVALUATION_REPLY = """
        {"passed": %s, "reasoning": "Scripted evaluator: trusting the self-reported status.",
         "remainingSubtasks": []}
        """;

    // The evaluator's prompt states the attempt's self-reported status in these words.
    private static final String REPORTED_SUCCESS = "reported SUCCESS";

    // Checked in order; each key is a property only that reply's schema has.
    private static final List<Map.Entry<String, Function<String, String>>> STRUCTURED_REPLIES =
        List.of(
            Map.entry("\"needsRevision\"", contents -> REFINEMENT_REPLY),
            Map.entry("\"shouldReattempt\"", contents -> REATTEMPT_REPLY),
            Map.entry("\"passed\"", contents -> EVALUATION_REPLY.formatted(
                contents.contains(REPORTED_SUCCESS))),
            Map.entry("\"objective\"", contents -> contents.contains(UPLOAD_SCRIPT_MARKER)
                ? UPLOAD_DECOMPOSITION_REPLY
                : DECOMPOSITION_REPLY));

    private static final String TOOL_CALL_TYPE = "function";

    private static final List<AssistantMessage.ToolCall> DEFAULT_SCRIPT = List.of(
        new AssistantMessage.ToolCall(
            "scripted-call-1", TOOL_CALL_TYPE, SCRIPTED_TOOL_NAME, "{}"));

    private static final List<AssistantMessage.ToolCall> UPLOAD_SCRIPT = List.of(
        new AssistantMessage.ToolCall(
            "scripted-call-1", TOOL_CALL_TYPE, "run_python_code",
            "{\"code\": \"open('" + UPLOADED_REPORT_FILENAME + "', 'w').write('"
                + UPLOADED_REPORT_CONTENT + "')\"}"),
        new AssistantMessage.ToolCall(
            "scripted-call-2", TOOL_CALL_TYPE, "upload_object",
            "{\"filename\": \"" + UPLOADED_REPORT_FILENAME + "\"}"));

    private static final int SCRIPTED_TOKENS_PER_DIRECTION = 5;

    private static final String FINISH_REASON = "stop";

    @Value("${vader.mode:PROD}")
    private VaderMode mode;

    /**
     * Fails startup unless running in {@link VaderMode#TEST}.
     *
     * @throws IllegalStateException if scripted mode was selected in any other mode
     */
    @PostConstruct
    void requireTestMode() {
        if (this.mode != VaderMode.TEST) {
            throw new IllegalStateException(
                "vader.orchestrator.type=scripted returns canned results and is only permitted "
                    + "when vader.mode=TEST, but vader.mode is " + this.mode + ". Use "
                    + "vader.orchestrator.type=local.");
        }
    }

    @Override
    public ChatResponse call(final Prompt prompt) {
        var reply = isInferenceTurn(prompt) ? inferenceReply(prompt) : textReply(prompt);
        var generation = new Generation(
            reply, ChatGenerationMetadata.builder().finishReason(FINISH_REASON).build());
        var metadata = ChatResponseMetadata.builder()
            .usage(new DefaultUsage(SCRIPTED_TOKENS_PER_DIRECTION, SCRIPTED_TOKENS_PER_DIRECTION))
            .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    private static boolean isInferenceTurn(final Prompt prompt) {
        return prompt.getOptions() instanceof ToolCallingChatOptions options
            && Boolean.FALSE.equals(options.getInternalToolExecutionEnabled());
    }

    /**
     * Plays the next step of the prompt's script: each tool result already in the conversation
     * marks one step done, and once they all are the turn gives the final answer.
     */
    private static AssistantMessage inferenceReply(final Prompt prompt) {
        var script = prompt.getContents().contains(UPLOAD_SCRIPT_MARKER)
            ? UPLOAD_SCRIPT
            : DEFAULT_SCRIPT;
        var stepsDone = (int) prompt.getInstructions().stream()
            .filter(message -> message.getMessageType() == MessageType.TOOL)
            .count();
        return stepsDone < script.size()
            ? new AssistantMessage("", Map.of(), List.of(script.get(stepsDone)))
            : new AssistantMessage(INFERENCE_ANSWER);
    }

    private static AssistantMessage textReply(final Prompt prompt) {
        var contents = prompt.getContents();
        var text = STRUCTURED_REPLIES.stream()
            .filter(entry -> contents.contains(entry.getKey()))
            .findFirst()
            .map(entry -> entry.getValue().apply(contents))
            .orElse(SYNTHESIS_ANSWER);
        return new AssistantMessage(text);
    }
}
