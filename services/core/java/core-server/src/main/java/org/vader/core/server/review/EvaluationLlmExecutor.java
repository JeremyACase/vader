package org.vader.core.server.review;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.llm.LlmRequestInbox;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.interfaces.InterfaceLlmExecutor;
import org.vader.core.server.review.model.EvaluationRequest;
import org.vader.core.server.review.model.EvaluationVerdict;
import org.vader.core.server.workflow.model.PlanStep;

/**
 * Asks the chat model to independently judge one settled attempt, via Spring AI's
 * {@link ChatClient}. Called only from inside {@link LlmRequestInbox#handle}; callers submit the
 * request through {@link LlmRequestQueue}.
 *
 * <p>Offered no tools at all: judging whether a reported result is actually correct is a
 * reasoning task over the task description and the attempt's own reported output, not something
 * that benefits from calling back out to the system it is judging.</p>
 *
 * <p>Builds a fresh {@link ChatClient} per call: one shared instance corrupts its advisor chain
 * under concurrent calls (spring-projects/spring-ai#3537). Building one is cheap -- it wraps the
 * already-configured {@code ChatModel}.</p>
 */
@Service
public class EvaluationLlmExecutor
    implements InterfaceLlmExecutor<EvaluationRequest, EvaluationVerdict> {

    private static final String EVALUATION_INSTRUCTIONS = """
        You are Vader, independently reviewing whether a completed task actually succeeded. A
        task-execution agent has reported its own outcome for the task below; your job is to
        judge that claim on its merits, not to simply trust it. Agents sometimes report success
        for work that is incomplete, off-target, or does not actually satisfy the task -- and
        sometimes report failure for work that is actually fine. Read the task's title and
        description, what the agent reported (its result if it claimed success, or its failure
        reason if it claimed failure), and any prior updates already recorded against this task,
        then decide for yourself: did this attempt actually accomplish the task?

        The task is one step of a plan for the user's request. The request and the whole plan
        are shown below, with the task under review marked. Judge the attempt only on this task's
        own share of the request: work that another task in the plan covers is not missing from
        this one, even when the request mentions it. If the user asked whether something can be
        done, establishing that it can is enough, and doing it is out of scope. Never list
        remaining work that another task already covers, or that the user did not ask for.

        Set `passed` to true only if the reported result genuinely satisfies the task. A result
        that describes work instead of reporting it has NOT passed: code the agent shows but never
        ran, a plan for what it will do next ("let's proceed", "next I will"), or a confident
        answer that rests on a tool call that failed. Judge a failed tool call by what it was
        for. If it was doing this task's own work, that work is not done. If it was attempting
        something outside this task's share -- work another task owns, or that the user did not
        ask for -- ignore it, and judge whether the result reports this task's own work, backed by
        calls that succeeded. Never list fixing out-of-scope work as remaining work. Explain your
        reasoning in
        `reasoning` -- this is recorded as the durable audit trail for this task, so be specific
        about what you checked and why you reached your conclusion.

        When `passed` is false but the agent made real progress toward the task, list the work
        that is still left in `remainingSubtasks`, in the order it must be done: at most three
        small, concrete steps, each with a short `title` and a `description` that a different
        agent -- one that never saw this attempt -- could carry out on its own. Mention the
        specific error to fix or the specific code to run when there is one. Leave
        `remainingSubtasks` empty when the attempt passed, or when it made no usable progress and
        should simply be tried again from scratch.
        """;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Override
    public LlmRequestKind kind() {
        return LlmRequestKind.EVALUATION;
    }

    /**
     * Independently evaluates one settled attempt.
     *
     * @param request the task and attempt outcome to judge
     * @return the evaluator's verdict
     */
    @Override
    public EvaluationVerdict execute(final EvaluationRequest request) {
        return this.chatClientBuilder.build().prompt()
            .system(EVALUATION_INSTRUCTIONS)
            .user(this.userPromptFor(request))
            .call()
            .entity(EvaluationVerdict.class);
    }

    private static String outlineOf(final List<PlanStep> planSteps) {
        return planSteps.stream()
            .map(step -> step.outlineLine("the task under review"))
            .collect(Collectors.joining("\n"));
    }

    private String userPromptFor(final EvaluationRequest request) {
        var reported = request.attemptStatus() == TaskAttemptStatus.SUCCEEDED
            ? "The agent reported SUCCESS with this result:\n" + request.attemptResult()
            : "The agent reported FAILURE with this reason:\n" + request.attemptFailureReason();
        var priorUpdates = request.priorUpdateDescriptions().isEmpty()
            ? "(none)"
            : String.join("\n", request.priorUpdateDescriptions());
        var toolEvidence = Objects.requireNonNullElse(
            request.lastToolCallEvidence(), "The agent made no tool calls.");

        return """
            The user's request: %s

            The plan for that request, in the order its tasks run (objective: %s):
            %s

            Task under review: %s

            Description: %s

            %s

            Evidence from the agent's tool-call log (recorded by the system, not by the agent):
            %s

            Prior updates already recorded against this task:
            %s
            """.formatted(
                request.userRequest(), request.planObjective(), outlineOf(request.planSteps()),
                request.taskTitle(), request.taskDescription(), reported, toolEvidence,
                priorUpdates);
    }
}
