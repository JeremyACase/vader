package org.vader.core.server.service.agent.orchestrator.strategies;

/**
 * The canned, schema-valid task plan used both by {@link StaticLlmOrchestrationStrategy} and as
 * the fallback {@link LocalLlmOrchestrationStrategy} returns when the local LLM is unreachable.
 *
 * <p>Kept as a shared constant because the two strategies have opposite
 * {@code @ConditionalOnProperty} values and so are never beans at the same time.</p>
 */
public final class StaticTaskPlan {

    /**
     * A fixed decomposition ("plan a small birthday party") that satisfies the {@code TaskPlan}
     * schema without calling any LLM.
     */
    public static final String JSON = """
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

    private StaticTaskPlan() {
    }
}
