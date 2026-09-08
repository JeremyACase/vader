package org.vader.core.server.tools.operators;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@EnableKubernetesMockClient(crud = true)
class AbstractOperatorTest {

    private static final String NAMESPACE = "test-ns";
    private static final String OWNER = "owner-dep";

    private KubernetesClient client;

    private TestOperator operator(final int retries) {
        var operator = new TestOperator(retries);
        ReflectionTestUtils.setField(operator, "client", this.client);
        ReflectionTestUtils.setField(operator, "namespace", NAMESPACE);
        return operator;
    }

    private void createOwnerDeployment() {
        var owner = new DeploymentBuilder()
            .withNewMetadata()
                .withName(OWNER)
                .withUid("owner-uid")
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
                .withNewSelector().withMatchLabels(Map.of("app", OWNER)).endSelector()
                .withNewTemplate()
                    .withNewMetadata().addToLabels("app", OWNER).endMetadata()
                    .withNewSpec()
                        .addNewContainer().withName("c").withImage("busybox").endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();
        this.client.apps().deployments().inNamespace(NAMESPACE).resource(owner).create();
    }

    private Deployment fetchDeployment(final String name) {
        return this.client.apps().deployments().inNamespace(NAMESPACE).withName(name).get();
    }

    @Test
    void reconcile_createsLabelledDeploymentAndServiceWithOwnerReference() {
        this.createOwnerDeployment();
        var operator = this.operator(3);
        operator.init();

        var managed = operator.reconcile("sbx-a");

        assertThat(managed.name()).isEqualTo("sbx-a");
        var deployment = this.fetchDeployment("sbx-a");
        assertThat(deployment.getMetadata().getLabels())
            .containsEntry(OperatorLabels.OPERATOR, "test-op")
            .containsEntry(OperatorLabels.MANAGED_BY, OperatorLabels.MANAGED_BY_VALUE);
        assertThat(deployment.getMetadata().getOwnerReferences()).hasSize(1);
        assertThat(deployment.getMetadata().getOwnerReferences().get(0).getName()).isEqualTo(OWNER);
        assertThat(this.client.services().inNamespace(NAMESPACE).withName("sbx-a").get())
            .isNotNull();
    }

    @Test
    void reconcile_isIdempotent() {
        this.createOwnerDeployment();
        var operator = this.operator(3);
        operator.init();

        operator.reconcile("sbx-a");
        operator.reconcile("sbx-a");

        assertThat(operator.list()).hasSize(1);
    }

    @Test
    void deleteAll_removesOnlyResourcesThisOperatorManages() {
        this.createOwnerDeployment();
        var operator = this.operator(3);
        operator.init();
        operator.reconcile("sbx-a");

        var unrelated = new DeploymentBuilder()
            .withNewMetadata().withName("not-ours").endMetadata()
            .withNewSpec()
                .withReplicas(1)
                .withNewSelector().withMatchLabels(Map.of("app", "x")).endSelector()
                .withNewTemplate()
                    .withNewMetadata().addToLabels("app", "x").endMetadata()
                    .withNewSpec()
                        .addNewContainer().withName("c").withImage("busybox").endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();
        this.client.apps().deployments().inNamespace(NAMESPACE).resource(unrelated).create();

        operator.deleteAll();

        assertThat(this.fetchDeployment("sbx-a")).isNull();
        assertThat(this.fetchDeployment("not-ours")).isNotNull();
    }

    @Test
    void teardown_whenOwnerDeploymentIsGone_deletesEveryManagedResource() {
        this.createOwnerDeployment();
        var operator = this.operator(3);
        operator.init();
        operator.reconcile("sbx-a");

        this.client.apps().deployments().inNamespace(NAMESPACE).withName(OWNER).delete();
        operator.teardown();

        assertThat(operator.list()).isEmpty();
    }

    @Test
    void init_whenOwnerDeploymentNeverAppears_completesWithoutOwnerReference() {
        var operator = this.operator(2);

        operator.init();

        assertThat(operator.owner()).isNull();
        operator.reconcile("sbx-a");
        assertThat(this.fetchDeployment("sbx-a").getMetadata().getOwnerReferences()).isEmpty();
    }

    private static final class TestOperator extends AbstractOperator<String> {

        private final int retries;

        TestOperator(final int retries) {
            this.retries = retries;
        }

        Deployment owner() {
            return this.cachedOwnerDeployment();
        }

        @Override
        protected int maxOwnerLookupRetries() {
            return this.retries;
        }

        @Override
        protected Duration ownerLookupBackoff() {
            return Duration.ofMillis(10);
        }

        @Override
        protected String operatorName() {
            return "test-op";
        }

        @Override
        protected String ownerDeploymentName() {
            return OWNER;
        }

        @Override
        protected String resourceName(final String spec) {
            return spec;
        }

        @Override
        protected List<HasMetadata> buildManifests(final String spec) {
            var deployment = new DeploymentBuilder()
                .withNewMetadata().withName(spec).endMetadata()
                .withNewSpec()
                    .withReplicas(1)
                    .withNewSelector().withMatchLabels(Map.of("app", spec)).endSelector()
                    .withNewTemplate()
                        .withNewMetadata().addToLabels("app", spec).endMetadata()
                        .withNewSpec()
                            .addNewContainer().withName("c").withImage("busybox").endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
            var service = new ServiceBuilder()
                .withNewMetadata().withName(spec).endMetadata()
                .withNewSpec()
                    .addNewPort().withPort(80).endPort()
                .endSpec()
                .build();
            return List.of(deployment, service);
        }
    }
}
