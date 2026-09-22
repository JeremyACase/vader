package org.vader.core.server.service.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.vader.core.server.service.agent.orchestrator.strategies.LlmTaskPlan;
import org.vader.core.server.service.registries.AgentToolAudience;
import org.vader.core.server.service.registries.McpToolCallbackRegistry;

/**
 * Actually asks the in-cluster Ollama instance to decompose one client prompt, via Spring AI's
 * {@link ChatClient}. Called only from inside {@link LlmRequestInbox#handle} -- never directly by
 * {@code LocalLlmOrchestrationStrategy}, which only enqueues and waits.
 *
 * <p>Every tool tagged {@link AgentToolAudience#ORCHESTRATION} (via
 * {@link McpToolCallbackRegistry#forAudience}) is offered to the model, so it may call them while
 * it plans -- this is the "higher-level agent" role: it never gets sandbox code execution, only
 * tools for reasoning about system state.</p>
 *
 * <p>A fresh {@link ChatClient} is built per call rather than cached on the bean: concurrent
 * {@code .call()} invocations against one shared instance corrupt Spring AI's internal
 * advisor-chain state (spring-projects/spring-ai#3537, still open as of 1.0.9) and intermittently
 * fail with {@code IllegalStateException: No CallAdvisors available to execute}. Building from
 * {@link ChatClient.Builder} is cheap -- it wraps an already-configured {@code ChatModel}, no new
 * network connection -- so there is no real cost to paying it per call.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class DecompositionLlmExecutor {

    private static final String DECOMPOSITION_INSTRUCTIONS = """
        You are Vader, a planning assistant. Your job is to decompose the user's problem into a
        concrete plan of 2 to 6 top-level tasks.

        Before writing the plan, reason step by step in the `reasoning` field: restate the goal
        in your own words, identify any constraints or unknowns, decide whether any of your
        available tools would materially help, and sketch your overall approach. Write this
        reasoning before filling in `objective` and `tasks` — it will be shown to the user.

        List tasks in the order they would naturally happen. Most plans have real dependencies —
        a task that needs another task's output cannot run in parallel with it. Use
        `dependsOnIndices` to say so: it holds the 0-based positions, in this same tasks array, of
        every task that must finish first. Only reference earlier positions (a task can never
        depend on itself or on something listed after it); leave it empty for a task that can
        start immediately. Do not default every task to an empty list just because it is easier —
        think about which tasks actually need another one's result.

        For example, "research competitors, then write a positioning doc, then get it reviewed"
        is three tasks where task 1 (write) has `dependsOnIndices: [0]` and task 2 (review) has
        `dependsOnIndices: [1]` — each waits only on the one task immediately before it, not on
        every prior task. A plan whose tasks are all independent (e.g. "fetch three unrelated
        reports") correctly leaves every `dependsOnIndices` empty.

        You have been given a set of tools. Call a tool only when doing so materially helps you
        plan or gather information the plan needs; otherwise just plan. Do not call tools
        speculatively.
        """;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private McpToolCallbackRegistry toolCallbackRegistry;

    /**
     * Decomposes one client prompt.
     *
     * <p>A connectivity failure ({@link ResourceAccessException} or {@link TransientAiException})
     * is caught here and returned as an {@link DecompositionOutcome#isUnreachable() unreachable}
     * outcome rather than left to propagate -- it is an everyday condition
     * {@code LocalLlmOrchestrationStrategy} has its own fallback policy for, not a bug in this
     * request's processing. Any other exception still propagates, settling the underlying queue
     * message {@code FAILED}.</p>
     *
     * @param clientPromptText the original client-submitted request text
     * @return the model's plan, in the lean shape it was asked to produce, or an unreachable
     *     outcome
     */
    public DecompositionOutcome execute(final String clientPromptText) {
        var toolCallbacks = this.toolCallbackRegistry.forAudience(AgentToolAudience.ORCHESTRATION);
        try {
            var plan = this.chatClientBuilder.build().prompt()
                .system(DECOMPOSITION_INSTRUCTIONS)
                .user(clientPromptText)
                .toolCallbacks(toolCallbacks)
                .call()
                .entity(LlmTaskPlan.class);
            return new DecompositionOutcome(plan, null);
        } catch (ResourceAccessException | TransientAiException e) {
            return new DecompositionOutcome(null, e.getMessage());
        }
    }
}
