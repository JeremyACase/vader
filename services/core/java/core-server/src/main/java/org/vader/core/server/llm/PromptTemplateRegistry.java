package org.vader.core.server.llm;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.ValidationMode;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.LlmRequestKind;

/**
 * Loads every {@link PromptKey}'s template once, at startup, and renders them. Templates are
 * StringTemplate with {@code < >} delimiters, so a prompt can show the model a JSON example
 * without escaping its braces.
 *
 * <p>Startup fails if a template is missing or references a variable its key does not declare,
 * and a render fails if the caller supplies one the key does not declare: either way, a
 * template and the code that fills it cannot drift apart silently.</p>
 *
 * <p>Each {@link LlmRequestKind} gets a fingerprint -- a hash of every template it uses -- so an
 * outcome can be attributed to the exact prompt wording that produced it. Line endings are
 * normalized before hashing, so a checkout's line-ending settings never change a fingerprint.</p>
 */
@Service
public class PromptTemplateRegistry {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateRegistry.class);

    private static final String LOCATION = "classpath:prompts/";

    private static final String EXTENSION = ".st";

    private static final int FINGERPRINT_HEX_LENGTH = 12;

    private static final String VALIDATION_SENTINEL = "x";

    private final TemplateRenderer validatingRenderer = renderer(ValidationMode.THROW);

    // A null variable is an absent optional section, which the validating renderer would reject
    // as missing; every template was already checked against its declared variables at startup.
    private final TemplateRenderer renderer = renderer(ValidationMode.NONE);

    private final Map<PromptKey, String> templates = new EnumMap<>(PromptKey.class);

    private final Map<LlmRequestKind, String> fingerprints = new EnumMap<>(LlmRequestKind.class);

    @Autowired
    private ResourceLoader resourceLoader;

    /**
     * Loads and validates every template, then fingerprints each call kind.
     *
     * @throws IllegalStateException if a template is missing or references an undeclared
     *     variable
     */
    @PostConstruct
    void load() {
        Arrays.stream(PromptKey.values()).forEach(this::loadTemplate);
        Arrays.stream(LlmRequestKind.values()).forEach(this::fingerprintKind);
        logger.info("Prompt fingerprints: {}", this.fingerprints);
    }

    /**
     * Renders a template that takes no variables.
     *
     * @param key the template
     * @return the rendered prompt text
     */
    public String render(final PromptKey key) {
        return this.render(key, Map.of());
    }

    /**
     * Renders a template.
     *
     * @param key the template
     * @param variables its variables; a {@code null} value omits that optional section
     * @return the rendered prompt text
     * @throws IllegalArgumentException if a variable is not one the key declares
     */
    public String render(final PromptKey key, final Map<String, Object> variables) {
        var undeclared = variables.keySet().stream()
            .filter(name -> !key.variables().contains(name))
            .toList();
        if (!undeclared.isEmpty()) {
            throw new IllegalArgumentException(
                "Prompt " + key + " does not declare variables " + undeclared);
        }
        return normalized(this.renderer.apply(this.templates.get(key), variables));
    }

    /**
     * The fingerprint of every template a call kind uses.
     *
     * @param kind the call kind
     * @return a short hex hash that changes whenever any of the kind's templates does
     */
    public String fingerprint(final LlmRequestKind kind) {
        return this.fingerprints.get(kind);
    }

    /**
     * The templates a call kind's fingerprint covers, exactly as hashed.
     *
     * @param kind the call kind
     * @return each template's text by its name (e.g. {@code review/evaluation-system}), in the
     *     order they are hashed
     */
    public Map<String, String> templates(final LlmRequestKind kind) {
        return Arrays.stream(PromptKey.values())
            .filter(key -> key.kind() == kind)
            .collect(Collectors.toMap(PromptKey::templateName, this.templates::get,
                (first, second) -> first, LinkedHashMap::new));
    }

    private void loadTemplate(final PromptKey key) {
        var template = this.read(key);
        try {
            this.validatingRenderer.apply(template, sentinelsFor(key));
        } catch (IllegalStateException exception) {
            throw new IllegalStateException(
                "Prompt template " + key.templateName() + " does not match the variables "
                    + key.variables() + " its key declares: " + exception.getMessage(),
                exception);
        }
        this.templates.put(key, template);
    }

    private String read(final PromptKey key) {
        var location = LOCATION + key.templateName() + EXTENSION;
        var resource = this.resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Prompt template not found: " + location);
        }
        try {
            return normalized(resource.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read prompt template " + location, exception);
        }
    }

    private void fingerprintKind(final LlmRequestKind kind) {
        var digest = sha256();
        Arrays.stream(PromptKey.values())
            .filter(key -> key.kind() == kind)
            .forEach(key -> digest.update(
                (key.name() + "\0" + this.templates.get(key) + "\0")
                    .getBytes(StandardCharsets.UTF_8)));
        var hex = HexFormat.of().formatHex(digest.digest());
        this.fingerprints.put(kind, hex.substring(0, FINGERPRINT_HEX_LENGTH));
    }

    /**
     * Trailing whitespace is never part of a prompt: files end with a newline by convention, and
     * an omitted optional section at the end leaves its own. Line endings are always LF, since a
     * Windows checkout may store a template with CRLF and StringTemplate renders the platform's
     * line separator; neither may change a prompt or its hash.
     */
    private static String normalized(final String content) {
        return content.replace("\r\n", "\n").stripTrailing();
    }

    private static Map<String, Object> sentinelsFor(final PromptKey key) {
        return key.variables().stream()
            .collect(Collectors.toMap(Function.identity(), name -> VALIDATION_SENTINEL));
    }

    private static TemplateRenderer renderer(final ValidationMode validationMode) {
        return StTemplateRenderer.builder()
            .startDelimiterToken('<')
            .endDelimiterToken('>')
            .validationMode(validationMode)
            .build();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
