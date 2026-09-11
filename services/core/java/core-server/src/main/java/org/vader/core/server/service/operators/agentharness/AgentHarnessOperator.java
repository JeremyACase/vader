package org.vader.core.server.service.operators.agentharness;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.models.AgentHarnessSpec;
import org.vader.core.server.models.ManagedResource;
import org.vader.core.server.service.builders.agentharness.AgentHarnessManifestBuilder;
import org.vader.core.server.service.operators.AbstractOperator;
import org.vader.core.server.service.operators.OperatorLabels;

/**
 * Manages the lifecycle of agent-harness Jobs: one Job per dispatched {@code TaskAttempt}.
 *
 * <p>{@link AbstractOperator} is written around Deployment+Service (the shape every other
 * operator manages); a Job is a different resource kind with different status semantics, so
 * {@link #reconcile}, {@link #delete}, {@link #deleteAll}, and {@link #list} are overridden here
 * to operate on Jobs directly. Everything else -- owning-Deployment caching, the self-deletion
 * watch, and the overall {@link #init()}/{@link #teardown()} lifecycle -- is inherited unchanged:
 * those concern core-server's <em>own</em> Deployment (the owner, always a Deployment regardless
 * of what a given operator manages), not the kind being managed, so they need no override.</p>
 */
@Service
@ConditionalOnProperty(
    prefix = "vader.operators.agent-harness",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class AgentHarnessOperator extends AbstractOperator<AgentHarnessSpec> {

    private static final String OPERATOR_NAME = "agent-harness";

    private static final Logger logger = LoggerFactory.getLogger(AgentHarnessOperator.class);

    @Autowired
    private AgentHarnessManifestBuilder manifestBuilder;

    @Value("${vader.operators.agent-harness.owner-deployment-name:vader-core-server}")
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
    protected String resourceName(final AgentHarnessSpec spec) {
        return AgentHarnessNaming.resolve(spec.assignmentId());
    }

    @Override
    protected List<HasMetadata> buildManifests(final AgentHarnessSpec spec) {
        return this.manifestBuilder.build(spec);
    }

    @Override
    public ManagedResource reconcile(final AgentHarnessSpec spec) {
        final var name = this.resourceName(spec);
        final var existing = this.job(name);
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
        return this.toManagedResource(this.job(name));
    }

    @Override
    public void delete(final String name) {
        logger.info("Deleting managed resources '{}' for operator '{}'...",
            name, this.operatorName());
        this.client.batch().v1().jobs()
            .inNamespace(this.namespace)
            .withName(name)
            .delete();
    }

    @Override
    public void deleteAll() {
        logger.info("Deleting all resources managed by operator '{}'...", this.operatorName());
        this.client.batch().v1().jobs()
            .inNamespace(this.namespace)
            .withLabel(OperatorLabels.OPERATOR, this.operatorName())
            .delete();
    }

    @Override
    public List<ManagedResource> list() {
        final var jobs = this.client.batch().v1().jobs()
            .inNamespace(this.namespace)
            .withLabel(OperatorLabels.OPERATOR, this.operatorName())
            .list()
            .getItems();

        final var managed = new ArrayList<ManagedResource>(jobs.size());
        for (final var job : jobs) {
            managed.add(this.toManagedResource(job));
        }
        return managed;
    }

    private Job job(final String name) {
        return this.client.batch().v1().jobs()
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

        if (Objects.nonNull(this.cachedOwnerDeployment())) {
            metadata.setOwnerReferences(List.of(this.ownerReference()));
        }
    }

    private OwnerReference ownerReference() {
        final var owner = this.cachedOwnerDeployment();
        return new OwnerReferenceBuilder()
            .withApiVersion("apps/v1")
            .withKind("Deployment")
            .withName(owner.getMetadata().getName())
            .withUid(owner.getMetadata().getUid())
            .withController(false)
            .withBlockOwnerDeletion(false)
            .build();
    }

    private ManagedResource toManagedResource(final Job job) {
        if (Objects.isNull(job)) {
            return null;
        }
        return new ManagedResource(
            job.getMetadata().getName(),
            job.getMetadata().getNamespace(),
            this.phaseOf(job.getStatus()),
            job.getMetadata().getLabels());
    }

    private String phaseOf(final JobStatus status) {
        if (Objects.isNull(status)) {
            return "Pending";
        }
        if (isPositive(status.getSucceeded())) {
            return "Succeeded";
        }
        if (isPositive(status.getFailed())) {
            return "Failed";
        }
        if (isPositive(status.getActive())) {
            return "Running";
        }
        return "Pending";
    }

    private static boolean isPositive(final Integer value) {
        return Objects.nonNull(value) && value > 0;
    }
}
