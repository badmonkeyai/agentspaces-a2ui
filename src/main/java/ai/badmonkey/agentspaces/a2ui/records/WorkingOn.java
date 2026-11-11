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
 * What an agent holds while it works: the files it has open for a thread or a record key, a
 * short task line, and the A2A task state, in the group's {@code views} space. One entry per agent and key
 * ({@link #idFor}), updated in place, leased {@link #LEASE} and renewed every {@link #RENEW} while held, so an agent
 * that stops leaves nothing behind for long. Type IRI {@code https://badmonkey.ai/agentspaces/a2ui/v1/WorkingOn}.
 *
 * @param schemaVersion 1
 * @param id            {@link #idFor} the agent and the key
 * @param agent         the agent
 * @param key           a thread or a record key
 * @param files         the paths it holds
 * @param task          what it does, at most 120 characters
 * @param state         an A2A task state ({@link TaskStates})
 * @param since         when it took the work
 */
public record WorkingOn(int schemaVersion, String id, String agent, String key, List<String> files, String task, String state, Instant since) {

    public static final int VERSION = 1;
    public static final String IRI = A2uiRecords.NAMESPACE + "WorkingOn";
    public static final String SPACE = "views";
    public static final Duration LEASE = Duration.ofSeconds(60);
    public static final Duration RENEW = Duration.ofSeconds(20);
    public static final int MAX_TASK = 120;

    public WorkingOn {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(agent, "agent");
        key = key == null ? "" : key;
        files = files == null ? List.of() : List.copyOf(files);
        files.forEach(f -> A2uiRecords.requirePath(f, "a held file's"));
        task = task == null ? "" : task;
        if (task.length() > MAX_TASK) {
            throw new IllegalArgumentException("a task line is at most 120 characters: " + task.length());
        }
        state = TaskStates.require(state == null ? TaskStates.WORKING : state);
        Objects.requireNonNull(since, "since");
    }

    /** The one entry an agent keeps per key. */
    public static String idFor(String agent, String key) {
        return "working-" + agent + "-" + key;
    }
}
