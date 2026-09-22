package org.vader.core.server.service.tools.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskRepository;
import org.vader.core.server.service.agent.TaskUpdateService;

class TaskUpdateToolsTest {

    private TaskRepository taskRepository;
    private TaskAttemptRepository taskAttemptRepository;
    private TaskUpdateService taskUpdateService;
    private TaskUpdateTools tools;

    @BeforeEach
    void setUp() {
        this.taskRepository = mock(TaskRepository.class);
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.taskUpdateService = mock(TaskUpdateService.class);
        this.tools = new TaskUpdateTools();
        ReflectionTestUtils.setField(this.tools, "taskRepository", this.taskRepository);
        ReflectionTestUtils.setField(
            this.tools, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(this.tools, "taskUpdateService", this.taskUpdateService);
    }

    @Test
    void postTaskUpdate_recordsAnUpdateTypeNoteAgainstTheTaskAndAttempt() {
        var task = new TaskEntity();
        task.setId("t1");
        var attempt = new TaskAttemptEntity();
        attempt.setId("a1");
        when(this.taskRepository.findById("t1")).thenReturn(Optional.of(task));
        when(this.taskAttemptRepository.findById("a1")).thenReturn(Optional.of(attempt));

        var result = this.tools.postTaskUpdate("t1", "a1", "hit an obstacle, retrying");

        var descriptionCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.taskUpdateService).record(
            same(task), same(attempt), eq(TaskUpdateType.UPDATE), descriptionCaptor.capture(),
            eq(TaskUpdateAuthor.TASK_AGENT));
        assertThat(descriptionCaptor.getValue()).isEqualTo("hit an obstacle, retrying");
        assertThat(result).contains("t1");
    }

    @Test
    void postTaskUpdate_withUnknownTaskId_throws() {
        when(this.taskRepository.findById("bogus")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.tools.postTaskUpdate("bogus", "a1", "note"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void postTaskUpdate_withUnknownAttemptId_throws() {
        var task = new TaskEntity();
        task.setId("t1");
        when(this.taskRepository.findById("t1")).thenReturn(Optional.of(task));
        when(this.taskAttemptRepository.findById("bogus")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.tools.postTaskUpdate("t1", "bogus", "note"))
            .isInstanceOf(NoSuchElementException.class);
    }
}
