package org.vader.core.server.sandbox.model;

/**
 * One file in a sandbox's workspace as the sandbox lists it: enough to tell whether it is already
 * stored, without fetching its content.
 *
 * @param filename the file's name in the workspace
 * @param size the file's size in bytes
 * @param sha256 the hex SHA-256 of the file's content
 */
public record SandboxFileInfo(String filename, long size, String sha256) {
}
