package org.vader.core.server.service.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the shared fabric8 {@link KubernetesClient} every operator uses.
 *
 * <p>Registered whenever the operator subsystem is switched on via {@code vader.operators.enabled}
 * — on by default, since a normal deployment expects to reach the Kubernetes API server; set it to
 * {@code false} where no cluster is reachable (unit/integration test runs, CI). Individual
 * operators are then toggled with their own {@code vader.operators.<name>.enabled} flag; enabling
 * any of them requires the master flag as well.</p>
 */
@Configuration
@ConditionalOnProperty(
    prefix = "vader.operators",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class KubernetesClientConfig {

    /**
     * Builds the Kubernetes client. fabric8 auto-detects in-cluster configuration and falls back
     * to the local kubeconfig for development; the builder is lazy, so no connection is opened
     * until the first API call.
     *
     * @return the client
     */
    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
