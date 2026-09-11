package org.vader.core.server.service.initializers.agentharness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.vader.core.server.service.initializers.pythonsandbox.PythonSandboxOperatorInitializer;
import org.vader.core.server.service.operators.agentharness.AgentHarnessOperator;

/**
 * Initializes the agent-harness operator once the application is ready, mirroring
 * {@code PythonSandboxOperatorInitializer}. A failure to reach Kubernetes is logged, not fatal,
 * so the rest of {@code core-server} still starts. The operator bean is absent (and the field
 * stays {@code null}) when the operator is disabled.
 */
@Component
public class AgentHarnessOperatorInitializer
    implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger =
        LoggerFactory.getLogger(AgentHarnessOperatorInitializer.class);

    @Autowired(required = false)
    private AgentHarnessOperator operator;

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        if (this.operator == null) {
            return;
        }
        try {
            this.operator.init();
        } catch (RuntimeException e) {
            logger.error("Could not initialize the agent-harness operator: {}", e.getMessage(), e);
        }
    }
}
