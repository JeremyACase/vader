package org.vader.core.server.controller.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskDtoMapper;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.entity.TaskEntity;

/** Read-only query API for {@link TaskEntity}. */
@RestController
@RequestMapping("/vader/core-server/data/task")
public class TaskDaoController extends GenericVaderDaoController<TaskEntity, Task> {

    @Autowired
    private EntityDao<TaskEntity> entityDao;

    @Autowired
    private TaskDtoMapper dtoMapper;

    @Override
    public EntityDao<TaskEntity> getDataAccessObject() {
        return this.entityDao;
    }

    @Override
    public InterfaceEntityToDtoMapper<TaskEntity, Task> getDataTransferObjectMapper() {
        return this.dtoMapper;
    }
}
