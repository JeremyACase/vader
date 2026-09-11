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
        ReflectionTestUtils.setField(this.builder, "cpuRequest", "100m");
        ReflectionTestUtils.setField(this.builder, "memoryRequest", "128Mi");
        ReflectionTestUtils.setField(this.builder, "cpuLimit", "500m");
        ReflectionTestUtils.setField(this.builder, "memoryLimit", "256Mi");
    }

    @Test
    void buildDeployment_runsAnIdlingHardenedContainer() {
        Deployment deployment = this.builder.buildDeployment("vader-sandbox-a");

        assertThat(deployment.getMetadata().getName()).isEqualTo("vader-sandbox-a");
        assertThat(deployment.getSpec().getReplicas()).isEqualTo(1);

        var container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(container.getImage()).isEqualTo("python:3.12-slim");
        assertThat(container.getCommand()).containsExactly("sleep", "infinity");
        assertThat(container.getPorts().get(0).getContainerPort()).isEqualTo(8888);

        var security = container.getSecurityContext();
        assertThat(security.getRunAsNonRoot()).isTrue();
        assertThat(security.getAllowPrivilegeEscalation()).isFalse();
        assertThat(security.getCapabilities().getDrop()).containsExactly("ALL");

        assertThat(container.getResources().getLimits().get("cpu").toString()).isEqualTo("500m");
        assertThat(deployment.getSpec().getTemplate().getSpec().getAutomountServiceAccountToken())
            .isFalse();
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
