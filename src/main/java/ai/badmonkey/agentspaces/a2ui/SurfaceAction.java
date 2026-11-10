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
 */
public record SurfaceAction(int schemaVersion, String app, String surfaceId, String session, Map<String, Object> action, Instant at) {

    public static final int VERSION = 1;

    public SurfaceAction {
        // the app and the surface id are dereferenced by the bridge's write set and the pen, so a null is
        // refused; the instant defaults
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(surfaceId, "surfaceId");
        at = at == null ? Instant.now() : at;
        action = action == null ? Map.of() : Map.copyOf(action);
        session = session == null ? "" : session;
    }
}
