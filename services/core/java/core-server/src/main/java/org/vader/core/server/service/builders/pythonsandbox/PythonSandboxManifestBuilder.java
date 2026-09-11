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

/**
 * Builds the Kubernetes {@link Deployment} and {@link Service} manifests that make up one Python
 * sandbox. Mirrors ubiquia's {@code BeliefStateDeploymentBuilder}, using the fabric8 builder DSL.
 *
 * <p>The sandbox container simply idles ({@code sleep infinity}); this cut manages lifecycle only,
 * so nothing runs code inside it yet. The Service and its {@code exec} port are created now so the
 * shape is stable when code-execution proxying is added later.</p>
 */
@Component
public class PythonSandboxManifestBuilder {

    private static final String CONTAINER_NAME = "sandbox";
    private static final String PORT_NAME = "exec";
    private static final int EXEC_PORT = 8888;
    private static final long RUN_AS_USER = 1000L;

    @Value("${vader.operators.python-sandbox.sandbox.image:python:3.12-slim}")
    private String image;

    @Value("${vader.operators.python-sandbox.sandbox.resources.requests.cpu:100m}")
    private String cpuRequest;

    @Value("${vader.operators.python-sandbox.sandbox.resources.requests.memory:128Mi}")
    private String memoryRequest;

    @Value("${vader.operators.python-sandbox.sandbox.resources.limits.cpu:500m}")
    private String cpuLimit;

    @Value("${vader.operators.python-sandbox.sandbox.resources.limits.memory:256Mi}")
    private String memoryLimit;

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
            .withCommand("sleep", "infinity")
            .addNewPort()
                .withName(PORT_NAME)
                .withContainerPort(EXEC_PORT)
            .endPort()
            .withNewResources()
                .addToRequests("cpu", new Quantity(this.cpuRequest))
                .addToRequests("memory", new Quantity(this.memoryRequest))
                .addToLimits("cpu", new Quantity(this.cpuLimit))
                .addToLimits("memory", new Quantity(this.memoryLimit))
            .endResources()
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
