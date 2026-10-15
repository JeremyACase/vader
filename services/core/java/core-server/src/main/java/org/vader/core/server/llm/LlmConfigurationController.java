package org.vader.core.server.llm;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.config.VaderMode;
import org.vader.core.server.llm.model.LlmConfiguration;
import org.vader.core.server.llm.model.PromptSet;

/**
 * Read-only view of the LLM configuration in effect: the model, its sampling options, every
 * call kind's prompt fingerprint and the templates behind it. A benchmark runner records it
 * with each result, so a result is only ever compared with one produced by the same prompts,
 * model and sampling.
 */
@RestController
@RequestMapping("/vader/core-server/llm")
public class LlmConfigurationController {

    @Autowired
    private PromptTemplateRegistry promptTemplateRegistry;

    @Autowired
    private LlmModelNameResolver llmModelNameResolver;

    @Value("${vader.mode:PROD}")
    private VaderMode mode;

    @Value("${spring.ai.ollama.chat.options.temperature:#{null}}")
    private Double temperature;

    @Value("${spring.ai.ollama.chat.options.seed:#{null}}")
    private Integer seed;

    @Value("${spring.ai.ollama.chat.options.num-ctx:#{null}}")
    private Integer contextTokens;

    @Value("${spring.ai.ollama.chat.options.num-predict:#{null}}")
    private Integer maxOutputTokens;

    /**
     * The LLM configuration in effect.
     *
     * @return the mode, model, sampling options and prompt fingerprints
     */
    @GetMapping("/configuration")
    public LlmConfiguration configuration() {
        var fingerprints = Arrays.stream(LlmRequestKind.values())
            .collect(Collectors.toMap(
                Function.identity(), this.promptTemplateRegistry::fingerprint,
                (first, second) -> first, () -> new EnumMap<>(LlmRequestKind.class)));
        return new LlmConfiguration(this.mode, this.llmModelNameResolver.modelName(),
            this.temperature, this.seed, this.contextTokens, this.maxOutputTokens, fingerprints);
    }

    /**
     * Every call kind's prompt templates with their fingerprint: the wording behind each
     * fingerprint {@link #configuration()} reports.
     *
     * @return each call kind's fingerprint and template texts
     */
    @GetMapping("/prompts")
    public Map<LlmRequestKind, PromptSet> prompts() {
        return Arrays.stream(LlmRequestKind.values())
            .collect(Collectors.toMap(Function.identity(), this::promptSet,
                (first, second) -> first, () -> new EnumMap<>(LlmRequestKind.class)));
    }

    private PromptSet promptSet(final LlmRequestKind kind) {
        return new PromptSet(this.promptTemplateRegistry.fingerprint(kind),
            this.promptTemplateRegistry.templates(kind));
    }
}
