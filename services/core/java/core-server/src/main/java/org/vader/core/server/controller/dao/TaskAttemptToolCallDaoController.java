package org.vader.core.server.controller.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskAttemptToolCallDtoMapper;
import org.vader.common.model.vader.dto.TaskAttemptToolCall;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;

/** Read-only query API for {@link TaskAttemptToolCallEntity}. */
@RestController
@RequestMapping("/vader/core-server/data/task-attempt-tool-call")
public class TaskAttemptToolCallDaoController
    extends GenericVaderDaoController<TaskAttemptToolCallEntity, TaskAttemptToolCall> {

    @Autowired
    private EntityDao<TaskAttemptToolCallEntity> entityDao;

    @Autowired
    private TaskAttemptToolCallDtoMapper dtoMapper;

    @Override
    public EntityDao<TaskAttemptToolCallEntity> getDataAccessObject() {
        return this.entityDao;
    }

    @Override
    public InterfaceEntityToDtoMapper<TaskAttemptToolCallEntity, TaskAttemptToolCall>
        getDataTransferObjectMapper() {
        return this.dtoMapper;
    }
}
