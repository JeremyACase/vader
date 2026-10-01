package org.vader.core.server.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskPlanDtoToEntityMapper;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.events.EventPublishingFacade;
import org.vader.core.server.intake.ClientPromptRepository;
import org.vader.core.server.workflow.TaskUpdateService;
import org.vader.core.server.workflow.WorkflowDecomposedEvent;
import org.vader.core.server.workflow.WorkflowRepository;

/**
 * Turns an already-persisted client prompt into a persisted problem decomposition.
 *
 * <p>Called by {@code ClientPromptInbox} once a prompt has been popped from the queue. The prompt
 * (and any attached files) were stored by {@code ClientPromptIntakeService} when the request was
 * accepted. {@link TaskPlanRefinementService} produces a validated, critiqued plan <em>before</em>
 * a workflow is written, so a malformed response leaves no workflow behind. On success the task
 * plan, its task graph and every task are persisted as a single graph hanging off a new
 * {@link WorkflowEntity}, with the plan associated back to that workflow.</p>
 */
@Service
public class OrchestratorAgentService {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorAgentService.class);

    @Autowired
    private ClientPromptRepository clientPromptRepository;

    @Autowired
    private ClientPromptDtoMapper clientPromptDtoMapper;

    @Autowired
    private TaskPlanRefinementService taskPlanRefinementService;

    @Autowired
    private TaskPlanDtoToEntityMapper taskPlanDtoToEntityMapper;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private TaskUpdateService taskUpdateService;

    @Autowired
    private EventPublishingFacade eventPublishingFacade;

    /**
     * Decomposes a persisted client prompt into a persisted task plan under a new workflow.
     *
     * @param clientPromptId the id of the persisted prompt to decompose
     * @return the persisted workflow, with its task plan attached
     * @throws OrchestratorResponseException if the orchestrator response is missing, unparseable,
     *     or fails the task-plan schema
     */
    @Transactional
    public WorkflowEntity decompose(final String clientPromptId) {

        var clientPrompt = this.clientPromptRepository.findById(clientPromptId).orElseThrow();
        var promptDto = this.clientPromptDtoMapper.map(clientPrompt);
        var taskPlanDto = this.taskPlanRefinementService.refine(promptDto);

        var workflow = new WorkflowEntity();
        workflow.setClientPrompt(clientPrompt);

        var taskPlan = this.taskPlanDtoToEntityMapper.map(taskPlanDto);
        taskPlan.setWorkflow(workflow);
        workflow.setTaskPlan(taskPlan);

        var saved = this.workflowRepository.save(workflow);
        logger.info(
            "Persisted workflow {} with task plan {} ({} root tasks)",
            saved.getId(),
            saved.getTaskPlan().getId(),
            saved.getTaskPlan().getTaskGraph().getTasks().size());
        this.recordTaskCreatedUpdates(saved.getTaskPlan().getTaskGraph().getTasks());
        this.eventPublishingFacade.publish(new WorkflowDecomposedEvent(saved.getId()));
        return saved;
    }

    /**
     * Records a {@link TaskUpdateType#CREATED} update for every task in the graph, including
     * nested subtasks, so a task's history starts the moment it exists rather than only ever
     * accumulating entries once something happens to it.
     *
     * @param rootTasks the task graph's root tasks
     */
    private void recordTaskCreatedUpdates(final Set<TaskEntity> rootTasks) {
        flattenTasks(rootTasks).forEach(task ->
            this.taskUpdateService.record(
                task, null, TaskUpdateType.CREATED, task.getDescription(),
                TaskUpdateAuthor.SYSTEM));
    }

    private static List<TaskEntity> flattenTasks(final Set<TaskEntity> tasks) {
        var flat = new ArrayList<TaskEntity>();
        tasks.forEach(task -> {
            flat.add(task);
            flat.addAll(flattenTasks(task.getSubTasks()));
        });
        return flat;
    }
}
