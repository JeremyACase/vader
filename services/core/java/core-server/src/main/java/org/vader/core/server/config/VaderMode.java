package org.vader.core.server.config;

/**
 * The deployment's environment classification, bound from {@code vader.mode}.
 *
 * <p>Governs one thing: whether canned results are allowed at all.
 * {@code vader.orchestrator.type=scripted} replaces the LLM with {@code ScriptedChatModelStub},
 * which answers every call with a fixed reply -- e.g. a "plan a small birthday party" task plan
 * regardless of what was actually asked. That is useful for a deterministic devops test pipeline
 * and nothing else, so it is permitted only in {@link #TEST}; core-server refuses to start with it
 * in {@link #DEV} or {@link #PROD}. With a real LLM, an unreachable one fails loudly in every
 * mode -- nothing ever substitutes a canned result.</p>
 *
 * <p>{@link #PROD} is the default, since production safety should never depend on a value
 * someone remembered to set.</p>
 */
public enum VaderMode {
    /** A developer's own local/dev cluster. Real LLM only; never canned results. */
    DEV,
    /** A real deployment. Real LLM only; never canned results. */
    PROD,
    /** A devops test pipeline -- the only mode in which static, canned results are permitted. */
    TEST,
}
