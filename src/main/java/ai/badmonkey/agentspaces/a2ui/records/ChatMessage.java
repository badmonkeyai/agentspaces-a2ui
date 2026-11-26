/*
 * Copyright 2026 Bad Monkey, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.badmonkey.agentspaces.a2ui.records;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One line of a thread in a group's {@code conversations} space, from a person or an agent, with its attachments as
 * {@link SharedFile} ids and an optional reply target (a record that began on a platform and moved to the open
 * namespace with its fields unchanged). Type IRI {@code https://badmonkey.ai/agentspaces/a2ui/v1/ChatMessage}.
 *
 * <p>Version 2 adds {@code format} ({@code text/plain} or {@code text/markdown}, RFC 6838) and {@code cites} (at most
 * 16 files the answer drew on). Both are omitted from the wire while null, so a version 1 value carries neither and
 * encodes to the version 1 bytes; a writer chooses the version by {@link A2uiRecords#versionFor}.
 *
 * <p>Version 3 (srad/hitl.md §3.8, decision 2; srad/cards.md §8.7 slice 6.4) adds the chat step of a person in the
 * loop: {@code confirm}, a desk's request as a chat shows it (what is asked, its state, the decision once there is one,
 * and what a person may do), and {@code answer}, a person's press on it. A line carries at most one of them, and both
 * are omitted from the wire while null, so a version 3 line without them encodes as a version 2 line does apart from
 * its {@code schemaVersion}. A reader of an older version draws the text, which a desk ends with how to reply in words.
 *
 * @param schemaVersion 1 or 2
 * @param id            the message id
 * @param key           the thread
 * @param from          who wrote it
 * @param text          the text
 * @param attachments   {@link SharedFile} ids
 * @param inReplyTo     the message this answers, empty when none
 * @param at            when
 * @param format        version 2: the media type of {@code text}
 * @param cites         version 2: the files the answer cites
 * @param confirm       version 3: a desk's request, written by the desk
 * @param answer        version 3: a person's answer to a request, written for the person by the host that proved them
 */
