package org.vader.core.server.workflow;

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
import org.vader.core.server.workflow.model.DeliveredFile;
import org.vader.core.server.workflow.model.TaskOutcome;
import org.vader.core.server.workflow.model.WorkflowSynthesisRequest;

/**
 * Asks the chat model to write one coherent final answer from every task's own outcome, via
 * Spring AI's {@link ChatClient}. Called only from inside {@link LlmRequestInbox};
 * {@code WorkflowSynthesisService} submits the request through {@link LlmRequestQueue}.
 *
 * <p>Offers no tools and expects free text, not structured output: the point is a direct answer
 * to the user, not another plan.</p>
 *
 * <p>Builds a fresh {@link ChatClient} per call: one shared instance corrupts its advisor chain
 * under concurrent calls (spring-projects/spring-ai#3537). Building one is cheap -- it wraps the
 * already-configured {@code ChatModel}.</p>
 */
@Service
public class WorkflowSynthesisLlmExecutor
    implements InterfaceLlmExecutor<WorkflowSynthesisRequest, String> {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private PromptTemplateRegistry promptTemplateRegistry;

    @Override
    public LlmRequestKind kind() {
        return LlmRequestKind.WORKFLOW_SYNTHESIS;
    }

    /**
     * Writes a completed workflow's final answer.
     *
     * @param request the original prompt, the plan's objective, every task's outcome, and the
     *     files the tasks delivered
     * @return the synthesized final answer
     */
    @Override
    public String execute(final WorkflowSynthesisRequest request) {
        var taskSummaries = request.taskOutcomes().stream()
            .map(this::taskSummary)
            .collect(Collectors.joining("\n"));
        var variables = new PromptVariablesBuilder()
            .with("request", request.promptText())
            .with("objective", request.objective())
            .with("taskSummaries", taskSummaries)
            .with("files", this.fileListing(request.deliveredFiles()))
            .build();
        var prompt = this.promptTemplateRegistry.render(PromptKey.SYNTHESIS, variables);

        return this.chatClientBuilder.build().prompt().user(prompt).call().content();
    }

    private String taskSummary(final TaskOutcome outcome) {
        var variables = new PromptVariablesBuilder()
            .with("title", outcome.title())
            .with("succeeded", outcome.succeeded())
            .with("output", outcome.output())
            .build();
        return this.promptTemplateRegistry.render(PromptKey.SYNTHESIS_TASK, variables);
    }

    private String fileListing(final List<DeliveredFile> files) {
        var listing = files.stream()
            .map(this::fileEntry)
            .collect(Collectors.joining("\n\n"));
        return files.isEmpty() ? null : listing;
    }

    private String fileEntry(final DeliveredFile file) {
        var variables = new PromptVariablesBuilder()
            .with("filename", file.filename())
            .with("content", file.inlineContent())
            .build();
        return this.promptTemplateRegistry.render(PromptKey.SYNTHESIS_FILE, variables);
    }
}
