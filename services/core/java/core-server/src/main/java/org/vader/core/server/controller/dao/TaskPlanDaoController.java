package org.vader.core.server.controller.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskPlanDtoMapper;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.common.model.vader.entity.TaskPlanEntity;

/** Read-only query API for {@link TaskPlanEntity}. */
@RestController
@RequestMapping("/vader/core-server/data/task-plan")
public class TaskPlanDaoController extends GenericVaderDaoController<TaskPlanEntity, TaskPlan> {

    @Autowired
    private EntityDao<TaskPlanEntity> entityDao;

    @Autowired
    private TaskPlanDtoMapper dtoMapper;

    @Override
    public EntityDao<TaskPlanEntity> getDataAccessObject() {
        return this.entityDao;
    }

    @Override
    public InterfaceEntityToDtoMapper<TaskPlanEntity, TaskPlan> getDataTransferObjectMapper() {
        return this.dtoMapper;
    }
}
