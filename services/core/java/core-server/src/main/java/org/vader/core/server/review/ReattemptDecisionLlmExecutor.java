package org.vader.core.server.review;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.llm.LlmRequestInbox;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.interfaces.InterfaceLlmExecutor;
import org.vader.core.server.review.model.ReattemptDecision;
import org.vader.core.server.review.model.ReattemptDecisionRequest;

/**
 * Asks the chat model whether a failed task is worth re-attempting, via Spring AI's
 * {@link ChatClient}. Called only from inside {@link LlmRequestInbox#handle}; callers submit the
 * request through {@link LlmRequestQueue}.
 *
 * <p>Builds a fresh {@link ChatClient} per call: one shared instance corrupts its advisor chain
 * under concurrent calls (spring-projects/spring-ai#3537). Building one is cheap -- it wraps the
 * already-configured {@code ChatModel}.</p>
 */
@Service
public class ReattemptDecisionLlmExecutor
    implements InterfaceLlmExecutor<ReattemptDecisionRequest, ReattemptDecision> {

    private static final String REATTEMPT_DECISION_INSTRUCTIONS = """
        You are Vader, deciding whether a failed task is worth re-attempting. An independent
        evaluation has already judged the task's latest attempt a failure; your job is not to
        re-litigate that verdict, but to decide whether trying again is likely to help, given the
        task, why it failed, and what has already been tried.

        Retrying makes sense when the failure looks transient or addressable (a tool call that
        can be retried differently, a misunderstanding that clearer instructions would fix, an
        approach that has not yet been tried). It does not make sense when the task is
        fundamentally unachievable as stated, when updates from earlier attempts show the same
        failure repeating despite different approaches, or when nothing about a fresh attempt
        would plausibly change the outcome. On a first attempt there are no earlier attempts, so
        there is no repeating pattern to point to -- judge the one failure on its own merits.

        Set `shouldReattempt` to your decision and explain your reasoning in `reasoning` -- this
        is recorded as the durable audit trail for this task, so be specific about what makes you
        think another attempt would (or would not) fare differently.
        """;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Override
    public LlmRequestKind kind() {
        return LlmRequestKind.REATTEMPT_DECISION;
    }

    /**
     * Decides whether a failed task is worth re-attempting.
     *
     * @param request the failed task's context
     * @return the reattempt decision
     */
    @Override
    public ReattemptDecision execute(final ReattemptDecisionRequest request) {
        return this.chatClientBuilder.build().prompt()
            .system(REATTEMPT_DECISION_INSTRUCTIONS)
            .user(this.userPromptFor(request))
            .call()
            .entity(ReattemptDecision.class);
    }

    private String userPromptFor(final ReattemptDecisionRequest request) {
        var priorUpdates = request.priorUpdateDescriptions().isEmpty()
            ? "(none -- no earlier attempt of this task has been made)"
            : String.join("\n", request.priorUpdateDescriptions());

        return """
            Task: %s

            Description: %s

            This was attempt %d of a maximum of %d.

            Why the latest attempt failed:
            %s

            Updates from earlier attempts of this task:
            %s
            """.formatted(
                request.taskTitle(), request.taskDescription(), request.attemptNumber(),
                request.maxAttempts(), request.latestFailureReasoning(), priorUpdates);
    }
}
