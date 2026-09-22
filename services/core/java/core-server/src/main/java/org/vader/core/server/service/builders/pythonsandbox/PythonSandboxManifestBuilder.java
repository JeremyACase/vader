package org.vader.core.server.service.builders.pythonsandbox;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vader.core.server.service.operators.AbstractOperator;

/**
 * Builds the Kubernetes {@link Deployment} and {@link Service} manifests that make up one Python
 * sandbox. Mirrors ubiquia's {@code BeliefStateDeploymentBuilder}, using the fabric8 builder DSL.
 *
 * <p>The sandbox container runs {@code core-python-sandbox-server}'s own image entrypoint --
 * a small HTTP server exposing {@code /execute} -- so no command override is needed here. Its
 * readiness and liveness probes hit that server's {@code /health} endpoint on the same
 * {@code exec} port the Service exposes, so {@link AbstractOperator}'s "Running" phase means the
 * server can actually accept requests, not just that the container process started.</p>
 */
@Component
public class PythonSandboxManifestBuilder {

    private static final String CONTAINER_NAME = "sandbox";
    private static final String PORT_NAME = "exec";
    private static final int EXEC_PORT = 8888;
    private static final String HEALTH_PATH = "/health";
    private static final long RUN_AS_USER = 1000L;
    private static final String DEFAULT_IMAGE =
        "jeremyacase/vader-core-python-sandbox-server:latest";

    @Value("${vader.operators.python-sandbox.sandbox.image:" + DEFAULT_IMAGE + "}")
    private String image;

    // Same concern core-server/core-ui's own Deployments already solve via
    // infrastructure.image.pullPolicy: without this, a "latest"-tagged image defaults to
    // Always, so a dev/kind cluster would try to pull from Docker Hub instead of using the
    // image `kind load docker-image` already staged locally.
    @Value("${vader.operators.python-sandbox.sandbox.image-pull-policy:IfNotPresent}")
    private String imagePullPolicy;

    @Value("${vader.operators.python-sandbox.sandbox.resources.requests.cpu:100m}")
    private String cpuRequest;

    @Value("${vader.operators.python-sandbox.sandbox.resources.requests.memory:128Mi}")
    private String memoryRequest;

    @Value("${vader.operators.python-sandbox.sandbox.resources.limits.cpu:500m}")
    private String cpuLimit;

    @Value("${vader.operators.python-sandbox.sandbox.resources.limits.memory:256Mi}")
    private String memoryLimit;

    @Value("${vader.operators.python-sandbox.sandbox.exec-timeout-seconds:30}")
    private String execTimeoutSeconds;

    // Passed straight through as SANDBOX_LOG_LEVEL, read by logging_config.configure_logging();
    // Python's stdlib logging level names (INFO, DEBUG, ...) match core-server's own
    // logging.level.org.vader value unchanged, the same reasoning
    // AgentHarnessManifestBuilder documents for RUST_LOG.
    @Value("${vader.operators.python-sandbox.sandbox.log-level:INFO}")
    private String logLevel;

    /**
     * Builds both manifests for the sandbox named {@code name}.
     *
     * @param name the resolved sandbox name
     * @return the Deployment followed by the Service
     */
    public List<HasMetadata> build(final String name) {
        return List.of(this.buildDeployment(name), this.buildService(name));
    }

    /**
     * Builds the sandbox Deployment.
     *
     * @param name the resolved sandbox name
     * @return the Deployment manifest
     */
    public Deployment buildDeployment(final String name) {
        final Map<String, String> podLabels = Map.of("app", name);
        return new DeploymentBuilder()
            .withNewMetadata()
                .withName(name)
                .addToLabels("app", name)
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                    .withMatchLabels(podLabels)
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels(podLabels)
                    .endMetadata()
                    .withNewSpec()
                        .withAutomountServiceAccountToken(false)
                        .withContainers(this.buildContainer())
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();
    }

    /**
     * Builds the sandbox Service.
     *
     * @param name the resolved sandbox name
     * @return the Service manifest
     */
    public Service buildService(final String name) {
        return new ServiceBuilder()
            .withNewMetadata()
                .withName(name)
                .addToLabels("app", name)
            .endMetadata()
            .withNewSpec()
                .withType("ClusterIP")
                .withSelector(Map.of("app", name))
                .addNewPort()
                    .withName(PORT_NAME)
                    .withPort(EXEC_PORT)
                    .withTargetPort(new IntOrString(EXEC_PORT))
                .endPort()
            .endSpec()
            .build();
    }

    private Container buildContainer() {
        return new ContainerBuilder()
            .withName(CONTAINER_NAME)
            .withImage(this.image)
            .withImagePullPolicy(this.imagePullPolicy)
            .addNewPort()
                .withName(PORT_NAME)
                .withContainerPort(EXEC_PORT)
            .endPort()
            .addNewEnv()
                .withName("SANDBOX_EXEC_MAX_TIMEOUT_SECONDS")
                .withValue(this.execTimeoutSeconds)
            .endEnv()
            .addNewEnv()
                .withName("SANDBOX_LOG_LEVEL")
                .withValue(this.logLevel)
            .endEnv()
            .withNewResources()
                .addToRequests("cpu", new Quantity(this.cpuRequest))
                .addToRequests("memory", new Quantity(this.memoryRequest))
                .addToLimits("cpu", new Quantity(this.cpuLimit))
                .addToLimits("memory", new Quantity(this.memoryLimit))
            .endResources()
            .withNewReadinessProbe()
                .withNewHttpGet()
                    .withPath(HEALTH_PATH)
                    .withPort(new IntOrString(EXEC_PORT))
                .endHttpGet()
                .withInitialDelaySeconds(2)
                .withPeriodSeconds(5)
            .endReadinessProbe()
            .withNewLivenessProbe()
                .withNewHttpGet()
                    .withPath(HEALTH_PATH)
                    .withPort(new IntOrString(EXEC_PORT))
                .endHttpGet()
                .withInitialDelaySeconds(10)
                .withPeriodSeconds(15)
            .endLivenessProbe()
            .withNewSecurityContext()
                .withRunAsNonRoot(true)
                .withRunAsUser(RUN_AS_USER)
                .withAllowPrivilegeEscalation(false)
                .withNewCapabilities()
                    .withDrop("ALL")
                .endCapabilities()
            .endSecurityContext()
            .build();
    }
}
