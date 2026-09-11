package org.vader.core.server.service.operators;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.vader.core.server.models.ManagedResource;
import org.vader.core.server.service.operators.interfaces.InterfaceOperator;

/**
 * Base class for vader's Kubernetes operators, ported and de-duplicated from ubiquia's
 * hand-rolled {@code BeliefStateOperator} and {@code ComponentOperator}.
 *
 * <p>It owns everything the two ubiquia operators had in common: caching the owning Deployment on
 * startup (with retry), watching for the operator's own deletion to cascade-clean managed
 * resources, stamping ownership labels and an owner reference on everything it creates, and
 * scoping list and delete operations to those labels. Subclasses supply only the operator name,
 * its owning Deployment name, and the manifests for a given specification.</p>
 *
 * <p>The shared Kubernetes client and namespace are injected here and inherited by the concrete
 * subclass, which is the Spring bean and drives {@link #init()} on application readiness and
 * {@link #teardown()} on shutdown.</p>
 *
 * @param <S> the specification type describing a single desired unit of managed infrastructure
 */
public abstract class AbstractOperator<S> implements InterfaceOperator<S> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractOperator.class);

    @Autowired
    protected KubernetesClient client;

    @Value("${vader.kubernetes.namespace:default}")
    protected String namespace;

    private Deployment ownerDeployment;
    private Watch selfDeletionWatch;

    /**
     * Returns the stable name of this operator, used as the value of the
     * {@link OperatorLabels#OPERATOR} label on every resource it manages.
     *
     * @return the operator name, e.g. {@code "python-sandbox"}
     */
    protected abstract String operatorName();

    /**
     * Returns the name of the Deployment that runs this operator's process. The operator watches
     * it and cascade-deletes every managed resource if it disappears.
     *
     * @return the owning Deployment name
     */
    protected abstract String ownerDeploymentName();

    /**
     * Returns the managed resource name for {@code spec}. The name must be a valid DNS-1123 label
     * and must be deterministic for a given spec, since it is used both to look up an existing
     * resource and to name a new one.
     *
     * @param spec the specification
     * @return the managed resource name
     */
    protected abstract String resourceName(S spec);

    /**
     * Builds the Kubernetes objects that make up the resource for {@code spec}. The list is
     * expected to contain exactly one Deployment named {@link #resourceName(Object)}, optionally
     * followed by a Service of the same name.
     *
     * @param spec the specification
     * @return the manifests to create
     */
    protected abstract List<HasMetadata> buildManifests(S spec);

    @Override
    public void init() {
        logger.info("Initializing '{}' operator (namespace={})...",
            this.operatorName(), this.namespace);
        this.cacheOwnerDeployment(0);
        this.startSelfDeletionWatch();
        logger.info("...'{}' operator initialized.", this.operatorName());
    }

    @Override
    public void teardown() {
        logger.info("Tearing down '{}' operator...", this.operatorName());
        if (Objects.nonNull(this.selfDeletionWatch)) {
            this.selfDeletionWatch.close();
        }
        if (this.ownerDeploymentIsGone()) {
            logger.info("...owner Deployment '{}' is gone; deleting managed resources...",
                this.ownerDeploymentName());
            this.deleteAll();
        }
    }

    @Override
    public ManagedResource reconcile(final S spec) {
        final var name = this.resourceName(spec);
        final var existing = this.deployment(name);
        if (Objects.nonNull(existing)) {
            logger.info("...'{}' already exists for operator '{}'; not creating.",
                name, this.operatorName());
            return this.toManagedResource(existing);
        }

        logger.info("Creating managed resources '{}' for operator '{}'...",
            name, this.operatorName());
        for (final var manifest : this.buildManifests(spec)) {
            this.stampManaged(manifest);
            this.client.resource(manifest).inNamespace(this.namespace).create();
            logger.info("...created {} '{}'.",
                manifest.getKind(), manifest.getMetadata().getName());
        }
        return this.toManagedResource(this.deployment(name));
    }

    @Override
    public void delete(final String name) {
        logger.info("Deleting managed resources '{}' for operator '{}'...",
            name, this.operatorName());
        this.client.apps().deployments()
            .inNamespace(this.namespace)
            .withName(name)
            .delete();
        this.client.services()
            .inNamespace(this.namespace)
            .withName(name)
            .delete();
    }

    @Override
    public void deleteAll() {
        logger.info("Deleting all resources managed by operator '{}'...", this.operatorName());
        this.client.apps().deployments()
            .inNamespace(this.namespace)
            .withLabel(OperatorLabels.OPERATOR, this.operatorName())
            .delete();
        this.client.services()
            .inNamespace(this.namespace)
            .withLabel(OperatorLabels.OPERATOR, this.operatorName())
            .delete();
    }

    @Override
    public List<ManagedResource> list() {
        final var deployments = this.client.apps().deployments()
            .inNamespace(this.namespace)
            .withLabel(OperatorLabels.OPERATOR, this.operatorName())
            .list()
            .getItems();

        final var managed = new ArrayList<ManagedResource>(deployments.size());
        for (final var deployment : deployments) {
            managed.add(this.toManagedResource(deployment));
        }
        return managed;
    }

    /**
     * How many extra times to retry fetching the owner Deployment on startup before giving up and
     * running without owner references. Overridable for tests.
     *
     * @return the retry count
     */
    protected int maxOwnerLookupRetries() {
        return 10;
    }

    /**
     * How long to wait between owner-Deployment lookup attempts. Overridable for tests.
     *
     * @return the backoff duration
     */
    protected Duration ownerLookupBackoff() {
        return Duration.ofSeconds(1);
    }

    /**
     * Returns the cached owning Deployment, or {@code null} if it could not be fetched on startup.
     * Subclasses may use it to inherit spec (image pull secrets, node selector, ...) from the
     * operator's own pod.
     *
     * @return the owner Deployment, or {@code null}
     */
    protected Deployment cachedOwnerDeployment() {
        return this.ownerDeployment;
    }

    private Deployment deployment(final String name) {
        return this.client.apps().deployments()
            .inNamespace(this.namespace)
            .withName(name)
            .get();
    }

    private void stampManaged(final HasMetadata manifest) {
        final var metadata = manifest.getMetadata();

        final var labels = new HashMap<String, String>();
        if (Objects.nonNull(metadata.getLabels())) {
            labels.putAll(metadata.getLabels());
        }
        labels.put(OperatorLabels.MANAGED_BY, OperatorLabels.MANAGED_BY_VALUE);
        labels.put(OperatorLabels.OPERATOR, this.operatorName());
        metadata.setLabels(labels);

        if (Objects.nonNull(this.ownerDeployment)) {
            metadata.setOwnerReferences(List.of(this.ownerReference()));
        }
    }

    private OwnerReference ownerReference() {
        return new OwnerReferenceBuilder()
            .withApiVersion("apps/v1")
            .withKind("Deployment")
            .withName(this.ownerDeployment.getMetadata().getName())
            .withUid(this.ownerDeployment.getMetadata().getUid())
            .withController(false)
            .withBlockOwnerDeletion(false)
            .build();
    }

    private void cacheOwnerDeployment(final int attempt) {
        final var deployment = this.deployment(this.ownerDeploymentName());
        if (Objects.nonNull(deployment)) {
            this.ownerDeployment = deployment;
            logger.info("...cached owner Deployment '{}'.", this.ownerDeploymentName());
            return;
        }

        if (attempt >= this.maxOwnerLookupRetries()) {
            logger.warn("Could not find owner Deployment '{}' after {} attempts; operator will "
                    + "run without owner references.",
                this.ownerDeploymentName(), this.maxOwnerLookupRetries());
            return;
        }

        try {
            Thread.sleep(this.ownerLookupBackoff().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        this.cacheOwnerDeployment(attempt + 1);
    }

    private void startSelfDeletionWatch() {
        this.selfDeletionWatch = this.client.apps().deployments()
            .inNamespace(this.namespace)
            .withName(this.ownerDeploymentName())
            .watch(new Watcher<Deployment>() {
                @Override
                public void eventReceived(final Action action, final Deployment resource) {
                    if (action == Action.DELETED) {
                        logger.info("Owner Deployment '{}' was deleted; cascading cleanup of "
                                + "operator '{}' resources...",
                            ownerDeploymentName(), operatorName());
                        deleteAll();
                    }
                }

                @Override
                public void onClose(final WatcherException cause) {
                    if (Objects.nonNull(cause)) {
                        logger.warn("Self-deletion watch for operator '{}' closed: {}",
                            operatorName(), cause.getMessage());
                    }
                }
            });
    }

    private boolean ownerDeploymentIsGone() {
        try {
            return Objects.isNull(this.deployment(this.ownerDeploymentName()));
        } catch (KubernetesClientException e) {
            logger.warn("Could not determine whether owner Deployment '{}' still exists: {}",
                this.ownerDeploymentName(), e.getMessage());
            return false;
        }
    }

    private ManagedResource toManagedResource(final Deployment deployment) {
        if (Objects.isNull(deployment)) {
            return null;
        }
        final var status = deployment.getStatus();
        final var ready = Objects.nonNull(status)
            && Objects.nonNull(status.getReadyReplicas())
            && status.getReadyReplicas() > 0;
        return new ManagedResource(
            deployment.getMetadata().getName(),
            deployment.getMetadata().getNamespace(),
            ready ? "Running" : "Pending",
            deployment.getMetadata().getLabels());
    }
}
