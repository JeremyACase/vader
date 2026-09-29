package org.vader.core.server.llm;

import java.lang.reflect.Type;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.llm.interfaces.InterfaceLlmExecutor;

/**
 * Looks up the {@link InterfaceLlmExecutor} for a queued LLM call, by its class on the enqueuing
 * side and by its {@link LlmRequestKind} on the processing side.
 *
 * <p>Executors are resolved on each lookup rather than injected up front: they depend on the MCP
 * tool graph, which leads back to {@code LlmRequestInbox}, so eager injection would form a
 * cycle.</p>
 */
@Service
public class LlmExecutorRegistry {

    @Autowired
    private ObjectProvider<InterfaceLlmExecutor<?, ?>> executors;

    /**
     * Finds the executor of the given class.
     *
     * @param type the executor class
     * @param <E> the executor type
     * @return the executor bean
     * @throws LlmRequestQueueException if no such executor is registered
     */
    public <E extends InterfaceLlmExecutor<?, ?>> E forType(final Class<E> type) {
        return this.executors.orderedStream()
            .filter(type::isInstance)
            .map(type::cast)
            .findFirst()
            .orElseThrow(() -> new LlmRequestQueueException(
                "No LLM executor registered of type " + type.getSimpleName()));
    }

    /**
     * Finds the executor that handles the given kind.
     *
     * @param kind the queued message's kind
     * @return the executor bean
     * @throws LlmRequestQueueException if no executor handles that kind
     */
    public InterfaceLlmExecutor<?, ?> forKind(final LlmRequestKind kind) {
        return this.executors.orderedStream()
            .filter(executor -> executor.kind() == kind)
            .findFirst()
            .orElseThrow(() -> new LlmRequestQueueException(
                "No LLM executor registered for kind " + kind));
    }

    /**
     * The executor's request type argument, including any generics (e.g. a {@code List} of
     * messages), for decoding a queued request.
     *
     * @param executor the executor
     * @return its {@code Q} type
     */
    public static Type requestTypeOf(final InterfaceLlmExecutor<?, ?> executor) {
        return typeArgumentOf(executor, 0);
    }

    /**
     * The executor's response type argument, for decoding a queued response.
     *
     * @param executor the executor
     * @return its {@code R} type
     */
    public static Type responseTypeOf(final InterfaceLlmExecutor<?, ?> executor) {
        return typeArgumentOf(executor, 1);
    }

    private static Type typeArgumentOf(final InterfaceLlmExecutor<?, ?> executor, final int index) {
        return ResolvableType.forClass(executor.getClass())
            .as(InterfaceLlmExecutor.class)
            .getGeneric(index)
            .getType();
    }
}
