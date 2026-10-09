package org.vader.core.server.orchestration;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.llm.LlmRequestInbox;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.interfaces.InterfaceLlmExecutor;
import org.vader.core.server.mcp.AgentToolAudience;
import org.vader.core.server.mcp.McpToolCallbackRegistry;
import org.vader.core.server.orchestration.model.AttachedFile;
import org.vader.core.server.orchestration.model.DecompositionRequest;
import org.vader.core.server.orchestration.model.LlmTaskPlan;

/**
 * Asks the chat model to decompose one client prompt, via Spring AI's {@link ChatClient}. Called
 * only from inside {@link LlmRequestInbox#handle}; {@code LlmTaskPlanAdapter} submits the request
 * through {@link LlmRequestQueue}.
 *
 * <p>Every tool tagged {@link AgentToolAudience#ORCHESTRATION} (via
 * {@link McpToolCallbackRegistry#forAudience}) is offered to the model, so it may call them while
 * it plans -- this is the "higher-level agent" role: it never gets sandbox code execution, only
 * tools for reasoning about system state.</p>
 *
 * <p>Builds a fresh {@link ChatClient} per call: one shared instance corrupts its advisor chain
 * under concurrent calls (spring-projects/spring-ai#3537). Building one is cheap -- it wraps the
 * already-configured {@code ChatModel}.</p>
 */
@Service
public class DecompositionLlmExecutor
    implements InterfaceLlmExecutor<DecompositionRequest, LlmTaskPlan> {

    private static final String DECOMPOSITION_INSTRUCTIONS = """
        You are Vader, a planning assistant. Your job is to decompose the user's problem into a
        concrete plan of 2 to 6 top-level tasks.

        Before writing the plan, reason step by step in the `reasoning` field: restate the goal
        in your own words, identify any constraints or unknowns, decide whether any of your
        available tools would materially help, and sketch your overall approach. Write this
        reasoning before filling in `objective` and `tasks` — it will be shown to the user.

        Give every task a short, unique title. List tasks in the order they would naturally
        happen. Most plans have real dependencies — a task that needs another task's output
        cannot run in parallel with it. Use `dependsOn` to say so: it holds the exact titles of
        the tasks listed earlier that must finish first. Only name earlier tasks (a task can never
        depend on itself or on something listed after it); leave it empty for a task that can
        start immediately. Do not default every task to an empty list just because it is easier —
        for each task, ask what it needs from the others.

        For example, "research competitors, then write a positioning doc, then get it reviewed":
          - "Research competitors" with `dependsOn: []`
          - "Write positioning doc" with `dependsOn: ["Research competitors"]`
          - "Review positioning doc" with `dependsOn: ["Write positioning doc"]`
        Each waits only on the task it actually needs, not on every prior task. A plan whose tasks
        are all independent (e.g. "fetch three unrelated reports") correctly leaves every
        `dependsOn` empty.

        Each task is carried out by an autonomous agent that can run Python code in its own
        sandbox, where every file attached to the request is already in its working directory.
        No agent can ask the user anything, so never plan a task that requests information from
        the user: plan around what the request and its files provide.

        When the user asks for something to keep -- code, a script, a report, a document, a data
        file -- that file is the deliverable, and the user cannot reach any agent's sandbox. So
        the plan must end with a task that writes the finished file and saves it with the
        upload_object tool, which is how the user receives it. Running or testing the file in a
        sandbox only checks it; it does not deliver it. Every task gets a fresh sandbox, so files
        do not carry over between tasks: the task that saves the deliverable must write the
        complete file itself. Sandboxes have no network access, so nothing can be installed or
        downloaded: plan around Python's standard library plus pandas and openpyxl, which are
        preinstalled.

        You have been given a set of tools. Call a tool only when doing so materially helps you
        plan or gather information the plan needs; otherwise just plan. Do not call tools
        speculatively.
        """;

    private static final String ATTACHED_FILES_INSTRUCTIONS = """

        The user attached these files to the request. You cannot see their contents, but the
        agents carrying out your tasks can:
        %s
        """;

    private static final String REVISION_INSTRUCTIONS = """

        A reviewer rejected your previous plan for this same request, for this reason:
        %s

        Produce a new plan that fixes that problem. Your `reasoning` must explain the new plan on
        its own terms -- do not quote, restate, or respond to this feedback, and do not mention
        any previous plan or its tasks. The user will read your reasoning and has never seen the
        previous plan.
        """;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private McpToolCallbackRegistry toolCallbackRegistry;

    @Override
    public LlmRequestKind kind() {
        return LlmRequestKind.DECOMPOSITION;
    }

    /**
     * Decomposes one client prompt.
     *
     * @param request the user's original request text, verbatim, plus its attached files and any
     *     revision guidance
     * @return the model's plan, in the lean shape it was asked to produce
     */
    @Override
    public LlmTaskPlan execute(final DecompositionRequest request) {
        var toolCallbacks = this.toolCallbackRegistry.forAudience(AgentToolAudience.ORCHESTRATION);
        return this.chatClientBuilder.build().prompt()
            .system(instructionsFor(request))
            .user(request.clientPromptText())
            .toolCallbacks(toolCallbacks)
            .call()
            .entity(LlmTaskPlan.class);
    }

    /**
     * The system instructions for one decomposition: the standing planning instructions, plus
     * the attached files, if any, and -- only on a revision -- why the previous plan was
     * rejected. Both go here, as system-level instructions, rather than into the user message,
     * so the user's request is always passed through exactly as written.
     */
    private static String instructionsFor(final DecompositionRequest request) {
        return DECOMPOSITION_INSTRUCTIONS + attachedFilesSection(request.attachedFiles())
            + revisionSection(request.revisionGuidance());
    }

    /** Names and types only: the planner never receives a file's contents. */
    private static String attachedFilesSection(final List<AttachedFile> attachedFiles) {
        var lines = attachedFiles.stream()
            .map(file -> "- \"" + file.filename() + "\" (type: " + file.contentType() + ")")
            .collect(Collectors.joining("\n"));
        return attachedFiles.isEmpty() ? "" : ATTACHED_FILES_INSTRUCTIONS.formatted(lines);
    }

    private static String revisionSection(final String revisionGuidance) {
        return revisionGuidance == null ? "" : REVISION_INSTRUCTIONS.formatted(revisionGuidance);
    }
}
