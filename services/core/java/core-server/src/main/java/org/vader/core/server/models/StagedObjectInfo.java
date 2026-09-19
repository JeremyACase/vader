package org.vader.core.server.models;

/**
 * Confirms an object was staged into a sandbox's workspace, deliberately carrying no content --
 * the point of staging is that the bytes go straight from {@code core-server} to the sandbox pod,
 * never through the conversation a model sees.
 *
 * @param filename the name the object was written under inside the sandbox's workspace
 * @param contentType the object's recorded MIME type, or {@code null} if never recorded
 * @param size the object's size in bytes
 */
public record StagedObjectInfo(String filename, String contentType, long size) {
}
