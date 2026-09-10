package org.vader.common.library.implementation.service.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.IngressResponse;
import org.vader.common.model.vader.entity.AbstractModelEntity;

/**
 * Builds an {@link IngressResponse} receipt from a persisted entity, carrying its id and model
 * type back to a caller whose request was accepted for asynchronous processing.
 */
@Service
public class VaderIngressResponseBuilder {

    private static final Logger logger =
        LoggerFactory.getLogger(VaderIngressResponseBuilder.class);

    /**
     * Builds a receipt for a persisted entity.
     *
     * @param entity the persisted payload record
     * @return an ingress response carrying the entity's id and model type
     */
    public IngressResponse buildIngressResponseFrom(final AbstractModelEntity entity) {
        logger.debug("Building ingress response for {} with id {}",
            entity.getModelType(), entity.getId());
        var response = new IngressResponse();
        response.setId(entity.getId());
        response.setPayloadModelType(entity.getModelType());
        return response;
    }
}
