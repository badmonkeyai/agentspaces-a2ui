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
                Map.of("version", "1.0", "createSurface", Map.of("surfaceId", "claim-77", "catalogId", Surface.BASIC_CATALOG)), "1.0", AT);
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

    @org.junit.jupiter.api.Test
    void envelopesAreA2uiOnePointZero() {
        // A2UI 1.0 (srad/agentspaces-geo.md phase 0, 2026-11-12): the version, the v1 basic catalog, and @path bindings
        org.assertj.core.api.Assertions.assertThat(Surface.A2UI_VERSION).isEqualTo("1.0");
        org.assertj.core.api.Assertions.assertThat(Surface.BASIC_CATALOG).isEqualTo("https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json");
        org.assertj.core.api.Assertions.assertThat(Surfaces.bound("t", "/name", "body").get("text")).isEqualTo(java.util.Map.of("@path", "/name"));
        // 1.0's Text has no heading variants: a heading is Markdown, and no variant outside caption and body is written
        org.assertj.core.api.Assertions.assertThat(Surfaces.text("h", "Claims", "h2")).containsEntry("text", "## Claims").doesNotContainKey("variant");
        org.assertj.core.api.Assertions.assertThat(Surfaces.bound("b", "/t", "h3")).doesNotContainKey("variant");
        org.assertj.core.api.Assertions.assertThat(Surfaces.text("c", "small", "caption")).containsEntry("variant", "caption");
        // 1.0's component ids are UAX #31 identifiers: a hyphen is refused, naming a form that is taken
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Surfaces.componentId("bar-menu")).hasMessageContaining("barMenu");
        Surfaces.componentId("barMenu");
        Surfaces.componentId("bar_menu");
        // a button names its label's id, and that id is an identifier too (it was id + "-text" under 0.9)
        Object child = Surfaces.button("approve", "Approve", "approve", java.util.Map.of(), "primary").get("child");
        org.assertj.core.api.Assertions.assertThat(child).isEqualTo("approveText");
        Surfaces.componentId(child);
    }
}
