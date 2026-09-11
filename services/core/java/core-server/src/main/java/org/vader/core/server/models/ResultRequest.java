package org.vader.core.server.models;

import org.vader.common.model.vader.entity.TaskAttemptStatus;

/**
 * Body of {@code POST /vader/core-server/agent/assignments/{assignmentId}/result}: the terminal
 * outcome of a harness run. {@code status} must be a terminal value ({@code SUCCEEDED},
 * {@code FAILED}, {@code TIMED_OUT}, or {@code STALLED}).
 *
 * @param status the terminal outcome
 * @param output the task's output, when {@code status} is {@code SUCCEEDED}
 * @param failureReason why the attempt did not succeed, when {@code status} is not
 *     {@code SUCCEEDED}
 */
public record ResultRequest(TaskAttemptStatus status, String output, String failureReason) {
}
