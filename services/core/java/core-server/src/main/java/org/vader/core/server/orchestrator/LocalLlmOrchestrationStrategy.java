package org.vader.core.server.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.orchestrator.interfaces.InterfaceLlmOrchestrationStrategy;

/**
 * Coordinates problem decomposition with a local Ollama instance via Spring AI's
 * {@link ChatClient}.
 *
 * <p>Active only when {@code vader.orchestrator.type} is {@code local}, in which case the Helm
 * chart also installs an Ollama deployment and {@code spring.ai.ollama.*} points this client at
 * it.</p>
 *
 * <p>The client prompt is sent alongside every tool currently registered in the application
 * (every {@code ToolCallbackProvider} bean — today the MCP operator tools), so the model may
 * call them while it plans. Structured output is handled by {@code ChatClient.entity(...)}
 * against the lean {@link LlmTaskPlan} shape (objective + tasks only); this strategy then builds
 * a full {@link TaskPlan} from it and re-serializes to JSON to satisfy the
 * {@link InterfaceLlmOrchestrationStrategy} contract.</p>
 *
 * <p>When the LLM is unreachable and {@code vader.orchestrator.local.fallback-to-static} is
 * {@code true} (the default), this returns the canned {@link StaticTaskPlan} instead of failing,
 * so {@code helm test} and CI pass with no Ollama in the cluster. A reachable LLM that returns
 * an unusable response still fails with {@link OrchestratorResponseException}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalLlmOrchestrationStrategy implements InterfaceLlmOrchestrationStrategy {

    private static final Logger logger =
        LoggerFactory.getLogger(LocalLlmOrchestrationStrategy.class);

    private static final String DECOMPOSITION_INSTRUCTIONS = """
        You are Vader, a planning assistant. Decompose the user's problem into a concrete plan
        of 2 to 6 top-level tasks, each with a short title and a description of what to do.

        You have been given a set of tools. Call a tool only when doing so materially helps you
        plan or gather information the plan needs; otherwise just plan. Do not call tools
        speculatively.
        """;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private ObjectProvider<ToolCallbackProvider> toolCallbackProviders;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${vader.orchestrator.local.fallback-to-static:true}")
    private boolean fallbackToStatic;

    private ChatClient chatClient;
    private List<ToolCallback> toolCallbacks;

    /**
     * Builds the chat client and flattens the registered tool providers into a single tool set,
     * once dependencies are injected.
     */
    @PostConstruct
    void wire() {
        this.chatClient = this.chatClientBuilder.build();
        this.toolCallbacks = this.toolCallbackProviders.stream()
            .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
            .toList();
    }

    @Override
    public String orchestrate(final ClientPrompt clientPrompt) {
        logger.info("Requesting a decomposition from the local LLM with {} tool(s) available",
            this.toolCallbacks.size());

        try {
            var llmPlan = this.chatClient.prompt()
                .system(DECOMPOSITION_INSTRUCTIONS)
                .user(clientPrompt.getText())
                .toolCallbacks(this.toolCallbacks)
                .call()
                .entity(LlmTaskPlan.class);

            if (Objects.isNull(llmPlan)
                || Objects.isNull(llmPlan.objective())
                || Objects.isNull(llmPlan.tasks())
                || llmPlan.tasks().isEmpty()) {
                throw new OrchestratorResponseException(
                    "The local LLM did not return a usable task plan.");
            }
            return this.objectMapper.writeValueAsString(toTaskPlan(llmPlan));
        } catch (ResourceAccessException | TransientAiException e) {
            if (this.fallbackToStatic) {
                logger.warn("Local LLM unreachable ({}); returning the static fallback plan.",
                    e.getMessage());
                return StaticTaskPlan.JSON;
            }
            throw new OrchestratorUnavailableException("Could not reach the local LLM.", e);
        } catch (OrchestratorResponseException e) {
            throw e;
        } catch (JsonProcessingException | RuntimeException e) {
            throw new OrchestratorResponseException(
                "The local LLM's response was not a usable task plan: " + e.getMessage(), e);
        }
    }

    private static TaskPlan toTaskPlan(final LlmTaskPlan llmPlan) {
        var taskGraph = new TaskGraph();
        taskGraph.setTasks(llmPlan.tasks().stream().map(source -> {
            var task = new Task();
            task.setTitle(source.title());
            task.setDescription(source.description());
            return task;
        }).toList());

        var taskPlan = new TaskPlan();
        taskPlan.setObjective(llmPlan.objective());
        taskPlan.setTaskGraph(taskGraph);
        return taskPlan;
    }
}
