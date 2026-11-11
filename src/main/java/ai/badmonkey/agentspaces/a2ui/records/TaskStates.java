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

import java.util.List;

/** The task states of the A2A specification, the words {@link WorkingOn} and {@link ChatProgress} carry in {@code state}. */
public final class TaskStates {

    public static final String SUBMITTED = "submitted";
    public static final String WORKING = "working";
    public static final String INPUT_REQUIRED = "input-required";
    public static final String COMPLETED = "completed";
    public static final String FAILED = "failed";
    public static final String CANCELED = "canceled";
    /** The six, in the A2A specification's order. */
    public static final List<String> ALL = List.of(SUBMITTED, WORKING, INPUT_REQUIRED, COMPLETED, FAILED, CANCELED);

    private TaskStates() {
    }

    /** Whether the task ended: completed, failed or canceled. */
    public static boolean isFinal(String state) {
        return COMPLETED.equals(state) || FAILED.equals(state) || CANCELED.equals(state);
    }

    static String require(String state) {
        if (!ALL.contains(state)) {
            throw new IllegalArgumentException("state must be one of the A2A task states " + ALL + ": " + state);
        }
        return state;
    }
}
