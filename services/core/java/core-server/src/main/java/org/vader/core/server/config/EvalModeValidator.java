package org.vader.core.server.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start in {@link VaderMode#EVAL} unless sampling is pinned: a real local LLM at
 * temperature 0 with a fixed seed. Without that, two runs of the same benchmark differ by chance,
 * and a prompt change can't be told apart from noise.
 */
@Component
public class EvalModeValidator {

    @Value("${vader.mode:PROD}")
    private VaderMode mode;

    @Value("${vader.orchestrator.type:local}")
    private String orchestratorType;

    @Value("${spring.ai.ollama.chat.options.temperature:#{null}}")
    private Double temperature;

    @Value("${spring.ai.ollama.chat.options.seed:#{null}}")
    private Integer seed;

    /**
     * Fails startup when EVAL mode is set without pinned sampling.
     *
     * @throws IllegalStateException naming every unmet requirement
     */
    @PostConstruct
    void requirePinnedSampling() {
        var problems = this.mode == VaderMode.EVAL ? this.evalProblems() : List.<String>of();
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                "vader.mode=EVAL requires repeatable sampling, but " + String.join("; ", problems));
        }
    }

    private List<String> evalProblems() {
        return Stream.of(this.orchestratorProblem(), this.temperatureProblem(), this.seedProblem())
            .filter(Objects::nonNull)
            .toList();
    }

    private String orchestratorProblem() {
        return "local".equals(this.orchestratorType)
            ? null
            : "vader.orchestrator.type is " + this.orchestratorType + ", not local";
    }

    private String temperatureProblem() {
        return Objects.equals(this.temperature, 0.0)
            ? null
            : "spring.ai.ollama.chat.options.temperature is " + this.temperature + ", not 0";
    }

    private String seedProblem() {
        return this.seed == null ? "spring.ai.ollama.chat.options.seed is not set" : null;
    }
}
