package org.vader.core.server.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start in {@link VaderMode#EVAL} unless sampling is pinned: a real local LLM with an
 * explicitly configured temperature and a fixed seed. Without that, a benchmark run is neither
 * repeatable nor attributable to the sampling it was made under.
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
        return this.temperature == null
            ? "spring.ai.ollama.chat.options.temperature is not set"
            : null;
    }

    private String seedProblem() {
        return this.seed == null ? "spring.ai.ollama.chat.options.seed is not set" : null;
    }
}
