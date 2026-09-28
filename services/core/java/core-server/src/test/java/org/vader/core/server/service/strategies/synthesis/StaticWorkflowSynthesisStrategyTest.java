package org.vader.core.server.service.strategies.synthesis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.vader.core.server.models.TaskOutcome;
import org.vader.core.server.models.WorkflowSynthesisRequest;

class StaticWorkflowSynthesisStrategyTest {

    private final StaticWorkflowSynthesisStrategy strategy = new StaticWorkflowSynthesisStrategy();

    @Test
    void synthesize_countsSucceededTasksAndNamesTheObjective() {
        var request = new WorkflowSynthesisRequest(
            "What does this spreadsheet show?",
            "Analyze the spreadsheet",
            List.of(
                new TaskOutcome("Read the file", true, "Found 3 sheets."),
                new TaskOutcome("Chart the trend", false, "No charting library.")));

        var result = this.strategy.synthesize(request);

        assertThat(result).isEqualTo("Completed 1 of 2 tasks for: Analyze the spreadsheet");
    }
}
