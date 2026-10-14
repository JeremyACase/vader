package org.vader.core.server.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.config.VaderMode;

class LlmConfigurationControllerTest {

    private PromptTemplateRegistry promptTemplateRegistry;

    private LlmConfigurationController controller;

    @BeforeEach
    void setUp() {
        this.promptTemplateRegistry = PromptTemplateRegistryObjectMother.loaded();
        var modelNameResolver = new LlmModelNameResolver();
        ReflectionTestUtils.setField(modelNameResolver, "orchestratorType", "local");
        ReflectionTestUtils.setField(modelNameResolver, "localModel", "qwen2.5:7b");

        this.controller = new LlmConfigurationController();
        ReflectionTestUtils.setField(
            this.controller, "promptTemplateRegistry", this.promptTemplateRegistry);
        ReflectionTestUtils.setField(this.controller, "llmModelNameResolver", modelNameResolver);
        ReflectionTestUtils.setField(this.controller, "mode", VaderMode.EVAL);
        ReflectionTestUtils.setField(this.controller, "temperature", 0.0);
        ReflectionTestUtils.setField(this.controller, "seed", 42);
        ReflectionTestUtils.setField(this.controller, "contextTokens", 16384);
        ReflectionTestUtils.setField(this.controller, "maxOutputTokens", 2048);
    }

    @Test
    void configuration_reportsTheModelAndItsSampling() {
        var configuration = this.controller.configuration();

        assertThat(configuration.mode()).isEqualTo(VaderMode.EVAL);
        assertThat(configuration.model()).isEqualTo("qwen2.5:7b");
        assertThat(configuration.temperature()).isEqualTo(0.0);
        assertThat(configuration.seed()).isEqualTo(42);
        assertThat(configuration.contextTokens()).isEqualTo(16384);
        assertThat(configuration.maxOutputTokens()).isEqualTo(2048);
    }

    @Test
    void configuration_reportsEveryCallKindsPromptFingerprint() {
        var fingerprints = this.controller.configuration().promptFingerprints();

        assertThat(fingerprints).containsOnlyKeys(LlmRequestKind.values());
        Arrays.stream(LlmRequestKind.values()).forEach(kind -> assertThat(fingerprints.get(kind))
            .isEqualTo(this.promptTemplateRegistry.fingerprint(kind)));
    }
}
