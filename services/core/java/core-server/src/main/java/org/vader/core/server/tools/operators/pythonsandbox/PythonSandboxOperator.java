package org.vader.core.server.tools.operators.pythonsandbox;

import io.fabric8.kubernetes.api.model.HasMetadata;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.tools.operators.AbstractOperator;
import org.vader.core.server.tools.operators.pythonsandbox.manifest.PythonSandboxManifestBuilder;

/**
 * The first concrete vader operator: manages the lifecycle of isolated Python sandbox pods.
 *
 * <p>Active only when {@code vader.operators.python-sandbox.enabled} is {@code true}. Startup and
 * shutdown are driven by {@link PythonSandboxOperatorInitializer} and {@link #onShutdown()}.</p>
 */
@Service
@ConditionalOnProperty(
    prefix = "vader.operators.python-sandbox",
    name = "enabled",
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
