package org.vader.core.server.sandbox;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.sandbox.model.WorkspaceFile;
import org.vader.core.server.storage.ObjectMetadataRepository;
import org.vader.core.server.workflow.LiveSubtaskFilter;
import org.vader.core.server.workflow.TaskAttemptRepository;

/**
 * Resolves the files a task's sandbox workspace starts with: those attached to the client prompt,
 * plus every file saved by the work this task builds on. Each attempt gets a fresh sandbox, so
 * uploading is the only way one task's output reaches the next; without this, a step that saved
 * the file a later step needs would leave that later step nothing to open.
 *
 * <p>The work a task builds on is every task it transitively depends on -- its own prerequisites
 * and, for a runtime subtask, its ancestors' -- each contributing its latest attempt's files and
 * those of that attempt's live subtasks, plus, for a subtask, the decomposed attempts it continues.
 * The task's own earlier attempts are left out: a reattempt starts from scratch.</p>
 *
 * <p>Reads lazy associations, so call it inside a transaction. Names come from
 * {@link WorkspaceFileNaming}, so the context an agent is shown and the files staged for it agree.
 * </p>
 */
@Component
public class TaskWorkspaceFileResolver {

    private static final Comparator<ObjectMetadataEntity> NEWEST_FIRST = Comparator
        .comparing(ObjectMetadataEntity::getCreatedAt,
            Comparator.nullsFirst(Comparator.<OffsetDateTime>naturalOrder()))
        .thenComparing(ObjectMetadataEntity::getId)
        .reversed();

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private LiveSubtaskFilter liveSubtaskFilter;

    @Autowired
    private ObjectMetadataRepository objectMetadataRepository;

    /**
     * The files a task's workspace starts with, each under the name it is staged as.
     *
     * @param task the task about to run
     * @return the attached files, then the newest upstream file of each name
     */
    public List<WorkspaceFile> filesFor(final TaskEntity task) {
        var clientPrompt =
            task.owningTaskGraph().getTaskPlan().getWorkflow().getClientPrompt();
        return WorkspaceFileNaming.assign(clientPrompt.getFiles(), this.upstreamFilesOf(task));
    }

    private List<ObjectMetadataEntity> upstreamFilesOf(final TaskEntity task) {
        var upstream = new LinkedHashSet<TaskEntity>();
        collectUpstream(task, upstream);
        return Stream.concat(
                upstream.stream().flatMap(this::producingAttempts),
                continuedAttempts(task))
            .distinct()
            .flatMap(attempt -> this.objectMetadataRepository
                .findByTaskAttemptIdOrderByCreatedAtAsc(attempt.getId()).stream())
            .sorted(NEWEST_FIRST)
            .toList();
    }

    private static void collectUpstream(final TaskEntity task, final Set<TaskEntity> upstream) {
        task.getDependsOn().stream()
            .filter(upstream::add)
            .toList()
            .forEach(dependency -> collectUpstream(dependency, upstream));
        Optional.ofNullable(task.getParentTask())
            .ifPresent(parent -> collectUpstream(parent, upstream));
    }

    private Stream<TaskAttemptEntity> producingAttempts(final TaskEntity task) {
        var latest = this.taskAttemptRepository
            .findFirstByTaskIdOrderByAttemptNumberDesc(task.getId())
            .stream();
        var subtasks = this.liveSubtaskFilter.liveSubtasksOf(task).stream()
            .flatMap(this::producingAttempts);
        return Stream.concat(latest, subtasks);
    }

    private static Stream<TaskAttemptEntity> continuedAttempts(final TaskEntity task) {
        var spawnedBy = Optional.ofNullable(task.getSpawnedByAttempt()).stream();
        var ancestors = Optional.ofNullable(task.getParentTask()).stream()
            .flatMap(TaskWorkspaceFileResolver::continuedAttempts);
        return Stream.concat(spawnedBy, ancestors);
    }
}
