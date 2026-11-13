package org.vader.core.server.sandbox;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.vader.core.server.sandbox.model.SandboxFileInfo;
import org.vader.core.server.sandbox.model.WorkspaceFile;
import org.vader.core.server.storage.FileStorageException;
import org.vader.core.server.storage.ObjectDescriptor;
import org.vader.core.server.storage.ObjectStorageService;
import org.vader.core.server.taskagent.TaskAttemptLifecycleService;

/**
 * Saves the files a succeeding attempt's code left in its sandbox workspace, so the work reaches
 * object storage -- and with it the evaluator, the tasks that build on it and the user -- whether
 * or not the agent itself called {@code upload_object}.
 *
 * <p>Every file the workspace lists is saved except one already stored under the same name with
 * identical content: a file staged in at the start and left unchanged, or one the agent uploaded
 * itself and did not change afterwards. Content is compared by SHA-256, and stored content is only
 * read for a file whose name and size already match.</p>
 *
 * <p>Must run before the attempt settles: settling deletes the sandbox and queues the attempt's
 * review, which judges it by the files it saved. A sandbox that cannot be listed or read is
 * logged and leaves the attempt with whatever it uploaded itself, never failing the result.</p>
 */
@Service
@ConditionalOnProperty(
    name = {"vader.operators.enabled", "vader.operators.python-sandbox.enabled"},
    havingValue = "true",
    matchIfMissing = false)
public class TaskAttemptOutputCollectionService {

    private static final Logger logger =
        LoggerFactory.getLogger(TaskAttemptOutputCollectionService.class);

    private static final String DIGEST_ALGORITHM = "SHA-256";

    @Autowired
    private PythonSandboxService sandboxService;

    @Autowired
    private TaskAttemptSandboxService taskAttemptSandboxService;

    @Autowired
    private TaskAttemptLifecycleService lifecycleService;

    @Autowired
    private TaskWorkspaceFileResolver taskWorkspaceFileResolver;

    @Autowired
    private ObjectStorageService objectStorageService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @PostConstruct
    void init() {
        this.transaction = new TransactionTemplate(this.transactionManager);
    }

    /**
     * Saves every file in the attempt's workspace that is not already stored unchanged.
     *
     * @param taskAttemptId the attempt about to settle
     * @return the files saved, possibly none
     * @throws org.vader.core.server.taskagent.AssignmentAlreadyTerminalException if the attempt
     *     already settled
     */
    public List<ObjectDescriptor> collect(final String taskAttemptId) {
        var stored = this.transaction.execute(status -> this.storedFilesOf(taskAttemptId));
        List<ObjectDescriptor> collected;
        try {
            collected = this.collectUnstored(taskAttemptId, stored);
        } catch (RuntimeException e) {
            logger.warn("Could not save the files attempt {} left in its sandbox: {}",
                taskAttemptId, e.getMessage());
            collected = List.of();
        }
        return collected;
    }

    private List<ObjectDescriptor> collectUnstored(
            final String taskAttemptId, final List<ObjectDescriptor> stored) {
        var listed = this.sandboxService.listFiles(TaskAttemptSandboxNaming.resolve(taskAttemptId));
        var collected = listed.stream()
            .filter(file -> !this.isStored(file, stored))
            .map(file -> this.taskAttemptSandboxService.uploadFile(taskAttemptId, file.filename()))
            .toList();
        collected.forEach(file -> logger.info(
            "Saved {} that attempt {} left in its sandbox without uploading it",
            file.filename(), taskAttemptId));
        return collected;
    }

    private List<ObjectDescriptor> storedFilesOf(final String taskAttemptId) {
        var task = this.lifecycleService.requireOpen(taskAttemptId).getTask();
        var staged = this.taskWorkspaceFileResolver.filesFor(task).stream()
            .map(this::asStaged);
        var uploaded = this.objectStorageService.describeTaskAttemptOutputs(taskAttemptId).stream();
        return Stream.concat(staged, uploaded).toList();
    }

    private ObjectDescriptor asStaged(final WorkspaceFile file) {
        var stored = this.objectStorageService.describe(file.objectMetadataId());
        return new ObjectDescriptor(
            stored.id(), file.filename(), stored.contentType(), stored.size());
    }

    private boolean isStored(final SandboxFileInfo file, final List<ObjectDescriptor> stored) {
        return stored.stream()
            .filter(candidate -> candidate.filename().equals(file.filename()))
            .filter(candidate -> candidate.size() == file.size())
            .anyMatch(candidate -> this.sha256Of(candidate.id()).equalsIgnoreCase(file.sha256()));
    }

    private String sha256Of(final String objectMetadataId) {
        var resource = this.objectStorageService.retrieve(objectMetadataId).resource();
        try (InputStream in = resource.getInputStream()) {
            return HexFormat.of().formatHex(digest().digest(in.readAllBytes()));
        } catch (IOException e) {
            throw new FileStorageException("Could not read a stored object to compare it", e);
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to provide SHA-256.
            throw new IllegalStateException(DIGEST_ALGORITHM + " is unavailable", e);
        }
    }
}
