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
 * One A2UI envelope of a surface (agentspaces-a2ui): the app, the surface id, the catalog id, the sequence number a
 * renderer replays in, the message (one of createSurface, updateComponents, updateDataModel, deleteSurface, with its
 * {@code version}), and the protocol version. Written by a surface agent into the group's {@code views} space. Version 1.
 */
public record Surface(int schemaVersion, String app, String surfaceId, String catalogId, long seq, Map<String, Object> message, String version, Instant at) {

    public static final int VERSION = 1;
    public static final String A2UI_VERSION = "v0.9.1";
    /** The A2UI basic catalog, the one every renderer has. */
    public static final String BASIC_CATALOG = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json";

    public Surface {
        // the app and the surface id are dereferenced by the hosts that sort surfaces by them (one null
        // would end a whole sort), so a null is refused; the instant defaults
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(surfaceId, "surfaceId");
        at = at == null ? Instant.now() : at;
        message = message == null ? Map.of() : Map.copyOf(message);
        version = version == null || version.isBlank() ? A2UI_VERSION : version;
        catalogId = catalogId == null ? "" : catalogId;
    }

    /** Which message this envelope carries: the one key beside {@code version}. */
    public String kind() {
        for (String k : message.keySet()) {
            if (!"version".equals(k)) {
                return k;
            }
        }
        return "";
    }
}
