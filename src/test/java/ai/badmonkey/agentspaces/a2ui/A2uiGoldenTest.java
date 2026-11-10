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

import ai.badmonkey.agentspaces.common.codec.CborCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The two records round-trip and keep their pinned bytes. */
class A2uiGoldenTest {

    static final CborCodec CODEC = CborCodec.defaultCodec();
    static final Instant AT = Instant.parse("2026-11-04T12:00:00Z");

    static Surface surface() {
        return new Surface(Surface.VERSION, "app-desk", "claim-77", Surface.BASIC_CATALOG, 1,
                Map.of("version", "v0.9.1", "createSurface", Map.of("surfaceId", "claim-77", "catalogId", Surface.BASIC_CATALOG)), "v0.9.1", AT);
    }

    static SurfaceAction action() {
        return new SurfaceAction(SurfaceAction.VERSION, "app-desk", "claim-77", "as-1",
                Map.of("name", "acknowledge", "surfaceId", "claim-77", "sourceComponentId", "ack", "timestamp", "2026-11-04T12:00:01Z", "context", Map.of("claimId", "c-77")), AT);
    }

    @Test
    void theRecordsRoundTripAndMatchTheirVectors() throws IOException {
        pin("surface_cbor", surface(), Surface.class);
        pin("surface_action_cbor", action(), SurfaceAction.class);
        assertThat(surface().kind()).isEqualTo("createSurface");
        assertThat(new Surface(Surface.VERSION, "a", "s", null, 1, null, null, AT).catalogId()).isEmpty();
        assertThat(new SurfaceAction(SurfaceAction.VERSION, "a", "s", null, null, AT).session()).isEmpty();
    }

    @Test
    void theBuildersShapeTheBasicCatalogComponents() {
        assertThat(Surfaces.text("t", "Hello", "body")).containsEntry("id", "t").containsEntry("component", "Text");
        assertThat(Surfaces.button("b", "Acknowledge", "acknowledge", Map.of("claimId", "c-77"), "primary")).containsEntry("component", "Button");
        assertThat(Surfaces.column("root", List.of("t", "b"))).containsEntry("component", "Column");
    }

    private static <T> void pin(String name, T value, Class<T> type) throws IOException {
        byte[] bytes = CODEC.toBytes(value);
        assertThat(CODEC.fromBytes(bytes, type)).isEqualTo(value);
        Path vector = Path.of("src/test/resources/vectors/" + name + ".hex");
        String hex = HexFormat.of().formatHex(bytes);
        if (!Files.exists(vector)) {
            Files.createDirectories(vector.getParent());
            Files.writeString(vector, hex + "\n", StandardCharsets.UTF_8);
        }
        assertThat(hex).as(name + " pins the canonical bytes").isEqualTo(Files.readString(vector, StandardCharsets.UTF_8).trim());
    }
}
