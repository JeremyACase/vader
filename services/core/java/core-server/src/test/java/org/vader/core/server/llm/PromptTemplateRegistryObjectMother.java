package org.vader.core.server.llm;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds a {@link PromptTemplateRegistry} loaded with the real templates, for unit tests. */
public final class PromptTemplateRegistryObjectMother {

    private PromptTemplateRegistryObjectMother() {
    }

    /**
     * A registry that has loaded every template from the classpath, as it would at startup.
     *
     * @return the loaded registry
     */
    public static PromptTemplateRegistry loaded() {
        var registry = new PromptTemplateRegistry();
        ReflectionTestUtils.setField(registry, "resourceLoader", new DefaultResourceLoader());
        ReflectionTestUtils.invokeMethod(registry, "load");
        return registry;
    }
}
