package org.vader.core.server.llm.interfaces;

import org.vader.common.model.vader.entity.LlmRequestKind;

/**
 * Performs one kind of local-LLM call. Only ever invoked from inside {@code LlmRequestInbox}, so
 * at most one call is in flight against the LLM system-wide; callers go through
 * {@code LlmRequestQueue#submit} instead.
 *
 * <p>The request and response type arguments are also the queue's wire format: the request is
 * serialized as {@code Q}, and the response as {@code R}, via Jackson. A connectivity failure
 * should simply propagate -- the inbox reports it to the caller as an unreachable LLM.</p>
 *
 * @param <Q> the request type
 * @param <R> the response type
 */
public interface InterfaceLlmExecutor<Q, R> {

    /**
     * The queue discriminator that routes a message to this executor.
     *
     * @return this executor's kind, unique across all executors
     */
    LlmRequestKind kind();

    /**
     * Performs the call.
     *
     * @param request the decoded request
     * @return the model's response
     */
    R execute(Q request);
}
