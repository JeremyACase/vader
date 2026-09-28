package org.vader.core.server.controller.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskUpdateDtoMapper;
import org.vader.common.model.vader.dto.TaskUpdate;
import org.vader.common.model.vader.entity.TaskUpdateEntity;

/** Read-only query API for {@link TaskUpdateEntity}. */
@RestController
@RequestMapping("/vader/core-server/data/task-update")
public class TaskUpdateDaoController
    extends GenericVaderDaoController<TaskUpdateEntity, TaskUpdate> {

    @Autowired
    private EntityDao<TaskUpdateEntity> entityDao;

    @Autowired
    private TaskUpdateDtoMapper dtoMapper;

    @Override
    public EntityDao<TaskUpdateEntity> getDataAccessObject() {
        return this.entityDao;
    }

    @Override
    public InterfaceEntityToDtoMapper<TaskUpdateEntity, TaskUpdate>
        getDataTransferObjectMapper() {
        return this.dtoMapper;
    }
}
