package org.vader.core.server.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.sandbox.model.WorkspaceFile;
import org.vader.core.server.storage.ObjectMetadataRepository;
import org.vader.core.server.workflow.TaskAttemptRepository;

class TaskWorkspaceFileResolverTest {

    private final Map<String, TaskAttemptEntity> latestAttempts = new HashMap<>();
    private final Map<String, List<ObjectMetadataEntity>> savedFiles = new HashMap<>();
    private TaskGraphEntity taskGraph;
    private OffsetDateTime clock;
    private TaskWorkspaceFileResolver resolver;

    @BeforeEach
    void setUp() {
        var taskAttemptRepository = mock(TaskAttemptRepository.class);
        when(taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc(anyString()))
            .thenAnswer(call -> Optional.ofNullable(this.latestAttempts.get(call.getArgument(0))));
        var objectMetadataRepository = mock(ObjectMetadataRepository.class);
        when(objectMetadataRepository.findByTaskAttemptIdOrderByCreatedAtAsc(anyString()))
            .thenAnswer(call -> this.savedFiles.getOrDefault(call.getArgument(0), List.of()));
        this.resolver = TaskWorkspaceFileResolverObjectMother.over(
            taskAttemptRepository, objectMetadataRepository);

        var workflow = new WorkflowEntity();
        workflow.setClientPrompt(new ClientPromptEntity());
        var taskPlan = new TaskPlanEntity();
        taskPlan.setWorkflow(workflow);
        this.taskGraph = new TaskGraphEntity();
        this.taskGraph.setTaskPlan(taskPlan);
        this.clock = OffsetDateTime.now();
    }

    private TaskEntity planned(final String title, final TaskEntity... dependsOn) {
        var task = new TaskEntity();
        task.setId(UUID.randomUUID().toString());
        task.setTitle(title);
        task.setTaskGraph(this.taskGraph);
        task.getDependsOn().addAll(List.of(dependsOn));
        return task;
    }

    private TaskEntity subtaskOf(
            final TaskAttemptEntity decomposed, final String title, final TaskEntity... dependsOn) {
        var parent = decomposed.getTask();
        var subtask = new TaskEntity();
        subtask.setId(UUID.randomUUID().toString());
        subtask.setTitle(title);
        subtask.setParentTask(parent);
        subtask.setSpawnedByAttempt(decomposed);
        subtask.getDependsOn().addAll(List.of(dependsOn));
        parent.getSubTasks().add(subtask);
        return subtask;
    }

    private TaskAttemptEntity attempt(final TaskEntity task, final int number) {
        var attempt = new TaskAttemptEntity();
        attempt.setId(UUID.randomUUID().toString());
        attempt.setTask(task);
        attempt.setAttemptNumber(number);
        this.latestAttempts.put(task.getId(), attempt);
        this.savedFiles.put(attempt.getId(), new ArrayList<>());
        return attempt;
    }

    private ObjectMetadataEntity save(final TaskAttemptEntity attempt, final String filename) {
        var file = new ObjectMetadataEntity();
        file.setId(UUID.randomUUID().toString());
        file.setOriginalFilename(filename);
        file.setTaskAttempt(attempt);
        this.clock = this.clock.plusSeconds(1);
        file.setCreatedAt(this.clock);
        this.savedFiles.get(attempt.getId()).add(file);
        return file;
    }

    private List<String> namesFor(final TaskEntity task) {
        return this.resolver.filesFor(task).stream().map(WorkspaceFile::filename).toList();
    }

    @Test
    void filesFor_includesFilesSavedByEveryTransitivePrerequisite() {
        var load = planned("Load");
        save(attempt(load, 1), "loaded.csv");
        var split = planned("Split", load);
        save(attempt(split, 1), "monthly.csv");
        var report = planned("Report", split);

        var files = this.resolver.filesFor(report);

        assertThat(files).extracting(WorkspaceFile::filename)
            .containsExactly("monthly.csv", "loaded.csv");
        assertThat(files).extracting(WorkspaceFile::producedBy).containsExactly("Split", "Load");
    }

    @Test
    void filesFor_leavesOutTasksItDoesNotDependOn() {
        var unrelated = planned("Unrelated");
        save(attempt(unrelated, 1), "other.csv");
        var task = planned("Report");

        assertThat(namesFor(task)).isEmpty();
    }

    @Test
    void filesFor_leavesOutTheTasksOwnEarlierAttempts() {
        var task = planned("Split");
        save(attempt(task, 1), "monthly.csv");

        assertThat(namesFor(task)).isEmpty();
    }

    @Test
    void filesFor_givesSubtaskTheDecomposedAttemptsAndEarlierStepsFiles() {
        var parent = planned("Convert");
        var decomposed = attempt(parent, 1);
        save(decomposed, "draft.csv");
        var first = subtaskOf(decomposed, "Build months");
        save(attempt(first, 1), "months.csv");
        var second = subtaskOf(decomposed, "Check months", first);

        assertThat(namesFor(second)).containsExactlyInAnyOrder("months.csv", "draft.csv");
    }

    @Test
    void filesFor_includesFilesSavedByDecomposedPrerequisitesLiveSubtasks() {
        var convert = planned("Convert");
        var decomposed = attempt(convert, 1);
        var step = subtaskOf(decomposed, "Build months");
        save(attempt(step, 1), "monthly.csv");
        var report = planned("Report", convert);

        assertThat(namesFor(report)).containsExactly("monthly.csv");
    }

    @Test
    void filesFor_keepsTheNewestFileOfEachName() {
        var convert = planned("Convert");
        var decomposed = attempt(convert, 1);
        save(decomposed, "monthly.csv");
        var fix = subtaskOf(decomposed, "Fix months");
        var newest = save(attempt(fix, 1), "monthly.csv");
        var report = planned("Report", convert);

        var files = this.resolver.filesFor(report);

        assertThat(files).extracting(WorkspaceFile::objectMetadataId)
            .containsExactly(newest.getId());
    }
}
