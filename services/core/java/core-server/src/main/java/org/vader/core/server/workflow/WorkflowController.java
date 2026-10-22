package org.vader.core.server.workflow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.core.server.workflow.model.WorkflowCancellation;

/** Actions on a workflow in progress. Reading workflows goes through the query API instead. */
@RestController
@RequestMapping("/vader/core-server/workflow")
public class WorkflowController {

    @Autowired
    private WorkflowCancellationService workflowCancellationService;

    /**
     * Cancels a workflow that is still in progress; one that already settled is left as it is.
     *
     * @param workflowId the workflow to cancel
     * @return its status afterwards and how many open attempts were stopped
     */
    @PostMapping("/{workflowId}/cancel")
    public WorkflowCancellation cancel(@PathVariable final String workflowId) {
        return this.workflowCancellationService.cancel(workflowId);
    }
}
