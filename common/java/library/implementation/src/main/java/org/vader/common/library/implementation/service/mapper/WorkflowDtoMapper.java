package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.Workflow;
import org.vader.common.model.vader.entity.WorkflowEntity;

/**
 * Maps {@link WorkflowEntity} to {@link Workflow} DTOs.
 *
 * <p>The originating client prompt is emitted as a shallow id reference, since its attached files
 * are not meaningful to re-serialize here.</p>
 */
@Service
@Transactional
public class WorkflowDtoMapper extends GenericDtoMapper<WorkflowEntity, Workflow> {

    @Autowired
    private TaskPlanDtoMapper taskPlanDtoMapper;

    @Override
    public Workflow map(final WorkflowEntity from) {
        Workflow to = null;
        if (Objects.nonNull(from)) {
            to = new Workflow();
            super.setAbstractModelFields(from, to);
            if (Objects.nonNull(from.getClientPrompt())) {
                to.setClientPromptId(from.getClientPrompt().getId());
            }
            to.setTaskPlan(this.taskPlanDtoMapper.map(from.getTaskPlan()));
        }
        return to;
    }
}
