package org.vader.core.server.controller.advice;

/**
 * The one error body every REST endpoint in {@code core-server} returns, written by
 * {@link GlobalExceptionHandler}. Before this existed, three near-identical shapes were scattered
 * across the codebase: two structurally-identical-but-distinct local records (one each in
 * {@code ClientPromptController} and {@code BackpressureController}) and a bare
 * {@code Map.of("error", ..., "message", ...)} used everywhere else -- all saying the same thing
 * three different ways.
 *
 * @param error a stable, machine-readable code (e.g. {@code "unknown_tool"})
 * @param message a human-readable description
 */
public record ErrorResponse(String error, String message) {
}
