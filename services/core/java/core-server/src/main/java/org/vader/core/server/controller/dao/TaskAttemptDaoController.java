package org.vader.core.server.controller.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskAttemptDtoMapper;
import org.vader.common.model.vader.dto.TaskAttempt;
import org.vader.common.model.vader.entity.TaskAttemptEntity;

/** Read-only query API for {@link TaskAttemptEntity}. */
@RestController
@RequestMapping("/vader/core-server/data/task-attempt")
public class TaskAttemptDaoController
    extends GenericVaderDaoController<TaskAttemptEntity, TaskAttempt> {

    @Autowired
    private EntityDao<TaskAttemptEntity> entityDao;

    @Autowired
    private TaskAttemptDtoMapper dtoMapper;

    @Override
    public EntityDao<TaskAttemptEntity> getDataAccessObject() {
        return this.entityDao;
    }

    @Override
    public InterfaceEntityToDtoMapper<TaskAttemptEntity, TaskAttempt>
        getDataTransferObjectMapper() {
        return this.dtoMapper;
    }
}
