package org.vader.core.server.service.strategies.synthesis;

import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.models.WorkflowSynthesisRequest;
import org.vader.core.server.service.strategies.synthesis.interfaces.InterfaceWorkflowSynthesisStrategy;

/**
 * Asks the local Ollama instance to write one coherent final answer from every task's own
 * outcome, via Spring AI's {@link ChatClient}. Active when {@code vader.orchestrator.type} is
 * {@code local}.
 *
 * <p>Unlike the decomposition orchestrator, this issues no tool calls and expects free-text, not
 * structured output -- the point is a direct answer to the user, not another plan. A fresh
 * {@link ChatClient} is built for every call rather than cached on the bean, for the same reason
 * {@code LocalInferenceGatewayStrategy} does: concurrent {@code .call()} invocations against one
 * shared instance can corrupt Spring AI's internal advisor-chain state
 * (spring-projects/spring-ai#3537, still open as of 1.0.9).</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalWorkflowSynthesisStrategy implements InterfaceWorkflowSynthesisStrategy {

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
    public String synthesize(final WorkflowSynthesisRequest request) {
        var taskSummaries = request.taskOutcomes().stream()
            .map(outcome -> "- " + outcome.title() + " ("
                + (outcome.succeeded() ? "succeeded" : "failed") + "): " + outcome.output())
            .collect(Collectors.joining("\n"));

        var prompt = SYNTHESIS_INSTRUCTIONS.formatted(
            request.promptText(), request.objective(), taskSummaries);

        return this.chatClientBuilder.build().prompt().user(prompt).call().content();
    }
}
