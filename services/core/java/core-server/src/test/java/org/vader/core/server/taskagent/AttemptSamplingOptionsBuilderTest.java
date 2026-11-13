package org.vader.core.server.taskagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.test.util.ReflectionTestUtils;

class AttemptSamplingOptionsBuilderTest {

    private static AttemptSamplingOptionsBuilder builderFor(final ChatOptions defaults) {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(defaults);
        var builder = new AttemptSamplingOptionsBuilder();
        ReflectionTestUtils.setField(builder, "chatModel", chatModel);
        return builder;
    }

    private static OllamaChatOptions seeded(final int seed) {
        return OllamaChatOptions.builder().model("any-model").temperature(0.7).seed(seed).build();
    }

    private static OllamaChatOptions built(final ChatOptions.Builder<?> options) {
        return (OllamaChatOptions) options.build();
    }

    @Test
    void forAttempt_keepsTheConfiguredSeedForTheFirstAttempt() {
        var options = builderFor(seeded(42)).forAttempt(1);

        assertThat(options).map(AttemptSamplingOptionsBuilderTest::built)
            .get().extracting(OllamaChatOptions::getSeed).isEqualTo(42);
    }

    @Test
    void forAttempt_offsetsTheSeedByTheAttemptNumberAndKeepsTheOtherDefaults() {
        var options = builderFor(seeded(42)).forAttempt(3).map(
            AttemptSamplingOptionsBuilderTest::built).orElseThrow();

        assertThat(options.getSeed()).isEqualTo(44);
        assertThat(options.getModel()).isEqualTo("any-model");
        assertThat(options.getTemperature()).isEqualTo(0.7);
    }

    @Test
    void forAttempt_leavesTheModelsDefaultsUntouched() {
        var defaults = seeded(42);

        builderFor(defaults).forAttempt(3);

        assertThat(defaults.getSeed()).isEqualTo(42);
    }

    @Test
    void forAttempt_buildsNothingWhenNoSeedIsPinned() {
        var unseeded = OllamaChatOptions.builder().model("any-model").build();

        assertThat(builderFor(unseeded).forAttempt(2)).isEmpty();
    }

    @Test
    void forAttempt_buildsNothingForAnUnknownAttempt() {
        assertThat(builderFor(seeded(42)).forAttempt(null)).isEmpty();
    }

    @Test
    void forAttempt_buildsNothingForModelsOtherThanOllama() {
        assertThat(builderFor(ToolCallingChatOptions.builder().build()).forAttempt(2)).isEmpty();
    }
}
