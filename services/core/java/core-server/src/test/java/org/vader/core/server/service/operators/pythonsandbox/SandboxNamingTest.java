package org.vader.core.server.service.operators.pythonsandbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SandboxNamingTest {

    @Test
    void resolve_withBlankRequest_generatesPrefixedName() {
        assertThat(SandboxNaming.resolve(null)).startsWith(SandboxNaming.PREFIX);
        assertThat(SandboxNaming.resolve("  ")).startsWith(SandboxNaming.PREFIX);
        assertThat(SandboxNaming.resolve(null)).isNotEqualTo(SandboxNaming.resolve(null));
    }

    @Test
    void resolve_lowercasesAndSlugifies() {
        assertThat(SandboxNaming.resolve("My Cool Box")).isEqualTo("vader-sandbox-my-cool-box");
        assertThat(SandboxNaming.resolve("a__b--c")).isEqualTo("vader-sandbox-a-b-c");
        assertThat(SandboxNaming.resolve("-trim-")).isEqualTo("vader-sandbox-trim");
    }

    @Test
    void resolve_producesValidDns1123Labels() {
        var name = SandboxNaming.resolve("Ünïcode & symbols!!! " + "x".repeat(200));
        assertThat(name).matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?");
        assertThat(name.length()).isLessThanOrEqualTo(63);
    }

    @Test
    void resolve_whenRequestHasNoUsableCharacters_fallsBackToRandom() {
        assertThat(SandboxNaming.resolve("!!!")).startsWith(SandboxNaming.PREFIX);
    }
}
