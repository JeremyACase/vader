package org.vader.core.server.controller;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.model.vader.queue.BackPressure;
import org.vader.core.server.service.backpressure.BackpressureCalculator;
import org.vader.core.server.service.registries.BackpressureRegistry;

/**
 * Read-only view of the back pressure on the service's inbox/outbox queues. The individual queue
 * messages are not exposed; callers observe queue load here instead.
 */
@RestController
@RequestMapping("/vader/core-server/backpressure")
public class BackpressureController {

    private static final Logger logger = LoggerFactory.getLogger(BackpressureController.class);

    @Autowired
    private BackpressureRegistry registry;

    @Autowired
    private BackpressureCalculator calculator;

    /**
     * Lists every model type that has an inbox/outbox queue.
     *
     * @return the queued model-type names
     */
    @GetMapping
    public Set<String> queuedModelTypes() {
        return this.registry.names();
    }

    /**
     * Returns the current back pressure snapshot for one queued model type.
     *
     * @param modelType the payload model type (e.g. {@code "ClientPrompt"})
     * @return the snapshot
     */
    @GetMapping("/{modelType}")
    public ResponseEntity<BackPressure> backPressureFor(
        @PathVariable("modelType") final String modelType) {

        logger.debug("Received a back pressure request for {}", modelType);
        return ResponseEntity.ok(this.calculator.calculateFor(modelType));
    }
}
