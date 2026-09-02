package org.vader.core.server.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.TaskPlan;

class StaticLlmOrchestrationStrategyTest {

    private final StaticLlmOrchestrationStrategy strategy = new StaticLlmOrchestrationStrategy();

    @Test
    void orchestrate_returnsSchemaValidTaskPlanForAnyPrompt() throws Exception {
        var prompt = new ClientPrompt();
        prompt.setText("anything at all");

        var response = this.strategy.orchestrate(prompt);

        var taskPlan = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(response, TaskPlan.class);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(taskPlan);
            assertThat(violations).isEmpty();
        }
        assertThat(taskPlan.getObjective()).isNotBlank();
        assertThat(taskPlan.getTaskGraph().getTasks()).isNotEmpty();
    }
}
