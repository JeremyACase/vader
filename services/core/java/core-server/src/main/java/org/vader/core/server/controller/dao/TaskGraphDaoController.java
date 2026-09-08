package org.vader.core.server.controller.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskGraphDtoMapper;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.entity.TaskGraphEntity;

/** Read-only query API for {@link TaskGraphEntity}. */
@RestController
@RequestMapping("/vader/core-server/data/task-graph")
public class TaskGraphDaoController extends GenericVaderDaoController<TaskGraphEntity, TaskGraph> {

    @Autowired
    private EntityDao<TaskGraphEntity> entityDao;

    @Autowired
    private TaskGraphDtoMapper dtoMapper;

    @Override
    public EntityDao<TaskGraphEntity> getDataAccessObject() {
        return this.entityDao;
    }

    @Override
    public InterfaceEntityToDtoMapper<TaskGraphEntity, TaskGraph> getDataTransferObjectMapper() {
        return this.dtoMapper;
    }
}
