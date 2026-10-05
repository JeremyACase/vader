package org.vader.core.server.workflow;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.llm.LlmRequestInbox;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.interfaces.InterfaceLlmExecutor;
import org.vader.core.server.workflow.model.DeliveredFile;
import org.vader.core.server.workflow.model.WorkflowSynthesisRequest;

/**
 * Asks the chat model to write one coherent final answer from every task's own outcome, via
 * Spring AI's {@link ChatClient}. Called only from inside {@link LlmRequestInbox};
 * {@code WorkflowSynthesisService} submits the request through {@link LlmRequestQueue}.
 *
 * <p>Offers no tools and expects free text, not structured output: the point is a direct answer
 * to the user, not another plan.</p>
 *
 * <p>Builds a fresh {@link ChatClient} per call: one shared instance corrupts its advisor chain
 * under concurrent calls (spring-projects/spring-ai#3537). Building one is cheap -- it wraps the
 * already-configured {@code ChatModel}.</p>
 */
@Service
public class WorkflowSynthesisLlmExecutor
    implements InterfaceLlmExecutor<WorkflowSynthesisRequest, String> {

    private static final String SYNTHESIS_INSTRUCTIONS = """
        You are Vader, wrapping up a multi-step task for the user. Their original request was:
        "%s"

        Your plan's objective was: %s

        Every subtask has now finished. Here is what each one produced:

        %s

        Write a clear, direct final answer to the user's original request, synthesizing what
        every subtask found or produced into one coherent response -- not a list of what each
        subtask did, but the actual answer the tasks were meant to produce together. If any
        subtask failed, use what the successful ones still support and say plainly what could
        not be completed.
        """;

    private static final String FILES_INSTRUCTIONS = """

        The subtasks saved these files for the user:

        %s

        When a file's content is shown above, include it in your answer in full, exactly as
        written, in a fenced code block, so the user can copy it. Do not write download links or
        paths: a list of every file with its download link is added after your answer.
        """;

    private static final String NO_CONTENT_SHOWN = "(content not shown: binary or too large)";

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Override
    public LlmRequestKind kind() {
        return LlmRequestKind.WORKFLOW_SYNTHESIS;
    }

    /**
     * Writes a completed workflow's final answer.
     *
     * @param request the original prompt, the plan's objective, every task's outcome, and the
     *     files the tasks delivered
     * @return the synthesized final answer
     */
    @Override
    public String execute(final WorkflowSynthesisRequest request) {
        var taskSummaries = request.taskOutcomes().stream()
            .map(outcome -> "- " + outcome.title() + " ("
                + (outcome.succeeded() ? "succeeded" : "failed") + "): " + outcome.output())
            .collect(Collectors.joining("\n"));

        var prompt = SYNTHESIS_INSTRUCTIONS.formatted(
            request.promptText(), request.objective(), taskSummaries)
            + filesSection(request.deliveredFiles());

        return this.chatClientBuilder.build().prompt().user(prompt).call().content();
    }

    private static String filesSection(final List<DeliveredFile> files) {
        var listing = files.stream()
            .map(file -> "File " + file.filename() + ":\n"
                + Objects.requireNonNullElse(file.inlineContent(), NO_CONTENT_SHOWN))
            .collect(Collectors.joining("\n\n"));
        return files.isEmpty() ? "" : FILES_INSTRUCTIONS.formatted(listing);
    }
}
