package org.vader.core.server.service.operators.pythonsandbox;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.vader.core.server.exceptions.SandboxExecutionException;
import org.vader.core.server.models.ManagedResource;
import org.vader.core.server.models.PythonSandboxSpec;
import org.vader.core.server.models.SandboxExecutionRequest;
import org.vader.core.server.models.SandboxExecutionResult;
import org.vader.core.server.models.SandboxInfo;
import org.vader.core.server.models.StagedObjectInfo;
import org.vader.core.server.service.storage.ObjectStorageService;

/**
 * Domain layer over {@link PythonSandboxOperator}, shared by the MCP tools and the REST
 * controller so both go through one code path — the same split as
 * {@code OrchestratorAgentService} sitting under both {@code ClientPromptController} and the
 * orchestrator.
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

    @Autowired
    private ObjectStorageService objectStorageService;

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

    /**
     * Fetches a previously-uploaded object and writes its raw bytes straight into a sandbox's
     * workspace, so a model never has to hold (or re-emit) the content itself to get it there --
     * unlike {@code run_python_code}'s {@code files} parameter, this has no size ceiling tied to
     * a model's context budget.
     *
     * @param sandboxName the exact sandbox name
     * @param objectMetadataId the {@code ObjectMetadata} id to stage
     * @param filename the name to stage it under; the object's original filename when {@code
     *     null}
     * @return the staged filename, content type, and size -- never the content itself
     */
    public StagedObjectInfo stageObject(
        final String sandboxName, final String objectMetadataId, final String filename) {
        var content = this.objectStorageService.retrieve(objectMetadataId);
        var resolvedFilename = Objects.requireNonNullElse(filename, content.filename());
        var bytes = readAllBytes(content.resource());
        this.executionClient.stageFile(sandboxName, resolvedFilename, bytes);
        return new StagedObjectInfo(resolvedFilename, content.contentType(), content.size());
    }

    private static byte[] readAllBytes(final Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new SandboxExecutionException("Could not read object content to stage", e);
        }
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
