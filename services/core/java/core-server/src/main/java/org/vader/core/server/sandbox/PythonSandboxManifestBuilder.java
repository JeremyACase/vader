package org.vader.core.server.sandbox;

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
import org.vader.core.server.operators.AbstractOperator;

/**
 * Builds the Kubernetes {@link Deployment} and {@link Service} manifests that make up one Python
 * sandbox, using the fabric8 builder DSL.
 *
 * <p>The container runs {@code core-python-sandbox-server}'s own entrypoint. Its probes hit that
 * server's {@code /health} on the Service's {@code exec} port, so {@link AbstractOperator}'s
 * "Running" means the server is up; the Service routing to it lags slightly, which
 * {@code PythonSandboxService} waits out.</p>
 *
 * <p>The workspace is an {@code emptyDir} volume so staged files survive a container restart
 * (e.g. an OOM kill) for the pod's lifetime. They are lost if the pod is replaced, so core-server
 * re-stages missing files before each run of an attempt-owned sandbox.</p>
 *
 * <p>Every sandbox pod carries {@value #COMPONENT_LABEL}={@value #COMPONENT}, which the Helm
 * chart's sandbox NetworkPolicy selects to admit only core-server and deny the pod all egress.</p>
 */
@Component
public class PythonSandboxManifestBuilder {

    /** The label key the Helm chart's sandbox NetworkPolicy selects sandbox pods by. */
    public static final String COMPONENT_LABEL = "component";

    /** The {@link #COMPONENT_LABEL} value every sandbox pod carries. */
    public static final String COMPONENT = "vader-python-sandbox";

    private static final String CONTAINER_NAME = "sandbox";
    private static final String PORT_NAME = "exec";
    private static final int EXEC_PORT = 8888;
    private static final String HEALTH_PATH = "/health";
    private static final String WORKSPACE_VOLUME_NAME = "workspace";
    // Must match SANDBOX_WORKSPACE_DIR in core-python-sandbox-server's Dockerfile.
    private static final String WORKSPACE_MOUNT_PATH = "/workspace";
    private static final long RUN_AS_USER = 1000L;
    private static final String DEFAULT_IMAGE =
        "jeremyacase/vader-core-python-sandbox-server:latest";

    @Value("${vader.operators.python-sandbox.sandbox.image:" + DEFAULT_IMAGE + "}")
    private String image;

    // Without an explicit policy a "latest" tag defaults to Always, so a kind cluster would pull
    // from Docker Hub instead of using the image `kind load docker-image` staged.
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

    // Passed through as SANDBOX_LOG_LEVEL, whose Python level names match core-server's
    // logging.level.org.vader value, so sandbox verbosity follows core-server's.
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
        final Map<String, String> selectorLabels = Map.of("app", name);
        return new DeploymentBuilder()
            .withNewMetadata()
                .withName(name)
                .addToLabels("app", name)
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                    .withMatchLabels(selectorLabels)
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels(selectorLabels)
                        .addToLabels(COMPONENT_LABEL, COMPONENT)
                    .endMetadata()
                    .withNewSpec()
                        .withAutomountServiceAccountToken(false)
                        .withContainers(this.buildContainer())
                        .addNewVolume()
                            .withName(WORKSPACE_VOLUME_NAME)
                            .withNewEmptyDir()
                            .endEmptyDir()
                        .endVolume()
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
            .addNewVolumeMount()
                .withName(WORKSPACE_VOLUME_NAME)
                .withMountPath(WORKSPACE_MOUNT_PATH)
            .endVolumeMount()
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
