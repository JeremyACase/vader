package org.vader.core.server.storage;

/** Thrown when a file cannot be read from the upload stream or written to the backing store. */
public class FileStorageException extends RuntimeException {

    /**
     * Creates the exception with a message and underlying cause.
     *
     * @param message the detail message
     * @param cause the underlying I/O or transport failure
     */
    public FileStorageException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
