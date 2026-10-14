package org.vader.core.server.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class EvalModeValidatorTest {

    private static EvalModeValidator validator(
            final VaderMode mode, final String orchestratorType, final Double temperature,
            final Integer seed) {
        var validator = new EvalModeValidator();
        ReflectionTestUtils.setField(validator, "mode", mode);
        ReflectionTestUtils.setField(validator, "orchestratorType", orchestratorType);
        ReflectionTestUtils.setField(validator, "temperature", temperature);
        ReflectionTestUtils.setField(validator, "seed", seed);
        return validator;
    }

    @Test
    void requirePinnedSampling_acceptsEvalWithLocalModelZeroTemperatureAndSeed() {
        var validator = validator(VaderMode.EVAL, "local", 0.0, Instancio.create(Integer.class));

        assertThatCode(validator::requirePinnedSampling).doesNotThrowAnyException();
    }

    @Test
    void requirePinnedSampling_rejectsEvalWithoutSeed() {
        var validator = validator(VaderMode.EVAL, "local", 0.0, null);

        assertThatThrownBy(validator::requirePinnedSampling)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("seed is not set");
    }

    @Test
    void requirePinnedSampling_rejectsEvalAboveTemperatureZero() {
        var validator = validator(VaderMode.EVAL, "local", 0.7, 42);

        assertThatThrownBy(validator::requirePinnedSampling)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("temperature is 0.7");
    }

    @Test
    void requirePinnedSampling_namesEveryProblemAtOnce() {
        var validator = validator(VaderMode.EVAL, "scripted", null, null);

        assertThatThrownBy(validator::requirePinnedSampling)
            .hasMessageContaining("orchestrator.type is scripted")
            .hasMessageContaining("temperature is null")
            .hasMessageContaining("seed is not set");
    }

    @ParameterizedTest
    @EnumSource(value = VaderMode.class, names = "EVAL", mode = EnumSource.Mode.EXCLUDE)
    void requirePinnedSampling_ignoresEveryOtherMode(final VaderMode mode) {
        var validator = validator(mode, "scripted", null, null);

        assertThatCode(validator::requirePinnedSampling).doesNotThrowAnyException();
    }
}
