package org.vader.core.server.review;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.llm.LlmRequestInbox;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.PromptKey;
import org.vader.core.server.llm.PromptTemplateRegistry;
import org.vader.core.server.llm.PromptVariablesBuilder;
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

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private PromptTemplateRegistry promptTemplateRegistry;

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
            .system(this.promptTemplateRegistry.render(PromptKey.EVALUATION_SYSTEM))
            .user(this.userPromptFor(request))
            .call()
            .entity(EvaluationVerdict.class);
    }

    private static String outlineOf(final List<PlanStep> planSteps) {
        return planSteps.stream()
            .map(step -> step.outlineLine("the task under review"))
            .collect(Collectors.joining("\n"));
    }

    private static String uploadedFileList(final List<String> filenames) {
        return filenames.isEmpty()
            ? null
            : filenames.stream()
                .map(name -> "- \"" + name + "\"")
                .collect(Collectors.joining("\n"));
    }

    private String userPromptFor(final EvaluationRequest request) {
        var variables = new PromptVariablesBuilder()
            .with("userRequest", request.userRequest())
            .with("planObjective", request.planObjective())
            .with("plan", outlineOf(request.planSteps()))
            .with("taskTitle", request.taskTitle())
            .with("taskDescription", request.taskDescription())
            .with("succeeded", request.attemptStatus() == TaskAttemptStatus.SUCCEEDED)
            .with("result", request.attemptResult())
            .with("failureReason", request.attemptFailureReason())
            .with("toolEvidence", request.lastToolCallEvidence())
            .with("uploadedFiles", uploadedFileList(request.uploadedFilenames()))
            .build();
        return this.promptTemplateRegistry.render(PromptKey.EVALUATION_USER, variables);
    }
}
