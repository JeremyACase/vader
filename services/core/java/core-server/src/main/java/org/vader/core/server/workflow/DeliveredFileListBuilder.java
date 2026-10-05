package org.vader.core.server.workflow;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.storage.ObjectContentTypeResolver;
import org.vader.core.server.storage.ObjectDescriptor;
import org.vader.core.server.storage.ObjectStorageService;
import org.vader.core.server.workflow.model.DeliveredFile;

/**
 * Builds the list of files a workflow delivers to the user: everything uploaded by the latest
 * attempt of each task, runtime subtasks included, where that attempt succeeded. A failed
 * attempt's uploads are left out, since a later attempt was meant to replace them.
 *
 * <p>Small text files carry their content, so the final answer can show the file itself rather
 * than only a link to it.</p>
 */
@Component
public class DeliveredFileListBuilder {

    /** Text files up to this size are shown in the final answer, not only linked. */
    static final long MAX_INLINE_BYTES = 16 * 1024;

    private static final String DOWNLOAD_PATH = "/vader/core-server/object-storage/%s/content";

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private ObjectStorageService objectStorageService;

    /**
     * Builds the delivered-file list for a task graph's top-level tasks and all their subtasks.
     *
     * @param tasks the task graph's top-level tasks
     * @return every delivered file, possibly empty
     */
    public List<DeliveredFile> build(final Collection<TaskEntity> tasks) {
        return tasks.stream()
            .flatMap(DeliveredFileListBuilder::withSubtasks)
            .map(this::succeededLatestAttempt)
            .flatMap(Optional::stream)
            .flatMap(attempt ->
                this.objectStorageService.describeTaskAttemptOutputs(attempt.getId()).stream())
            .map(this::deliveredFile)
            .toList();
    }

    private static Stream<TaskEntity> withSubtasks(final TaskEntity task) {
        return Stream.concat(
            Stream.of(task),
            task.getSubTasks().stream().flatMap(DeliveredFileListBuilder::withSubtasks));
    }

    private Optional<TaskAttemptEntity> succeededLatestAttempt(final TaskEntity task) {
        return this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc(task.getId())
            .filter(attempt -> attempt.getStatus() == TaskAttemptStatus.SUCCEEDED);
    }

    private DeliveredFile deliveredFile(final ObjectDescriptor descriptor) {
        var inline = ObjectContentTypeResolver.isText(descriptor.contentType())
            && descriptor.size() <= MAX_INLINE_BYTES;
        var content = inline ? this.objectStorageService.retrieveText(descriptor.id()) : null;
        return new DeliveredFile(
            descriptor.filename(), DOWNLOAD_PATH.formatted(descriptor.id()), content);
    }
}
