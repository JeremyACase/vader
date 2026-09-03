package org.vader.core.server.storage;

/** Thrown when a file cannot be read from the upload stream or written to the backing store. */
public class FileStorageException extends RuntimeException {

    public FileStorageException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
