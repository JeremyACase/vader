package org.vader.core.server.tools.operators.pythonsandbox;

import java.util.Locale;
import java.util.UUID;

/**
 * Turns an optional caller-supplied sandbox name into a valid, unique Kubernetes resource name.
 */
public final class SandboxNaming {

    /** Prefix shared by every sandbox Deployment and Service. */
    public static final String PREFIX = "vader-sandbox-";

    private static final int MAX_LENGTH = 63;
    private static final int RANDOM_SUFFIX_LENGTH = 8;

    private SandboxNaming() {
    }

    /**
     * Resolves {@code requested} to a sandbox name.
     *
     * <p>A blank request yields {@code vader-sandbox-<random>}. A non-blank request is lower-cased,
     * has every run of non-{@code [a-z0-9-]} characters collapsed to a single dash, is stripped of
     * leading and trailing dashes, given the shared prefix, and truncated to the 63-character
     * DNS-1123 limit.</p>
     *
     * @param requested the caller-supplied name; may be {@code null} or blank
     * @return a valid DNS-1123 label
     */
    public static String resolve(final String requested) {
        if (requested == null || requested.isBlank()) {
            return PREFIX + randomSuffix();
        }

        var slug = requested.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            slug = randomSuffix();
        }

        var name = PREFIX + slug;
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return name;
    }

    private static String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, RANDOM_SUFFIX_LENGTH);
    }
}
