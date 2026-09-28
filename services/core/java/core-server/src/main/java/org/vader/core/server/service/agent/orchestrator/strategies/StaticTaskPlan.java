package org.vader.core.server.service.agent.orchestrator.strategies;

/**
 * The canned, schema-valid task plan {@link StaticLlmOrchestrationStrategy} returns for every
 * prompt, regardless of what was asked.
 *
 * <p>For the devops test pipeline only: static mode is refused outside {@code vader.mode=TEST}
 * (see {@code StaticStrategyModeGuard}), and nothing else ever returns this plan -- in particular,
 * {@link LocalLlmOrchestrationStrategy} fails loudly rather than falling back to it.</p>
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
