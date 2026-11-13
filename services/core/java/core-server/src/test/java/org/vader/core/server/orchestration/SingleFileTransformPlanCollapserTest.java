package org.vader.core.server.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.vader.core.server.orchestration.model.AttachedFile;
import org.vader.core.server.orchestration.model.LlmTaskPlan;

class SingleFileTransformPlanCollapserTest {

    private static final List<AttachedFile> ONE_FILE =
        List.of(new AttachedFile("quarterly_revenue.csv", "text/csv"));

    private static final List<LlmTaskPlan.LlmTask> SPLIT_TASKS = List.of(
        new LlmTaskPlan.LlmTask("Read Input CSV", "Read quarterly_revenue.csv.", List.of()),
        new LlmTaskPlan.LlmTask(
            "Calculate Monthly Revenue", "Split each quarter evenly.",
            List.of("Read Input CSV")),
        new LlmTaskPlan.LlmTask(
            "Write Monthly Revenue", "Save monthly_revenue.csv.",
            List.of("Calculate Monthly Revenue")));

    private static LlmTaskPlan planOf(
            final List<String> deliverables, final List<LlmTaskPlan.LlmTask> tasks) {
        return new LlmTaskPlan("reasoning", "convert it", deliverables, tasks);
    }

    private static List<AttachedFile> files(final int count) {
        return Instancio.ofList(AttachedFile.class).size(count).create();
    }

    @Test
    void collapse_mergesSplitSingleFileTransformIntoOneTask() {
        var plan = planOf(List.of("monthly_revenue.csv"), SPLIT_TASKS);

        var collapsed = SingleFileTransformPlanCollapser.collapse(plan, ONE_FILE);

        assertThat(collapsed.tasks()).hasSize(1);
        var task = collapsed.tasks().get(0);
        assertThat(task.title()).isEqualTo("Write Monthly Revenue");
        assertThat(task.description()).isEqualTo(
            "1. Read quarterly_revenue.csv.\n"
                + "2. Split each quarter evenly.\n"
                + "3. Save monthly_revenue.csv.");
        assertThat(task.dependsOn()).isEmpty();
        assertThat(collapsed.objective()).isEqualTo(plan.objective());
        assertThat(collapsed.reasoning()).isEqualTo(plan.reasoning());
    }

    @Test
    void collapse_treatsTheSameDeliverableNamedTwiceAsOne() {
        var plan = planOf(List.of("monthly_revenue.csv", " Monthly_Revenue.csv "), SPLIT_TASKS);

        assertThat(SingleFileTransformPlanCollapser.collapse(plan, ONE_FILE).tasks()).hasSize(1);
    }

    @Test
    void collapse_leavesPlanWithSeveralDeliverablesAlone() {
        var plan = planOf(List.of("monthly_revenue.csv", "chart.png"), SPLIT_TASKS);

        assertThat(SingleFileTransformPlanCollapser.collapse(plan, ONE_FILE)).isSameAs(plan);
    }

    @Test
    void collapse_leavesPlanWithNoDeliverableAlone() {
        var blankPlan = planOf(Arrays.asList(null, " "), SPLIT_TASKS);
        var nullPlan = planOf(null, SPLIT_TASKS);

        assertThat(SingleFileTransformPlanCollapser.collapse(blankPlan, ONE_FILE))
            .isSameAs(blankPlan);
        assertThat(SingleFileTransformPlanCollapser.collapse(nullPlan, ONE_FILE))
            .isSameAs(nullPlan);
    }

    @Test
    void collapse_leavesPlanWithoutExactlyOneAttachedFileAlone() {
        var plan = planOf(List.of("monthly_revenue.csv"), SPLIT_TASKS);

        assertThat(SingleFileTransformPlanCollapser.collapse(plan, files(0))).isSameAs(plan);
        assertThat(SingleFileTransformPlanCollapser.collapse(plan, files(2))).isSameAs(plan);
    }

    @Test
    void collapse_leavesSingleTaskPlanAlone() {
        var plan = planOf(List.of("monthly_revenue.csv"), SPLIT_TASKS.subList(0, 1));

        assertThat(SingleFileTransformPlanCollapser.collapse(plan, ONE_FILE)).isSameAs(plan);
    }
}
