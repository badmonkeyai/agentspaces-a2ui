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
 */
public record ChatMessage(int schemaVersion, String id, String key, From from, String text, List<String> attachments, String inReplyTo, Instant at,
                          @JsonInclude(JsonInclude.Include.NON_NULL) String format,
                          @JsonInclude(JsonInclude.Include.NON_NULL) List<Cite> cites) {

    public static final int V1 = 1;
    public static final int V2 = 2;
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

    public ChatMessage {
        if (schemaVersion != V1 && schemaVersion != V2) {
            throw new IllegalArgumentException("a ChatMessage has schemaVersion 1 or 2: " + schemaVersion);
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
        } else {
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

    /** This line in the version a writer chose: version 1 drops {@code format} and {@code cites}; version 2 takes their defaults. */
    public ChatMessage asVersion(int version) {
        if (version == schemaVersion) {
            return this;
        }
        return version == V1 ? new ChatMessage(V1, id, key, from, text, attachments, inReplyTo, at, null, null)
                : new ChatMessage(version, id, key, from, text, attachments, inReplyTo, at, format, cites);
    }
}
