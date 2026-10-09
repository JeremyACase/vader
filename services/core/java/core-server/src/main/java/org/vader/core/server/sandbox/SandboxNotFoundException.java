package org.vader.core.server.sandbox;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thrown when a caller names a sandbox this operator doesn't manage. Carries its own 404, so the
 * global exception handler reports it as a rejected request rather than a server failure.
 */
public class SandboxNotFoundException extends ResponseStatusException {

    /**
     * Creates the exception.
     *
     * @param name the sandbox name the caller gave
     */
    public SandboxNotFoundException(final String name) {
        super(HttpStatus.NOT_FOUND, "No sandbox named '" + name + "'");
    }
}
