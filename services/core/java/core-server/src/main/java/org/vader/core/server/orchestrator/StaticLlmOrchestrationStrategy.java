package org.vader.core.server.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.core.server.orchestrator.interfaces.InterfaceLlmOrchestrationStrategy;

/**
 * Returns a fixed, schema-valid task plan without calling any LLM.
 *
 * <p>Active when {@code vader.orchestrator.type} is {@code static}. It exists so the full
 * prompt -&gt; core-server -&gt; decomposition -&gt; persistence path can be exercised
 * deterministically -- notably by the Helm test hook -- without deploying Ollama or waiting on a
 * model download.</p>
 */
@Component
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "static")
public class StaticLlmOrchestrationStrategy implements InterfaceLlmOrchestrationStrategy {

    private static final Logger logger =
        LoggerFactory.getLogger(StaticLlmOrchestrationStrategy.class);

    private static final String STATIC_TASK_PLAN = """
        {
          "objective": "Plan and run a small birthday party for a friend.",
          "taskGraph": {
            "tasks": [
              {
                "title": "Set the date and guest list",
                "description": "Pick a date and invite a handful of close friends."
              },
              {
                "title": "Arrange food and cake",
                "description": "Order a cake and decide on snacks for the headcount."
              },
              {
                "title": "Handle venue and decorations",
                "description": "Prepare the space and buy simple decorations."
              },
              {
                "title": "Coordinate the day-of schedule",
                "description": "Confirm timings and assign setup and cleanup helpers."
              }
            ]
          }
        }
        """;

    @Override
    public String orchestrate(final ClientPrompt clientPrompt) {
        logger.info("Returning the static task plan for prompt: '{}'", clientPrompt.getText());
        return STATIC_TASK_PLAN;
    }
}
