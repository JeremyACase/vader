package org.vader.core.server.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.LlmRequestKind;

class PromptTemplateRegistryTest {

    private final PromptTemplateRegistry registry = PromptTemplateRegistryObjectMother.loaded();

    /** A registry whose every template has this same content, as though read from disk. */
    private static PromptTemplateRegistry loadedWith(final String content) {
        var resourceLoader = mock(ResourceLoader.class);
        when(resourceLoader.getResource(anyString())).thenAnswer(invocation ->
            new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
        var registry = new PromptTemplateRegistry();
        ReflectionTestUtils.setField(registry, "resourceLoader", resourceLoader);
        ReflectionTestUtils.invokeMethod(registry, "load");
        return registry;
    }

    @Test
    void render_leavesNoTemplateExpressionBehindInAnyTemplate() {
        Arrays.stream(PromptKey.values()).forEach(key -> {
            var variables = new PromptVariablesBuilder();
            key.variables().forEach(name -> variables.with(name, Instancio.create(String.class)));

            var rendered = this.registry.render(key, variables.build());

            assertThat(rendered).as(key.templateName()).isNotBlank().doesNotContain("<", ">");
        });
    }

    @Test
    void render_omitsAnOptionalSectionWhoseVariableIsNull() {
        var withoutRevision = this.registry.render(PromptKey.DECOMPOSITION);
        var withRevision = this.registry.render(PromptKey.DECOMPOSITION,
            new PromptVariablesBuilder().with("revisionGuidance", "Tasks overlap.").build());

        assertThat(withoutRevision).doesNotContain("A reviewer rejected");
        assertThat(withRevision).contains("A reviewer rejected").contains("Tasks overlap.");
    }

    @Test
    void render_alwaysUsesLfLineEndingsAndNoTrailingWhitespace() {
        var rendered = this.registry.render(PromptKey.EVALUATION_SYSTEM);

        assertThat(rendered).doesNotContain("\r").isEqualTo(rendered.stripTrailing());
    }

    @Test
    void render_rejectsUndeclaredVariables() {
        var variables = new PromptVariablesBuilder().with("notAVariable", "x").build();

        assertThatThrownBy(() -> this.registry.render(PromptKey.TASK_SYSTEM, variables))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("notAVariable");
    }

    @Test
    void load_failsOnTemplatesReferencingUndeclaredVariables() {
        assertThatThrownBy(() -> loadedWith("Hello <stranger>"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not match the variables");
    }

    @Test
    void load_failsOnMissingTemplates() {
        var resourceLoader = mock(ResourceLoader.class);
        var missing = mock(Resource.class);
        when(resourceLoader.getResource(anyString())).thenReturn(missing);
        var registry = new PromptTemplateRegistry();
        ReflectionTestUtils.setField(registry, "resourceLoader", resourceLoader);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(registry, "load"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void fingerprint_isStableAcrossLoadsAndDistinctPerKind() {
        var reloaded = PromptTemplateRegistryObjectMother.loaded();
        var fingerprints = Arrays.stream(LlmRequestKind.values())
            .map(this.registry::fingerprint)
            .toList();

        Arrays.stream(LlmRequestKind.values()).forEach(kind ->
            assertThat(reloaded.fingerprint(kind)).isEqualTo(this.registry.fingerprint(kind)));
        assertThat(fingerprints).doesNotHaveDuplicates().allMatch(hex -> hex.length() == 12);
    }

    @Test
    void fingerprint_ignoresLineEndings() {
        var lf = loadedWith("Same words.\nSecond line.\n");
        var crlf = loadedWith("Same words.\r\nSecond line.\r\n");

        assertThat(crlf.fingerprint(LlmRequestKind.EVALUATION))
            .isEqualTo(lf.fingerprint(LlmRequestKind.EVALUATION));
    }

    @Test
    void fingerprint_changesWhenAnyTemplateOfTheKindChanges() {
        var original = loadedWith("Some wording.");
        var edited = loadedWith("Some other wording.");

        assertThat(edited.fingerprint(LlmRequestKind.DECOMPOSITION))
            .isNotEqualTo(original.fingerprint(LlmRequestKind.DECOMPOSITION));
    }

    @Test
    void everyTemplateLoadsFromTheClasspath() {
        var resourceLoader = new DefaultResourceLoader();

        Arrays.stream(PromptKey.values()).forEach(key -> assertThat(
            resourceLoader.getResource("classpath:prompts/" + key.templateName() + ".st").exists())
            .as(key.templateName()).isTrue());
    }
}
