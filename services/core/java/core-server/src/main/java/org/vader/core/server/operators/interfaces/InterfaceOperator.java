package org.vader.core.server.operators.interfaces;

import java.util.List;
import org.vader.core.server.operators.ManagedResource;

/**
 * Contract for a Kubernetes operator that manages the lifecycle of a set of resources derived from
 * a domain-specific specification.
 *
 * <p>Implementations connect to the API server, treat their own owning {@code Deployment} as the
 * parent of everything they create, and cascade-clean those resources if that owner disappears.
 * The active implementation for a given operator is selected by Spring via
 * {@code @ConditionalOnProperty}, mirroring the {@code Interface*} strategy convention already used
 * for file storage.</p>
 *
 * @param <S> the specification type describing a single desired unit of managed infrastructure
 */
public interface InterfaceOperator<S> {

    /**
     * Connects to the Kubernetes API server, caches the owning Deployment, and begins watching for
     * this operator's own removal.
     */
    void init();

    /**
     * Releases any watches and, if the owning Deployment is already gone, deletes every resource
     * this operator manages.
     */
    void teardown();

    /**
     * Ensures the resources described by {@code spec} exist, creating them only if absent.
     *
     * @param spec the desired unit of managed infrastructure
     * @return the current state of the managed resource
     */
    ManagedResource reconcile(S spec);

    /**
     * Deletes the resources identified by {@code name}, if this operator manages them. A name
     * this operator doesn't manage -- absent, or created by anyone else -- is left alone.
     *
     * @param name the managed resource name
     */
    void delete(String name);

    /**
     * Reports whether this operator manages a resource named {@code name}.
     *
     * @param name the resource name
     * @return true if a resource of that name exists and this operator created it
     */
    boolean manages(String name);

    /**
     * Deletes every resource this operator manages.
     */
    void deleteAll();

    /**
     * Lists every resource this operator currently manages.
     *
     * @return the managed resources; empty if none
     */
    List<ManagedResource> list();
}
