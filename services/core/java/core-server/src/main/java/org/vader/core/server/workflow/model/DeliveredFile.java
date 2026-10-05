package org.vader.core.server.workflow.model;

/**
 * A file a workflow's tasks uploaded for the user, as the final answer presents it.
 *
 * @param filename the file's original name
 * @param downloadPath the REST path the file can be downloaded from
 * @param inlineContent the file's text, when it is text and small enough to show in the answer
 *     itself; {@code null} otherwise
 */
public record DeliveredFile(String filename, String downloadPath, String inlineContent) {
}