public record ChatMessage(int schemaVersion, String id, String key, From from, String text, List<String> attachments, String inReplyTo, Instant at,
                          @JsonInclude(JsonInclude.Include.NON_NULL) String format,
                          @JsonInclude(JsonInclude.Include.NON_NULL) List<Cite> cites,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Confirm confirm,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Answer answer) {

    public static final int V1 = 1;
    public static final int V2 = 2;
    public static final int V3 = 3;
    public static final String IRI = A2uiRecords.NAMESPACE + "ChatMessage";
    public static final String SPACE = "conversations";
    public static final String PLAIN = "text/plain";
    public static final String MARKDOWN = "text/markdown";
    public static final int MAX_CITES = 16;

    /** The author: a person (a signed-in user under a delegation) or an agent, with the peer it spoke from. */
    public record From(String kind, String name, String peer) {
        public static final String PERSON = "person";
        public static final String AGENT = "agent";

        public From {
            kind = kind == null || kind.isBlank() ? PERSON : kind;
            if (!PERSON.equals(kind) && !AGENT.equals(kind)) {
                throw new IllegalArgumentException("a chat message is from a person or an agent: " + kind);
            }
            name = name == null ? "" : name;
            peer = peer == null ? "" : peer;
        }
    }

    /** A file an answer drew on: its {@link SharedFile} id, its path in the tree, and the name a chip shows. */
    public record Cite(String file, String path, String name) {
        public Cite {
            Objects.requireNonNull(file, "file");
            if (file.isBlank()) {
                throw new IllegalArgumentException("a cite names its file");
            }
            path = path == null || path.isEmpty() ? "" : A2uiRecords.requirePath(path, "a cite's");
            name = name == null ? "" : name;
        }
    }

    /**
     * A desk's request as a chat shows it (version 3): the request, its state, the decision once there is one, and the
     * actions a person may take. The desk writes it once when the request opens and again, under a new line id with
     * {@code inReplyTo} the first, when it is decided, so every copy of the card shows the outcome.
     *
     * @param invocationId the ask's id, which the answer names
     * @param desk         the desk, as a reference ({@code #name}) or its card id
     * @param kind         approval, confirm, review, choice or input
     * @param question     the question a person answers
     * @param action       the operation asked for, {@code <asset>#<operation>}
     * @param arguments    the call's arguments, as canonical JSON text
     * @param summary      the asker's summary of what the call does
     * @param deadline     when the request lapses, or null
     * @param options      the verdicts a person may give: approve, reject, amend (amend on a review desk)
     * @param reasons      whether a reason goes with the answer: none, optional or required
     * @param code         a short code the request's text shows, for a typed reply when several requests are open
     * @param state        pending or decided
     * @param verdict      once decided: approved, rejected, amended, expired or cancelled; empty while pending
     * @param by           who decided, {@code person:<id>} each
     * @param reason       the decision's reason
     * @param approvals    the distinct approvals so far
     * @param quorum       the approvals the request needs
     */
    public record Confirm(String invocationId, String desk, String kind, String question, String action, String arguments, String summary,
                          Instant deadline, List<String> options, String reasons, String code, String state, String verdict, List<String> by,
                          String reason, int approvals, int quorum) {
        public static final List<String> KINDS = List.of("approval", "confirm", "review", "choice", "input");
        public static final List<String> OPTIONS = List.of(Answer.APPROVE, Answer.REJECT, Answer.AMEND);
        public static final List<String> REASONS = List.of("none", "optional", "required");
        public static final String PENDING = "pending";
        public static final String DECIDED = "decided";
        public static final List<String> VERDICTS = List.of("approved", "rejected", "amended", "expired", "cancelled");
        /** The longest question or summary a line carries, in characters. */
        public static final int MAX_TEXT = 2000;

        public Confirm {
            Objects.requireNonNull(invocationId, "invocationId");
            if (invocationId.isBlank()) {
                throw new IllegalArgumentException("a confirm names the request it is for");
            }
            desk = desk == null ? "" : desk;
            kind = kind == null || kind.isBlank() ? "approval" : kind;
            if (!KINDS.contains(kind)) {
                throw new IllegalArgumentException("a confirm's kind is one of " + KINDS + ": " + kind);
            }
            question = question == null ? "" : question;
            if (question.isBlank()) {
                throw new IllegalArgumentException("a confirm carries the question a person answers");
            }
            if (question.length() > MAX_TEXT || summary != null && summary.length() > MAX_TEXT) {
                throw new IllegalArgumentException("a confirm's question and summary are at most 2000 characters each");
            }
            action = action == null ? "" : action;
            arguments = arguments == null || arguments.isBlank() ? "{}" : arguments;
            summary = summary == null ? "" : summary;
            options = options == null || options.isEmpty() ? List.of(Answer.APPROVE, Answer.REJECT) : List.copyOf(options);
            for (String o : options) {
                if (!OPTIONS.contains(o)) {
                    throw new IllegalArgumentException("a confirm's options are approve, reject and amend: " + o);
                }
            }
            reasons = reasons == null || reasons.isBlank() ? "optional" : reasons;
            if (!REASONS.contains(reasons)) {
                throw new IllegalArgumentException("a confirm's reasons is none, optional or required: " + reasons);
            }
            code = code == null ? "" : code;
            state = state == null || state.isBlank() ? PENDING : state;
            if (!PENDING.equals(state) && !DECIDED.equals(state)) {
                throw new IllegalArgumentException("a confirm's state is pending or decided: " + state);
            }
            verdict = verdict == null ? "" : verdict;
            if (DECIDED.equals(state) != VERDICTS.contains(verdict)) {
                throw new IllegalArgumentException("a decided confirm carries its verdict (" + VERDICTS + "), a pending one none: " + verdict);
            }
            by = by == null ? List.of() : List.copyOf(by);
            reason = reason == null ? "" : reason;
            if (approvals < 0) {
                throw new IllegalArgumentException("a confirm's approvals are not negative");
            }
            quorum = Math.max(1, quorum);
        }

        /** Whether the request still waits for people. */
        public boolean pending() {
            return PENDING.equals(state);
        }

        /** The request decided, as the desk's later line shows it. */
        public Confirm decided(String finalVerdict, List<String> deciders, String why, int approved) {
            return new Confirm(invocationId, desk, kind, question, action, arguments, summary, deadline, options, reasons, code, DECIDED, finalVerdict,
                    deciders, why, approved, quorum);
        }
    }

    /**
     * A person's answer to a {@link Confirm} (version 3): the request, the verdict, and the reason. The host that proved
     * the person (an app's feed) writes it as the person's line in the thread, {@code inReplyTo} the request's line; a
     * desk takes it only from a host it trusts.
     *
     * @param verdict approve, reject or amend
     */
    public record Answer(String invocationId, String verdict, String reason, String amendedArguments) {
        public static final String APPROVE = "approve";
        public static final String REJECT = "reject";
        public static final String AMEND = "amend";

        public Answer {
            Objects.requireNonNull(invocationId, "invocationId");
            if (invocationId.isBlank()) {
                throw new IllegalArgumentException("an answer names the request it answers");
            }
            verdict = verdict == null ? "" : verdict;
            if (!Confirm.OPTIONS.contains(verdict)) {
                throw new IllegalArgumentException("an answer's verdict is approve, reject or amend: " + verdict);
            }
            reason = reason == null ? "" : reason;
            if (reason.length() > Confirm.MAX_TEXT) {
                throw new IllegalArgumentException("an answer's reason is at most 2000 characters");
            }
            amendedArguments = amendedArguments == null ? "" : amendedArguments;
            if (!amendedArguments.isEmpty() && !AMEND.equals(verdict)) {
                throw new IllegalArgumentException("only an amend carries amended arguments");
            }
        }
    }

    /** A line of version 1 or 2, as before version 3. */
    public ChatMessage(int schemaVersion, String id, String key, From from, String text, List<String> attachments, String inReplyTo, Instant at,
                       String format, List<Cite> cites) {
        this(schemaVersion, id, key, from, text, attachments, inReplyTo, at, format, cites, null, null);
    }

    public ChatMessage {
        if (schemaVersion != V1 && schemaVersion != V2 && schemaVersion != V3) {
            throw new IllegalArgumentException("a ChatMessage has schemaVersion 1, 2 or 3: " + schemaVersion);
        }
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        from = from == null ? new From(From.PERSON, "", "") : from;
        text = text == null ? "" : text;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        inReplyTo = inReplyTo == null ? "" : inReplyTo;
        if (schemaVersion == V1) {
            if (format != null || cites != null) {
                throw new IllegalArgumentException("a version 1 ChatMessage carries no format and no cites; write version 2 for them");
            }
            if (confirm != null || answer != null) {
                throw new IllegalArgumentException("a version 1 ChatMessage carries no confirm and no answer; write version 3 for them");
            }
        } else {
            if (schemaVersion == V2 && (confirm != null || answer != null)) {
                throw new IllegalArgumentException("a version 2 ChatMessage carries no confirm and no answer; write version 3 for them");
            }
            if (confirm != null && answer != null) {
                throw new IllegalArgumentException("a ChatMessage carries a confirm or an answer, not both");
            }
            format = format == null ? PLAIN : format;
            if (!PLAIN.equals(format) && !MARKDOWN.equals(format)) {
                throw new IllegalArgumentException("format must be text/plain or text/markdown: " + format);
            }
            cites = cites == null ? List.of() : List.copyOf(cites);
            if (cites.size() > MAX_CITES) {
                throw new IllegalArgumentException("a ChatMessage cites at most 16 files: " + cites.size());
            }
        }
    }

    /** A version 1 line: the fields it had before {@code format} and {@code cites}. */
    public static ChatMessage of(String id, String key, From from, String text, Instant at) {
        return new ChatMessage(V1, id, key, from, text, List.of(), "", at, null, null);
    }

    /**
     * This line in the version a writer chose: version 1 drops {@code format} and {@code cites}, version 2 takes their
     * defaults, and both drop {@code confirm} and {@code answer}, so a desk writing to older readers keeps the text alone.
     */
    public ChatMessage asVersion(int version) {
        if (version == schemaVersion) {
            return this;
        }
        return switch (version) {
            case V1 -> new ChatMessage(V1, id, key, from, text, attachments, inReplyTo, at, null, null);
            case V2 -> new ChatMessage(V2, id, key, from, text, attachments, inReplyTo, at, format, cites);
            default -> new ChatMessage(version, id, key, from, text, attachments, inReplyTo, at, format, cites, confirm, answer);
        };
    }
}
