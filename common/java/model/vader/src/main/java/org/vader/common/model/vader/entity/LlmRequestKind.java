package org.vader.common.model.vader.entity;

/**
 * Which shape of local-LLM call an {@link LlmRequestOutboxMessageEntity} carries -- determines
 * how its {@code requestJson} is decoded and which tool audience is offered.
 */
public enum LlmRequestKind {

    /** One harness turn: {@code requestJson} is a JSON array of {@code ConversationMessage}. */
    INFERENCE_TURN,

    /** One prompt decomposition: {@code requestJson} is the raw client-prompt text. */
    DECOMPOSITION,

    /**
     * One evaluator verdict on a settled attempt: {@code requestJson} is a JSON
     * {@code EvaluationRequest}.
     */
    EVALUATION,

    /**
     * One orchestrator reattempt decision on a failed attempt: {@code requestJson} is a JSON
     * {@code ReattemptDecisionRequest}.
     */
    REATTEMPT_DECISION,

    /**
     * One critique of a freshly-decomposed task plan, before it is ever persisted:
     * {@code requestJson} is a JSON {@code TaskPlanRefinementRequest}.
     */
    TASK_PLAN_REFINEMENT,
}
