package org.vader.core.server.orchestration;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vader.common.model.vader.dto.TaskPlan;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses the orchestrator's raw JSON into a {@link TaskPlan} and checks it against the
 * {@code TaskPlan} schema (jakarta bean validation) -- the first gate a plan passes, before
 * {@link TaskPlanStructuralValidator} checks its graph and the refinement critique reviews it.
 */
@Component
public class TaskPlanSchemaValidator {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Validator validator;

    /**
     * Parses and validates one orchestrator response.
     *
     * @param rawResponse the orchestrator's task plan, as JSON
     * @return the schema-valid task plan
     * @throws OrchestratorResponseException if the response is empty, unparseable, or fails the
     *     task-plan schema
     */
    public TaskPlan parseAndValidate(final String rawResponse) {

        if (Objects.isNull(rawResponse) || rawResponse.isBlank()) {
            throw new OrchestratorResponseException("Orchestrator returned an empty response.");
        }

        final TaskPlan taskPlan;
        try {
            taskPlan = this.objectMapper.readValue(rawResponse, TaskPlan.class);
        } catch (JacksonException e) {
            throw new OrchestratorResponseException(
                "Orchestrator response could not be parsed as a task plan.", e);
        }

        var violations = this.validator.validate(taskPlan);
        if (!violations.isEmpty()) {
            throw new OrchestratorResponseException(
                "Orchestrator response did not satisfy the task-plan schema: "
                    + describe(violations));
        }
        return taskPlan;
    }

    private static String describe(final Set<ConstraintViolation<TaskPlan>> violations) {
        return violations.stream()
            .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
            .sorted()
            .collect(Collectors.joining(", "));
    }
}
