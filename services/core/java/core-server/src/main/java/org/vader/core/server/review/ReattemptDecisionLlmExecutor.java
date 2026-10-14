package org.vader.core.server.review;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.llm.LlmRequestInbox;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.PromptKey;
import org.vader.core.server.llm.PromptTemplateRegistry;
import org.vader.core.server.llm.PromptVariablesBuilder;
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

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private PromptTemplateRegistry promptTemplateRegistry;

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
            .system(this.promptTemplateRegistry.render(PromptKey.REATTEMPT_DECISION_SYSTEM))
            .user(this.userPromptFor(request))
            .call()
            .entity(ReattemptDecision.class);
    }

    private String userPromptFor(final ReattemptDecisionRequest request) {
        var priorUpdates = request.priorUpdateDescriptions().isEmpty()
            ? null
            : String.join("\n", request.priorUpdateDescriptions());
        var variables = new PromptVariablesBuilder()
            .with("taskTitle", request.taskTitle())
            .with("taskDescription", request.taskDescription())
            .with("attemptNumber", request.attemptNumber())
            .with("maxAttempts", request.maxAttempts())
            .with("failureReasoning", request.latestFailureReasoning())
            .with("priorUpdates", priorUpdates)
            .build();
        return this.promptTemplateRegistry.render(PromptKey.REATTEMPT_DECISION_USER, variables);
    }
}
