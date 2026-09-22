package org.vader.core.server.service.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.vader.core.server.models.ReattemptDecision;
import org.vader.core.server.models.ReattemptDecisionRequest;

/**
 * Actually asks the in-cluster Ollama instance whether a failed task is worth re-attempting, via
 * Spring AI's {@link ChatClient}. Called only from inside {@link LlmRequestInbox#handle} -- never
 * directly by {@code LocalReattemptDecisionStrategy}, which only enqueues and waits.
 *
 * <p>A fresh {@link ChatClient} is built per call rather than cached on the bean, for the same
 * reason {@code DecompositionLlmExecutor} does (spring-projects/spring-ai#3537).</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class ReattemptDecisionLlmExecutor {

    private static final String REATTEMPT_DECISION_INSTRUCTIONS = """
        You are Vader, deciding whether a failed task is worth re-attempting. An independent
        evaluation has already judged the task's latest attempt a failure; your job is not to
        re-litigate that verdict, but to decide whether trying again is likely to help, given the
        task, why it failed, and what has already been tried.

        Retrying makes sense when the failure looks transient or addressable (a tool call that
        can be retried differently, a misunderstanding that clearer instructions would fix, an
        approach that has not yet been tried). It does not make sense when the task is
        fundamentally unachievable as stated, when prior updates show the same failure repeating
        despite different approaches, or when nothing about a fresh attempt would plausibly change
        the outcome.

        Set `shouldReattempt` to your decision and explain your reasoning in `reasoning` -- this
        is recorded as the durable audit trail for this task, so be specific about what makes you
        think another attempt would (or would not) fare differently.
        """;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    /**
     * Decides whether a failed task is worth re-attempting.
     *
     * <p>A connectivity failure ({@link ResourceAccessException} or {@link TransientAiException})
     * is caught here and returned as a {@link ReattemptDecisionOutcome#isUnreachable() unreachable}
     * outcome rather than left to propagate, for the same reason
     * {@code DecompositionLlmExecutor#execute} does. Any other exception still propagates,
     * settling the underlying queue message {@code FAILED}.</p>
     *
     * @param request the failed task's context
     * @return the reattempt decision, or an unreachable outcome
     */
    public ReattemptDecisionOutcome execute(final ReattemptDecisionRequest request) {
        try {
            var decision = this.chatClientBuilder.build().prompt()
                .system(REATTEMPT_DECISION_INSTRUCTIONS)
                .user(this.userPromptFor(request))
                .call()
                .entity(ReattemptDecision.class);
            return new ReattemptDecisionOutcome(decision, null);
        } catch (ResourceAccessException | TransientAiException e) {
            return new ReattemptDecisionOutcome(null, e.getMessage());
        }
    }

    private String userPromptFor(final ReattemptDecisionRequest request) {
        var priorUpdates = request.priorUpdateDescriptions().isEmpty()
            ? "(none)"
            : String.join("\n", request.priorUpdateDescriptions());

        return """
            Task: %s

            Description: %s

            This was attempt %d of a maximum of %d.

            Why the latest attempt failed:
            %s

            Prior updates already recorded against this task:
            %s
            """.formatted(
                request.taskTitle(), request.taskDescription(), request.attemptNumber(),
                request.maxAttempts(), request.latestFailureReasoning(), priorUpdates);
    }
}
