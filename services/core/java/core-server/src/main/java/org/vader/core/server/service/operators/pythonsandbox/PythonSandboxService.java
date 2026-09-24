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

    // reconcile() itself returns as soon as the Deployment/Service are *created*, not once the
    // pod is actually serving traffic -- without waiting here, a caller (a model, per its own
    // task instructions) that immediately stages a file or runs code right after create_sandbox
    // returns hits a real race: the sandbox's Service has no ready endpoints yet, so the very
    // next call fails with a connection error. The default (30 * 500ms = 15s) comfortably covers
    // this image's typical readinessProbe pass time (2s initial delay + a 5s period, per
    // PythonSandboxManifestBuilder) even on a cold pull.
    @Value("${vader.operators.python-sandbox.sandbox.ready-poll-max-attempts:30}")
    private int readyPollMaxAttempts;

    @Value("${vader.operators.python-sandbox.sandbox.ready-poll-interval-ms:500}")
    private long readyPollIntervalMs;

    private static final String RUNNING_PHASE = "Running";

    /**
     * Creates a sandbox, or returns the existing one if a sandbox of the resolved name is already
     * present, waiting (up to a bounded timeout) for it to actually be ready to accept requests.
     *
     * @param requestedName an optional friendly name; a unique name is generated when blank
     * @return the sandbox details; {@code phase} may still be {@code "Pending"} if it did not
     *     become ready within the wait budget
     */
    public SandboxInfo create(final String requestedName) {
        return this.ensureReady(SandboxNaming.resolve(requestedName));
    }

    /**
     * Creates the sandbox named exactly {@code name} if it doesn't already exist, then waits (up
     * to the same bounded timeout as {@link #create}) for it to be ready. Unlike {@link #create},
     * the name is used verbatim rather than resolved through {@link SandboxNaming} -- for a
     * caller that already owns a deterministic name, such as an attempt-owned sandbox.
     *
     * @param name the exact, already-valid sandbox name
     * @return the sandbox details; {@code phase} may still be {@code "Pending"} if it did not
     *     become ready within the wait budget
     */
    public SandboxInfo ensureReady(final String name) {
        var spec = new PythonSandboxSpec(name, null);
        var managed = this.awaitReady(spec, this.operator.reconcile(spec));
        return this.toInfo(managed);
    }

    private ManagedResource awaitReady(
            final PythonSandboxSpec spec, final ManagedResource initial) {
        var managed = initial;
        var attempts = 0;
        while (!RUNNING_PHASE.equals(managed.phase()) && attempts < this.readyPollMaxAttempts) {
            sleep(this.readyPollIntervalMs);
            managed = this.operator.reconcile(spec);
            attempts++;
        }
        return managed;
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    /**
     * Stages a previously-uploaded object under {@code filename}, unless a file of that name is
     * already in the sandbox's workspace -- cheap enough to call before every code run, and it
     * restores a file lost to a container restart without re-sending bytes that are still there.
     *
     * @param sandboxName the exact sandbox name
     * @param objectMetadataId the {@code ObjectMetadata} id to stage
     * @param filename the name to stage it under
     */
    public void stageObjectIfAbsent(
        final String sandboxName, final String objectMetadataId, final String filename) {
        if (!this.executionClient.isStaged(sandboxName, filename)) {
            this.stageObject(sandboxName, objectMetadataId, filename);
        }
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
