package org.vader.core.exceptions;

/** Thrown when a referenced {@code ObjectMetadata} id does not correspond to any stored object. */
public class ObjectNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception for a missing object id.
     *
     * @param objectMetadataId the id that could not be found
     */
    public ObjectNotFoundException(final String objectMetadataId) {
        super("No object found with id '" + objectMetadataId + "'");
    }
}
