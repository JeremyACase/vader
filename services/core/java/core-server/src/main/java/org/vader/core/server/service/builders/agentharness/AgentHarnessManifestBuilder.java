package org.vader.core.server.service.builders.agentharness;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vader.core.server.models.AgentHarnessSpec;
import org.vader.core.server.service.operators.agentharness.AgentHarnessNaming;

/**
 * Builds the Kubernetes {@link Job} manifest for a single agent-harness run.
 *
 * <p>{@code backoffLimit} is fixed at zero: a Job never retries at the Kubernetes level. Retries
 * are owned by {@code TaskGraphScheduler}, which dispatches a fresh assignment (a new
 * {@code TaskAttempt}, a new Job) rather than letting Kubernetes restart the same one -- keeping
 * exactly one Job per attempt, matching the harness's own {@code TaskId}/{@code AssignmentId}
 * identity.</p>
 */
@Component
public class AgentHarnessManifestBuilder {

    private static final String CONTAINER_NAME = "harness";
    private static final long RUN_AS_USER = 1000L;

    @Value("${vader.agent-harness.image:jeremyacase/vader-core-agent-harness:latest}")
    private String image;

    // Same concern core-server/core-ui's own Deployments already solve via
    // infrastructure.image.pullPolicy: without this, a "latest"-tagged image defaults to
    // Always, so a dev/kind cluster would try to pull from Docker Hub instead of using the
    // image `kind load docker-image` already staged locally.
    @Value("${vader.agent-harness.image-pull-policy:IfNotPresent}")
    private String imagePullPolicy;

    @Value("${vader.agent-harness.core-server-url:http://vader-core-server:8080}")
    private String coreServerUrl;

    // Passed straight through as RUST_LOG -- env_logger's own level names are case-insensitive,
    // so core-server's own logging.vader value (e.g. "INFO") works unchanged, keeping harness
    // log verbosity in step with core-server's without a second knob to keep in sync by hand.
    @Value("${vader.agent-harness.log-level:info}")
    private String logLevel;

    @Value("${vader.agent-harness.deadline-seconds:600}")
    private long deadlineSeconds;

    @Value("${vader.agent-harness.ttl-seconds-after-finished:3600}")
    private int ttlSecondsAfterFinished;

    @Value("${vader.agent-harness.resources.requests.cpu:50m}")
    private String cpuRequest;

    @Value("${vader.agent-harness.resources.requests.memory:32Mi}")
    private String memoryRequest;

    @Value("${vader.agent-harness.resources.limits.cpu:250m}")
    private String cpuLimit;

    @Value("${vader.agent-harness.resources.limits.memory:128Mi}")
    private String memoryLimit;

    /**
     * Builds the single-element manifest list (just the Job -- a harness needs no Service, since
     * nothing ever calls it inbound) for {@code spec}.
     *
     * @param spec the resolved task/assignment identity
     * @return the manifest to create
     */
    public List<HasMetadata> build(final AgentHarnessSpec spec) {
        return List.of(this.buildJob(spec));
    }

    /**
     * Builds the harness Job.
     *
     * @param spec the resolved task/assignment identity
     * @param name the resolved, unique Job name
     * @return the Job manifest
     */
    public Job buildJob(final AgentHarnessSpec spec, final String name) {
        final Map<String, String> podLabels = Map.of("app", name);
        return new JobBuilder()
            .withNewMetadata()
                .withName(name)
                .addToLabels("app", name)
            .endMetadata()
            .withNewSpec()
                .withBackoffLimit(0)
                .withTtlSecondsAfterFinished(this.ttlSecondsAfterFinished)
                .withActiveDeadlineSeconds(this.deadlineSeconds)
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels(podLabels)
                    .endMetadata()
                    .withNewSpec()
                        .withRestartPolicy("Never")
                        .withAutomountServiceAccountToken(false)
                        .withContainers(this.buildContainer(spec))
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();
    }

    private Job buildJob(final AgentHarnessSpec spec) {
        return this.buildJob(spec, AgentHarnessNaming.resolve(spec.assignmentId()));
    }

    private Container buildContainer(final AgentHarnessSpec spec) {
        return new ContainerBuilder()
            .withName(CONTAINER_NAME)
            .withImage(this.image)
            .withImagePullPolicy(this.imagePullPolicy)
            .addNewEnv().withName("TASK_ID").withValue(spec.taskId()).endEnv()
            .addNewEnv().withName("ASSIGNMENT_ID").withValue(spec.assignmentId()).endEnv()
            .addNewEnv().withName("CORE_SERVER_URL").withValue(this.coreServerUrl).endEnv()
            .addNewEnv().withName("RUST_LOG").withValue(this.logLevel).endEnv()
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
