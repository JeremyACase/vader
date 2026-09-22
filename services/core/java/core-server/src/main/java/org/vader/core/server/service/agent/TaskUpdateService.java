package org.vader.core.server.service.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.repository.TaskUpdateRepository;

/**
 * Records a single update against a task. Shared across every kind of author -- see
 * {@link TaskUpdateAuthor} for who writes what.
 */
@Service
public class TaskUpdateService {

    @Autowired
    private TaskUpdateRepository taskUpdateRepository;

    /**
     * Records one update against a task.
     *
     * @param task the task the update is about
     * @param taskAttempt the specific attempt the update is about, or {@code null} if it isn't
     *     about any one attempt in particular
     * @param type the kind of update
     * @param description text describing the update
     * @param author which role wrote this update
     * @return the persisted update
     */
    @Transactional
    public TaskUpdateEntity record(
            final TaskEntity task, final TaskAttemptEntity taskAttempt, final TaskUpdateType type,
            final String description, final TaskUpdateAuthor author) {
        var update = new TaskUpdateEntity();
        update.setTask(task);
        update.setTaskAttempt(taskAttempt);
        update.setType(type);
        update.setDescription(description);
        update.setAuthor(author);
        return this.taskUpdateRepository.save(update);
    }
}
