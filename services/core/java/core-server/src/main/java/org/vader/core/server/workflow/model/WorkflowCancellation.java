package org.vader.core.server.workflow.model;

import org.vader.common.model.vader.entity.WorkflowStatus;

/**
 * The outcome of a request to cancel a workflow.
 *
 * @param workflowId the workflow the request named
 * @param status its status afterwards: {@code CANCELLED}, or the terminal status it had already
 *     reached
 * @param cancelledAttempts how many attempts that were still open this request stopped
 */
public record WorkflowCancellation(
    String workflowId, WorkflowStatus status, int cancelledAttempts) {
}
