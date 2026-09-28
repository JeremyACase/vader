package org.vader.core.server.service.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.vader.core.server.models.DecompositionRequest;
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

        Give every task a short, unique title. List tasks in the order they would naturally
        happen. Most plans have real dependencies — a task that needs another task's output
        cannot run in parallel with it. Use `dependsOn` to say so: it holds the exact titles of
        the tasks listed earlier that must finish first. Only name earlier tasks (a task can never
        depend on itself or on something listed after it); leave it empty for a task that can
        start immediately. Do not default every task to an empty list just because it is easier —
        for each task, ask what it needs from the others.

        For example, "research competitors, then write a positioning doc, then get it reviewed":
          - "Research competitors" with `dependsOn: []`
          - "Write positioning doc" with `dependsOn: ["Research competitors"]`
          - "Review positioning doc" with `dependsOn: ["Write positioning doc"]`
        Each waits only on the task it actually needs, not on every prior task. A plan whose tasks
        are all independent (e.g. "fetch three unrelated reports") correctly leaves every
        `dependsOn` empty.

        You have been given a set of tools. Call a tool only when doing so materially helps you
        plan or gather information the plan needs; otherwise just plan. Do not call tools
        speculatively.
        """;

    private static final String REVISION_INSTRUCTIONS = """

        A reviewer rejected your previous plan for this same request, for this reason:
        %s

        Produce a new plan that fixes that problem. Your `reasoning` must explain the new plan on
        its own terms -- do not quote, restate, or respond to this feedback, and do not mention
        any previous plan or its tasks. The user will read your reasoning and has never seen the
        previous plan.
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
     * outcome rather than left to propagate -- {@code LocalLlmOrchestrationStrategy} turns it into
     * a loud {@code OrchestratorUnavailableException}, not a bug in this request's processing. Any
     * other exception still propagates, settling the underlying queue message {@code FAILED}.</p>
     *
     * @param request the user's original request text, verbatim, plus any revision guidance
     * @return the model's plan, in the lean shape it was asked to produce, or an unreachable
     *     outcome
     */
    public DecompositionOutcome execute(final DecompositionRequest request) {
        var toolCallbacks = this.toolCallbackRegistry.forAudience(AgentToolAudience.ORCHESTRATION);
        try {
            var plan = this.chatClientBuilder.build().prompt()
                .system(instructionsFor(request.revisionGuidance()))
                .user(request.clientPromptText())
                .toolCallbacks(toolCallbacks)
                .call()
                .entity(LlmTaskPlan.class);
            return new DecompositionOutcome(plan, null);
        } catch (ResourceAccessException | TransientAiException e) {
            return new DecompositionOutcome(null, e.getMessage());
        }
    }

    /**
     * The system instructions for one decomposition: the standing planning instructions, plus --
     * only on a revision -- why the previous plan was rejected. The guidance goes here, as a
     * system-level instruction, rather than into the user message, so the user's request is
     * always passed through exactly as written.
     */
    private static String instructionsFor(final String revisionGuidance) {
        return revisionGuidance == null
            ? DECOMPOSITION_INSTRUCTIONS
            : DECOMPOSITION_INSTRUCTIONS + REVISION_INSTRUCTIONS.formatted(revisionGuidance);
    }
}
