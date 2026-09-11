package org.vader.core.server.controller.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskAttemptTranscriptDtoMapper;
import org.vader.common.model.vader.dto.TaskAttemptTranscript;
import org.vader.common.model.vader.entity.TaskAttemptTranscriptEntity;

/** Read-only query API for {@link TaskAttemptTranscriptEntity}. */
@RestController
@RequestMapping("/vader/core-server/data/task-attempt-transcript")
public class TaskAttemptTranscriptDaoController
    extends GenericVaderDaoController<TaskAttemptTranscriptEntity, TaskAttemptTranscript> {

    @Autowired
    private EntityDao<TaskAttemptTranscriptEntity> entityDao;

    @Autowired
    private TaskAttemptTranscriptDtoMapper dtoMapper;

    @Override
    public EntityDao<TaskAttemptTranscriptEntity> getDataAccessObject() {
        return this.entityDao;
    }

    @Override
    public InterfaceEntityToDtoMapper<TaskAttemptTranscriptEntity, TaskAttemptTranscript>
        getDataTransferObjectMapper() {
        return this.dtoMapper;
    }
}
