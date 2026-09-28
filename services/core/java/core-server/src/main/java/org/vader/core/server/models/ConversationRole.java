package org.vader.core.server.models;

/** Which participant produced a {@link ConversationMessage}, mirroring Spring AI's MessageType. */
public enum ConversationRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
