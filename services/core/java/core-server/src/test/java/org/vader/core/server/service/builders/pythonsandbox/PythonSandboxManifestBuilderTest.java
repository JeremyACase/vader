package org.vader.core.server.service.builders.pythonsandbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PythonSandboxManifestBuilderTest {

    private PythonSandboxManifestBuilder builder;

    @BeforeEach
    void setUp() {
        this.builder = new PythonSandboxManifestBuilder();
        ReflectionTestUtils.setField(this.builder, "image", "python:3.12-slim");
        ReflectionTestUtils.setField(this.builder, "imagePullPolicy", "IfNotPresent");
        ReflectionTestUtils.setField(this.builder, "cpuRequest", "100m");
        ReflectionTestUtils.setField(this.builder, "memoryRequest", "128Mi");
        ReflectionTestUtils.setField(this.builder, "cpuLimit", "500m");
        ReflectionTestUtils.setField(this.builder, "memoryLimit", "256Mi");
        ReflectionTestUtils.setField(this.builder, "execTimeoutSeconds", "30");
        ReflectionTestUtils.setField(this.builder, "logLevel", "DEBUG");
    }

    @Test
    void buildDeployment_runsTheExecServerImageWithHealthProbes() {
        Deployment deployment = this.builder.buildDeployment("vader-sandbox-a");

        assertThat(deployment.getMetadata().getName()).isEqualTo("vader-sandbox-a");
        assertThat(deployment.getSpec().getReplicas()).isEqualTo(1);

        var container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(container.getImage()).isEqualTo("python:3.12-slim");
        assertThat(container.getImagePullPolicy()).isEqualTo("IfNotPresent");
        assertThat(container.getCommand()).isNullOrEmpty();
        assertThat(container.getPorts().get(0).getContainerPort()).isEqualTo(8888);
        assertThat(container.getEnv())
            .anyMatch(env -> "SANDBOX_EXEC_MAX_TIMEOUT_SECONDS".equals(env.getName())
                && "30".equals(env.getValue()));
        assertThat(container.getEnv())
            .anyMatch(env -> "SANDBOX_LOG_LEVEL".equals(env.getName())
                && "DEBUG".equals(env.getValue()));

        var readiness = container.getReadinessProbe();
        assertThat(readiness.getHttpGet().getPath()).isEqualTo("/health");
        assertThat(readiness.getHttpGet().getPort().getIntVal()).isEqualTo(8888);

        var liveness = container.getLivenessProbe();
        assertThat(liveness.getHttpGet().getPath()).isEqualTo("/health");
        assertThat(liveness.getHttpGet().getPort().getIntVal()).isEqualTo(8888);

        var security = container.getSecurityContext();
        assertThat(security.getRunAsNonRoot()).isTrue();
        assertThat(security.getAllowPrivilegeEscalation()).isFalse();
        assertThat(security.getCapabilities().getDrop()).containsExactly("ALL");

        assertThat(container.getResources().getLimits().get("cpu").toString()).isEqualTo("500m");
        assertThat(deployment.getSpec().getTemplate().getSpec().getAutomountServiceAccountToken())
            .isFalse();
    }

    @Test
    void buildDeployment_mountsTheWorkspaceOnAnEmptyDirSoItSurvivesContainerRestarts() {
        var podSpec = this.builder.buildDeployment("vader-sandbox-a").getSpec().getTemplate()
            .getSpec();

        var volume = podSpec.getVolumes().get(0);
        assertThat(volume.getEmptyDir()).isNotNull();
        var mount = podSpec.getContainers().get(0).getVolumeMounts().get(0);
        assertThat(mount.getName()).isEqualTo(volume.getName());
        assertThat(mount.getMountPath()).isEqualTo("/workspace");
    }

    @Test
    void buildService_exposesTheExecPortOverClusterIp() {
        Service service = this.builder.buildService("vader-sandbox-a");

        assertThat(service.getSpec().getType()).isEqualTo("ClusterIP");
        assertThat(service.getSpec().getSelector()).containsEntry("app", "vader-sandbox-a");
        assertThat(service.getSpec().getPorts().get(0).getPort()).isEqualTo(8888);
    }

    @Test
    void build_returnsDeploymentThenService() {
        var manifests = this.builder.build("vader-sandbox-a");

        assertThat(manifests).hasSize(2);
        assertThat(manifests.get(0)).isInstanceOf(Deployment.class);
        assertThat(manifests.get(1)).isInstanceOf(Service.class);
    }
}
