package org.vader.core.server.controller.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.WorkflowDtoMapper;
import org.vader.common.model.vader.dto.Workflow;
import org.vader.common.model.vader.entity.WorkflowEntity;

/** Read-only query API for {@link WorkflowEntity}. */
@RestController
@RequestMapping("/vader/core-server/data/workflow")
public class WorkflowDaoController extends GenericVaderDaoController<WorkflowEntity, Workflow> {

    @Autowired
    private EntityDao<WorkflowEntity> entityDao;

    @Autowired
    private WorkflowDtoMapper dtoMapper;

    @Override
    public EntityDao<WorkflowEntity> getDataAccessObject() {
        return this.entityDao;
    }

    @Override
    public InterfaceEntityToDtoMapper<WorkflowEntity, Workflow> getDataTransferObjectMapper() {
        return this.dtoMapper;
    }
}
