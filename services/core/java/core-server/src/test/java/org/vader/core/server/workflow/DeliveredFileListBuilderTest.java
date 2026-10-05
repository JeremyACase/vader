package org.vader.core.server.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.storage.ObjectDescriptor;
import org.vader.core.server.storage.ObjectStorageService;

class DeliveredFileListBuilderTest {

    private TaskAttemptRepository taskAttemptRepository;
    private ObjectStorageService objectStorageService;
    private DeliveredFileListBuilder builder;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.objectStorageService = mock(ObjectStorageService.class);

        this.builder = new DeliveredFileListBuilder();
        ReflectionTestUtils.setField(
            this.builder, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(
            this.builder, "objectStorageService", this.objectStorageService);
    }

    private static TaskEntity task(final String id) {
        var task = new TaskEntity();
        task.setId(id);
        return task;
    }

    private void latestAttempt(
        final String taskId, final String attemptId, final TaskAttemptStatus status) {
        var attempt = new TaskAttemptEntity();
        attempt.setId(attemptId);
        attempt.setStatus(status);
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc(taskId))
            .thenReturn(Optional.of(attempt));
    }

    @Test
    void build_inlinesSmallTextFilesAndLinksEveryFile() {
        latestAttempt("t1", "a1", TaskAttemptStatus.SUCCEEDED);
        when(this.objectStorageService.describeTaskAttemptOutputs("a1")).thenReturn(List.of(
            new ObjectDescriptor("o1", "server.py", "text/x-python", 120),
            new ObjectDescriptor("o2", "chart.png", "image/png", 4096)));
        when(this.objectStorageService.retrieveText("o1")).thenReturn("print('hi')");

        var files = this.builder.build(List.of(task("t1")));

        assertThat(files).hasSize(2);
        assertThat(files.get(0).filename()).isEqualTo("server.py");
        assertThat(files.get(0).downloadPath())
            .isEqualTo("/vader/core-server/object-storage/o1/content");
        assertThat(files.get(0).inlineContent()).isEqualTo("print('hi')");
        assertThat(files.get(1).filename()).isEqualTo("chart.png");
        assertThat(files.get(1).inlineContent()).isNull();
        verify(this.objectStorageService, never()).retrieveText("o2");
    }

    @Test
    void build_linksButDoesNotInlineLargeTextFiles() {
        latestAttempt("t1", "a1", TaskAttemptStatus.SUCCEEDED);
        when(this.objectStorageService.describeTaskAttemptOutputs("a1")).thenReturn(List.of(
            new ObjectDescriptor(
                "o1", "big.csv", "text/csv", DeliveredFileListBuilder.MAX_INLINE_BYTES + 1)));

        var files = this.builder.build(List.of(task("t1")));

        assertThat(files).singleElement()
            .satisfies(file -> assertThat(file.inlineContent()).isNull());
        verify(this.objectStorageService, never()).retrieveText(any());
    }

    @Test
    void build_includesRuntimeSubtasksUploads() {
        var parent = task("parent");
        var subtask = task("sub");
        parent.getSubTasks().add(subtask);
        latestAttempt("parent", "a-parent", TaskAttemptStatus.SUCCEEDED);
        latestAttempt("sub", "a-sub", TaskAttemptStatus.SUCCEEDED);
        when(this.objectStorageService.describeTaskAttemptOutputs("a-parent"))
            .thenReturn(List.of());
        when(this.objectStorageService.describeTaskAttemptOutputs("a-sub")).thenReturn(List.of(
            new ObjectDescriptor("o1", "report.pdf", "application/pdf", 900)));

        var files = this.builder.build(List.of(parent));

        assertThat(files).singleElement()
            .satisfies(file -> assertThat(file.filename()).isEqualTo("report.pdf"));
    }

    @Test
    void build_skipsTasksWhoseLatestAttemptDidNotSucceed() {
        latestAttempt("t1", "a1", TaskAttemptStatus.FAILED);
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t2"))
            .thenReturn(Optional.empty());

        var files = this.builder.build(List.of(task("t1"), task("t2")));

        assertThat(files).isEmpty();
        verify(this.objectStorageService, never()).describeTaskAttemptOutputs(any());
    }
}
