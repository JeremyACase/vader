package org.vader.core.server.workflow;

import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.llm.LlmRequestInbox;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.interfaces.InterfaceLlmExecutor;
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

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Override
    public LlmRequestKind kind() {
        return LlmRequestKind.WORKFLOW_SYNTHESIS;
    }

    /**
     * Writes a completed workflow's final answer.
     *
     * @param request the original prompt, the plan's objective, and every task's outcome
     * @return the synthesized final answer
     */
    @Override
    public String execute(final WorkflowSynthesisRequest request) {
        var taskSummaries = request.taskOutcomes().stream()
            .map(outcome -> "- " + outcome.title() + " ("
                + (outcome.succeeded() ? "succeeded" : "failed") + "): " + outcome.output())
            .collect(Collectors.joining("\n"));

        var prompt = SYNTHESIS_INSTRUCTIONS.formatted(
            request.promptText(), request.objective(), taskSummaries);

        return this.chatClientBuilder.build().prompt().user(prompt).call().content();
    }
}
