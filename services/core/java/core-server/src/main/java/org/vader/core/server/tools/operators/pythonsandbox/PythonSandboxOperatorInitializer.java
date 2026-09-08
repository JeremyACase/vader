package org.vader.core.server.tools.operators.pythonsandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Initializes the Python sandbox operator once the application is ready — the same pattern as
 * ubiquia's {@code InitializationLogic}. A failure to reach Kubernetes is logged, not fatal, so
 * the rest of {@code core-server} still starts. The operator bean is absent (and the field stays
 * {@code null}) when the operator is disabled.
 */
@Component
public class PythonSandboxOperatorInitializer
    implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger =
        LoggerFactory.getLogger(PythonSandboxOperatorInitializer.class);

    @Autowired(required = false)
    private PythonSandboxOperator operator;

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        if (this.operator == null) {
            return;
        }
        try {
            this.operator.init();
        } catch (RuntimeException e) {
            logger.error("Could not initialize the Python sandbox operator: {}", e.getMessage(), e);
        }
    }
}
