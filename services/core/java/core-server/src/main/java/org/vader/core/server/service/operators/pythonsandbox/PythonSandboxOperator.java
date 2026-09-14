package org.vader.core.server.service.operators.pythonsandbox;

import io.fabric8.kubernetes.api.model.HasMetadata;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.models.PythonSandboxSpec;
import org.vader.core.server.service.builders.pythonsandbox.PythonSandboxManifestBuilder;
import org.vader.core.server.service.initializers.pythonsandbox.PythonSandboxOperatorInitializer;
import org.vader.core.server.service.operators.AbstractOperator;

/**
 * The first concrete vader operator: manages the lifecycle of isolated Python sandbox pods.
 *
 * <p>Active only when both {@code vader.operators.enabled} (the master switch) and
 * {@code vader.operators.python-sandbox.enabled} are {@code true} -- listing both in one
 * condition, rather than this operator's flag alone, is what actually enforces the master switch
 * instead of just documenting it. Startup and shutdown are driven by
 * {@link PythonSandboxOperatorInitializer} and {@link #onShutdown()}.</p>
 */
@Service
@ConditionalOnProperty(
    name = {"vader.operators.enabled", "vader.operators.python-sandbox.enabled"},
    havingValue = "true",
    matchIfMissing = false)
public class PythonSandboxOperator extends AbstractOperator<PythonSandboxSpec> {

    private static final String OPERATOR_NAME = "python-sandbox";

    @Autowired
    private PythonSandboxManifestBuilder manifestBuilder;

    @Value("${vader.operators.python-sandbox.owner-deployment-name:vader-core-server}")
    private String ownerDeploymentName;

    /**
     * Tears the operator down on application shutdown.
     */
    @PreDestroy
    public void onShutdown() {
        this.teardown();
    }

    @Override
    protected String operatorName() {
        return OPERATOR_NAME;
    }

    @Override
    protected String ownerDeploymentName() {
        return this.ownerDeploymentName;
    }

    @Override
    protected String resourceName(final PythonSandboxSpec spec) {
        return spec.name();
    }

    @Override
    protected List<HasMetadata> buildManifests(final PythonSandboxSpec spec) {
        return this.manifestBuilder.build(spec.name());
    }
}
