package org.vader.core.server.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.repository.TaskUpdateRepository;

class TaskUpdateServiceTest {

    private TaskUpdateRepository taskUpdateRepository;
    private TaskUpdateService service;

    @BeforeEach
    void setUp() {
        this.taskUpdateRepository = mock(TaskUpdateRepository.class);
        this.service = new TaskUpdateService();
        ReflectionTestUtils.setField(
            this.service, "taskUpdateRepository", this.taskUpdateRepository);
    }

    @Test
    void record_persistsAnUpdateAgainstTheGivenTaskAndAttempt() {
        var task = new TaskEntity();
        task.setId("t1");
        var attempt = new TaskAttemptEntity();
        attempt.setId("a1");

        this.service.record(
            task, attempt, TaskUpdateType.UPDATE, "retrying with a different query",
            TaskUpdateAuthor.TASK_AGENT);

        var captor = ArgumentCaptor.forClass(TaskUpdateEntity.class);
        verify(this.taskUpdateRepository).save(captor.capture());
        assertThat(captor.getValue().getTask()).isSameAs(task);
        assertThat(captor.getValue().getTaskAttempt()).isSameAs(attempt);
        assertThat(captor.getValue().getType()).isEqualTo(TaskUpdateType.UPDATE);
        assertThat(captor.getValue().getDescription())
            .isEqualTo("retrying with a different query");
        assertThat(captor.getValue().getAuthor()).isEqualTo(TaskUpdateAuthor.TASK_AGENT);
    }

    @Test
    void record_allowsNullAttempt() {
        var task = new TaskEntity();
        task.setId("t1");

        this.service.record(task, null, TaskUpdateType.UPDATE, "note", TaskUpdateAuthor.SYSTEM);

        var captor = ArgumentCaptor.forClass(TaskUpdateEntity.class);
        verify(this.taskUpdateRepository).save(captor.capture());
        assertThat(captor.getValue().getTaskAttempt()).isNull();
    }
}
