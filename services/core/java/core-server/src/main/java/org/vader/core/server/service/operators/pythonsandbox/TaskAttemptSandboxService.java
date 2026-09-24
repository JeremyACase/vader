package org.vader.core.server.service.operators.pythonsandbox;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.core.server.exceptions.SandboxExecutionException;
import org.vader.core.server.exceptions.UnknownAssignmentException;
import org.vader.core.server.models.SandboxExecutionRequest;
import org.vader.core.server.models.SandboxExecutionResult;
import org.vader.core.server.models.WorkspaceFile;
import org.vader.core.server.repository.TaskAttemptRepository;

/**
 * Owns the one Python sandbox each task attempt gets, so a task agent never creates, names,
 * stages into, or cleans up a sandbox itself -- it only ever runs code.
 *
 * <p>The sandbox is provisioned lazily, on the attempt's first code run rather than at dispatch:
 * a task that never runs Python (e.g. "summarize the findings") never costs a pod. That first run
 * blocks for the sandbox's startup (bounded by {@link PythonSandboxService#ensureReady}); every
 * later run finds it already there. Before every run, each file attached to the original request
 * is staged if it isn't already in the workspace, under the name {@link WorkspaceFileNaming}
 * assigns it -- the same name the task agent's own context tells it to open.</p>
 *
 * <p>Deletion happens on settlement ({@code TaskAttemptSandboxCleanupListener}), not here.</p>
 */
@Service
@ConditionalOnProperty(
    name = {"vader.operators.enabled", "vader.operators.python-sandbox.enabled"},
    havingValue = "true",
    matchIfMissing = false)
public class TaskAttemptSandboxService {

    private static final String RUNNING_PHASE = "Running";

    @Autowired
    private PythonSandboxService sandboxService;

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    /**
     * Runs code in the calling attempt's own sandbox, provisioning it and staging the request's
     * attached files first if needed.
     *
     * @param taskAttemptId the calling attempt's id
     * @param code the Python source to run
     * @return the run's stdout/stderr/exit code
     * @throws SandboxExecutionException if the sandbox never became ready, or is unreachable
     */
    @Transactional
    public SandboxExecutionResult runCode(final String taskAttemptId, final String code) {
        var sandboxName = TaskAttemptSandboxNaming.resolve(taskAttemptId);
        var workspaceFiles = this.workspaceFilesFor(taskAttemptId);
        this.requireReady(sandboxName);
        workspaceFiles.forEach(file -> this.sandboxService.stageObjectIfAbsent(
            sandboxName, file.objectMetadataId(), file.filename()));
        return this.sandboxService.runCode(
            sandboxName, new SandboxExecutionRequest(code, null, null));
    }

    /**
     * Deletes the attempt's sandbox. A no-op in effect if the attempt never ran code, since the
     * sandbox was then never created.
     *
     * @param taskAttemptId the settled attempt's id
     */
    public void delete(final String taskAttemptId) {
        this.sandboxService.delete(TaskAttemptSandboxNaming.resolve(taskAttemptId));
    }

    /**
     * The files attached to a client prompt, with the names they are (or will be) staged under.
     *
     * @param clientPrompt the original request
     * @return one entry per attached file
     */
    public static List<WorkspaceFile> workspaceFilesFor(final ClientPromptEntity clientPrompt) {
        return WorkspaceFileNaming.assign(clientPrompt.getFiles());
    }

    private List<WorkspaceFile> workspaceFilesFor(final String taskAttemptId) {
        var attempt = this.taskAttemptRepository.findById(taskAttemptId)
            .orElseThrow(() -> new UnknownAssignmentException(
                "Unknown assignment: " + taskAttemptId));
        var clientPrompt =
            attempt.getTask().getTaskGraph().getTaskPlan().getWorkflow().getClientPrompt();
        return workspaceFilesFor(clientPrompt);
    }

    private void requireReady(final String sandboxName) {
        var info = this.sandboxService.ensureReady(sandboxName);
        if (!RUNNING_PHASE.equals(info.phase())) {
            throw new SandboxExecutionException(
                "Your Python sandbox did not become ready in time (last phase: " + info.phase()
                    + "). Try running the code again.");
        }
    }
}
