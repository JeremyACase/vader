package org.vader.core.server.orchestration;

import java.util.List;
import java.util.stream.Collectors;
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
import org.vader.core.server.orchestration.model.AttachedFile;
import org.vader.core.server.orchestration.model.DecompositionRequest;
import org.vader.core.server.orchestration.model.LlmTaskPlan;

/**
 * Asks the chat model to decompose one client prompt, via Spring AI's {@link ChatClient}. Called
 * only from inside {@link LlmRequestInbox#handle}; {@code LlmTaskPlanAdapter} submits the request
 * through {@link LlmRequestQueue}.
 *
 * <p>The planner is offered no tools: what it plans around is what the task agents can do, which
 * its prompt states, and a tool list of its own would read as that list.</p>
 *
 * <p>Builds a fresh {@link ChatClient} per call: one shared instance corrupts its advisor chain
 * under concurrent calls (spring-projects/spring-ai#3537). Building one is cheap -- it wraps the
 * already-configured {@code ChatModel}.</p>
 */
@Service
public class DecompositionLlmExecutor
    implements InterfaceLlmExecutor<DecompositionRequest, LlmTaskPlan> {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private PromptTemplateRegistry promptTemplateRegistry;

    @Override
    public LlmRequestKind kind() {
        return LlmRequestKind.DECOMPOSITION;
    }

    /**
     * Decomposes one client prompt.
     *
     * @param request the user's original request text, verbatim, plus its attached files and any
     *     revision guidance
     * @return the model's plan, in the lean shape it was asked to produce
     */
    @Override
    public LlmTaskPlan execute(final DecompositionRequest request) {
        return this.chatClientBuilder.build().prompt()
            .system(this.instructionsFor(request))
            .user(request.clientPromptText())
            .call()
            .entity(LlmTaskPlan.class);
    }

    /**
     * The system instructions for one decomposition: the standing planning instructions, plus
     * the attached files, if any, and -- only on a revision -- why the previous plan was
     * rejected. Both go here, as system-level instructions, rather than into the user message,
     * so the user's request is always passed through exactly as written.
     */
    private String instructionsFor(final DecompositionRequest request) {
        var variables = new PromptVariablesBuilder()
            .with("attachedFiles", attachedFileLines(request.attachedFiles()))
            .with("revisionGuidance", request.revisionGuidance())
            .build();
        return this.promptTemplateRegistry.render(PromptKey.DECOMPOSITION, variables);
    }

    /** Names and types only: the planner never receives a file's contents. */
    private static String attachedFileLines(final List<AttachedFile> attachedFiles) {
        var lines = attachedFiles.stream()
            .map(file -> "- \"" + file.filename() + "\" (type: " + file.contentType() + ")")
            .collect(Collectors.joining("\n"));
        return attachedFiles.isEmpty() ? null : lines;
    }
}
