package org.vader.core.server.web;

/**
 * The one error body every REST endpoint in {@code core-server} returns, written by
 * {@link GlobalExceptionHandler}. Controllers never build their own error bodies.
 *
 * @param error a stable, machine-readable code (e.g. {@code "unknown_tool"})
 * @param message a human-readable description
 */
public record ErrorResponse(String error, String message) {
}
