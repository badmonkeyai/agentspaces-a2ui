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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An agent's progress on one line of a chat: the A2A task state and at most 20 steps it took
 * (read, tool, write), in the group's {@code views} space. One entry per line ({@link #idFor} the person's line),
 * updated in place, leased {@link #LEASE} and renewed every {@link #RENEW} while working, so an agent that stops
 * mid-answer leaves an entry that lapses and a chat says "the agent stopped". For a reader that speaks AG-UI, the
 * states map onto {@code RUN_STARTED} and {@code RUN_FINISHED} and each step onto {@code TOOL_CALL_START} and
 * {@code TOOL_CALL_END}. Type IRI {@code https://badmonkey.ai/agentspaces/a2ui/v1/ChatProgress}.
 *
 * @param schemaVersion 1
 * @param id            {@link #idFor} the line
 * @param key           the thread
 * @param inReplyTo     the person's line
 * @param agent         the agent
 * @param state         an A2A task state ({@link TaskStates})
 * @param steps         at most 20, oldest first
 * @param at            when it last changed
 */
public record ChatProgress(int schemaVersion, String id, String key, String inReplyTo, String agent, String state, List<Step> steps, Instant at) {

    public static final int VERSION = 1;
    public static final String IRI = A2uiRecords.NAMESPACE + "ChatProgress";
    public static final String SPACE = "views";
    public static final Duration LEASE = Duration.ofSeconds(30);
    public static final Duration RENEW = Duration.ofSeconds(10);
    public static final int MAX_STEPS = 20;

    /** One step: what a person reads, its kind, what it touched (a path, a tool, an entry), and when. */
    public record Step(String label, String kind, String ref, Instant at) {
        public static final String READ = "read";
        public static final String TOOL = "tool";
        public static final String WRITE = "write";
        public static final List<String> KINDS = List.of(READ, TOOL, WRITE);

        public Step {
            label = label == null ? "" : label;
            Objects.requireNonNull(kind, "kind");
            if (!KINDS.contains(kind)) {
                throw new IllegalArgumentException("a step's kind is read, tool or write: " + kind);
            }
            ref = ref == null ? "" : ref;
            Objects.requireNonNull(at, "at");
        }
    }

    public ChatProgress {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(inReplyTo, "inReplyTo");
        Objects.requireNonNull(agent, "agent");
        state = TaskStates.require(state == null ? TaskStates.SUBMITTED : state);
        steps = steps == null ? List.of() : List.copyOf(steps);
        if (steps.size() > MAX_STEPS) {
            throw new IllegalArgumentException("a progress entry keeps at most 20 steps: " + steps.size());
        }
        Objects.requireNonNull(at, "at");
    }

    /** The one entry per line. */
    public static String idFor(String inReplyTo) {
        return "progress-" + inReplyTo;
    }
}
