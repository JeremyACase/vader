package org.vader.core.server.orchestration.model;

/**
 * A file attached to the request being planned, as the planner sees it: what it is called and
 * what kind of file it is, never its contents. Enough for the planner to know the request comes
 * with a file the task agents can work on.
 *
 * @param filename the file's original name
 * @param contentType the file's media type
 */
public record AttachedFile(String filename, String contentType) {
}
