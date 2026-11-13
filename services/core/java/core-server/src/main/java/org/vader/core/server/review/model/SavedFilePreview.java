package org.vader.core.server.review.model;

/**
 * One file an attempt saved, as the reviewer sees it: what the system recorded about it and, for
 * text, its opening lines -- so the reviewer can check what the file holds, not only that it
 * exists.
 *
 * @param filename the file's name
 * @param contentType the stored MIME type, or {@code null} if never recorded
 * @param size the file's size in bytes
 * @param preview the file's opening lines, or {@code null} when none are shown: the file is
 *     empty or binary, or the files before it used up the preview space
 * @param truncated whether the file continues past {@code preview}
 * @param binary whether the file's content is not text, and so is never previewed
 */
public record SavedFilePreview(
    String filename,
    String contentType,
    long size,
    String preview,
    boolean truncated,
    boolean binary) {
}
