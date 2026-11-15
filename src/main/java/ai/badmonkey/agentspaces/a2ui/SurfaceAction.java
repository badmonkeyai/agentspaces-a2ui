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


package ai.badmonkey.agentspaces.a2ui;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A renderer's action on a surface: the app, the surface id, the session it came through, and the A2UI
 * action ({@code name}, {@code surfaceId}, {@code sourceComponentId}, {@code timestamp}, {@code context}); the surface
 * agent takes it under the session's delegation. Version 1.
 *
 * <p>A page's call to one of the agent's functions (A2UI 1.0's {@code callAgentFunction}) arrives in the same shape, so
 * an agent that reads actions sees it: the action's {@code name} is the function, its {@code context} the arguments, and
 * its {@code functionCallId} the call's id ({@link #callsFunction()}). The agent answers it with a
 * {@link ai.badmonkey.agentspaces.a2ui.records.FunctionResponse} ({@link Surfaces#answer}, {@link Surfaces#fail}).
 */
public record SurfaceAction(int schemaVersion, String app, String surfaceId, String session, Map<String, Object> action, Instant at) {

    public static final int VERSION = 1;

    public SurfaceAction {
        // the app and the surface id are dereferenced by the hosts that tag actions by them and by the pen,
        // so a null is refused; the instant defaults
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(surfaceId, "surfaceId");
        at = at == null ? Instant.now() : at;
        action = action == null ? Map.of() : Map.copyOf(action);
        session = session == null ? "" : session;
    }

    /** The key of the action map that names a function call's id. */
    public static final String FUNCTION_CALL_ID = "functionCallId";

    // the accessors below have no get or is prefix, so a mapper that reads getters adds no field to the record's bytes

    /** Whether the action is a page's call to an agent function, which waits for an answer. */
    public boolean callsFunction() {
        return action.get(FUNCTION_CALL_ID) instanceof String id && !id.isBlank();
    }

    /** The call's id, or empty for an action that calls no function. */
    public String functionCallId() {
        return action.get(FUNCTION_CALL_ID) instanceof String id ? id : "";
    }

    /** The function a call names, or the action's name. */
    public String function() {
        return action.get("name") instanceof String name ? name : "";
    }

    /** The call's arguments: the action's context, empty when it has none. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> args() {
        return action.get("context") instanceof Map<?, ?> context ? (Map<String, Object>) context : Map.of();
    }
}
