package org.vader.core.server.controller.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoMapper;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;

/** Read-only query API for {@link ClientPromptEntity} (file bytes are never returned). */
@RestController
@RequestMapping("/vader/core-server/data/client-prompt")
public class ClientPromptDaoController
    extends GenericVaderDaoController<ClientPromptEntity, ClientPrompt> {

    @Autowired
    private EntityDao<ClientPromptEntity> entityDao;

    @Autowired
    private ClientPromptDtoMapper dtoMapper;

    @Override
    public EntityDao<ClientPromptEntity> getDataAccessObject() {
        return this.entityDao;
    }

    @Override
    public InterfaceEntityToDtoMapper<ClientPromptEntity, ClientPrompt>
        getDataTransferObjectMapper() {
        return this.dtoMapper;
    }
}
