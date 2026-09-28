package org.vader.core.server.models;

import java.util.Map;

/**
 * Code to run inside a Python sandbox, plus any files to stage into its persistent workspace
 * first.
 *
 * @param code the Python source to run
 * @param files a map of filename to base64-encoded content to write into the sandbox's
 *     workspace before running {@code code} -- e.g. a spreadsheet fetched via
 *     {@code get_object_content}; empty if nothing needs staging
 * @param timeoutSeconds an optional hint for how long to allow the run; the sandbox's own
 *     configured ceiling always applies regardless of what is requested here
 */
public record SandboxExecutionRequest(
    String code, Map<String, String> files, Double timeoutSeconds) {
}
