package org.vader.core.server.service.operators.pythonsandbox;

import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.models.ManagedResource;
import org.vader.core.server.models.PythonSandboxSpec;
import org.vader.core.server.models.SandboxExecutionRequest;
import org.vader.core.server.models.SandboxExecutionResult;
import org.vader.core.server.models.SandboxInfo;

/**
 * Domain layer over {@link PythonSandboxOperator}, shared by the MCP tools and the REST
 * controller so both go through one code path — the same split as {@code WorkflowService} sitting
 * under both {@code ClientPromptController} and the orchestrator.
 *
 * <p>Mirrors {@link PythonSandboxOperator}'s condition (both the master switch and this
 * operator's own flag) since this bean autowires that operator directly.</p>
 */
@Service
@ConditionalOnProperty(
    name = {"vader.operators.enabled", "vader.operators.python-sandbox.enabled"},
    havingValue = "true",
    matchIfMissing = false)
public class PythonSandboxService {

    @Autowired
    private PythonSandboxOperator operator;

    @Autowired
    private SandboxExecutionClient executionClient;

    @Value("${vader.kubernetes.namespace:default}")
    private String namespace;

    /**
     * Creates a sandbox, or returns the existing one if a sandbox of the resolved name is already
     * present.
     *
     * @param requestedName an optional friendly name; a unique name is generated when blank
     * @return the sandbox details
     */
    public SandboxInfo create(final String requestedName) {
        var name = SandboxNaming.resolve(requestedName);
        var managed = this.operator.reconcile(new PythonSandboxSpec(name, null));
        return this.toInfo(managed);
    }

    /**
     * Lists every sandbox this operator manages.
     *
     * @return the sandboxes; empty if none
     */
    public List<SandboxInfo> list() {
        return this.operator.list().stream().map(this::toInfo).toList();
    }

    /**
     * Deletes the named sandbox and its Kubernetes resources.
     *
     * @param name the exact sandbox name
     */
    public void delete(final String name) {
        this.operator.delete(name);
    }

    /**
     * Runs code inside an existing sandbox, transparently to the request being made over the
     * network rather than the Kubernetes API the rest of this class uses.
     *
     * @param name the exact sandbox name
     * @param request the code (and any files) to run
     * @return the run's stdout/stderr/exit code
     */
    public SandboxExecutionResult runCode(
        final String name, final SandboxExecutionRequest request) {
        return this.executionClient.execute(name, request);
    }

    private SandboxInfo toInfo(final ManagedResource resource) {
        var ns = Objects.nonNull(resource.namespace()) ? resource.namespace() : this.namespace;
        return new SandboxInfo(
            resource.name(),
            ns,
            resource.phase(),
            resource.name() + "." + ns + ".svc.cluster.local");
    }
}
