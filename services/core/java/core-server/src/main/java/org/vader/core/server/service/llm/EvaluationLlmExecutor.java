package org.vader.core.server.service.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.models.EvaluationRequest;
import org.vader.core.server.models.EvaluationVerdict;

/**
 * Actually asks the in-cluster Ollama instance to independently judge one settled attempt, via
 * Spring AI's {@link ChatClient}. Called only from inside {@link LlmRequestInbox#handle} -- never
 * directly by an evaluator strategy, which only enqueues and waits.
 *
 * <p>Offered no tools at all: judging whether a reported result is actually correct is a
 * reasoning task over the task description and the attempt's own reported output, not something
 * that benefits from calling back out to the system it is judging.</p>
 *
 * <p>A fresh {@link ChatClient} is built per call rather than cached on the bean, for the same
 * reason {@code DecompositionLlmExecutor} does (spring-projects/spring-ai#3537).</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class EvaluationLlmExecutor {

    private static final String EVALUATION_INSTRUCTIONS = """
        You are Vader, independently reviewing whether a completed task actually succeeded. A
        task-execution agent has reported its own outcome for the task below; your job is to
        judge that claim on its merits, not to simply trust it. Agents sometimes report success
        for work that is incomplete, off-target, or does not actually satisfy the task -- and
        sometimes report failure for work that is actually fine. Read the task's title and
        description, what the agent reported (its result if it claimed success, or its failure
        reason if it claimed failure), and any prior updates already recorded against this task,
        then decide for yourself: did this attempt actually accomplish the task?

        Set `passed` to true only if the reported result genuinely satisfies the task. Explain
        your reasoning in `reasoning` -- this is recorded as the durable audit trail for this
        task, so be specific about what you checked and why you reached your conclusion.
        """;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    /**
     * Independently evaluates one settled attempt.
     *
     * <p>A connectivity failure ({@link ResourceAccessException} or {@link TransientAiException})
     * is caught here and returned as an {@link EvaluationOutcome#isUnreachable() unreachable}
     * outcome rather than left to propagate, for the same reason
     * {@code DecompositionLlmExecutor#execute} does. Any other exception still propagates,
     * settling the underlying queue message {@code FAILED}.</p>
     *
     * @param request the task and attempt outcome to judge
     * @return the evaluator's verdict, or an unreachable outcome
     */
    public EvaluationOutcome execute(final EvaluationRequest request) {
        try {
            var verdict = this.chatClientBuilder.build().prompt()
                .system(EVALUATION_INSTRUCTIONS)
                .user(this.userPromptFor(request))
                .call()
                .entity(EvaluationVerdict.class);
            return new EvaluationOutcome(verdict, null);
        } catch (ResourceAccessException | TransientAiException e) {
            return new EvaluationOutcome(null, e.getMessage());
        }
    }

    private String userPromptFor(final EvaluationRequest request) {
        var reported = request.attemptStatus() == TaskAttemptStatus.SUCCEEDED
            ? "The agent reported SUCCESS with this result:\n" + request.attemptResult()
            : "The agent reported FAILURE with this reason:\n" + request.attemptFailureReason();
        var priorUpdates = request.priorUpdateDescriptions().isEmpty()
            ? "(none)"
            : String.join("\n", request.priorUpdateDescriptions());

        return """
            Task: %s

            Description: %s

            %s

            Prior updates already recorded against this task:
            %s
            """.formatted(
                request.taskTitle(), request.taskDescription(), reported, priorUpdates);
    }
}
