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
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The open visualisation catalog {@code agentspaces-viz} in the jar, one golden
 * surface per component, the Publish record pinned with {@code signOn} {@code idp}, and the look-and-feel schema beside
 * the catalog under its AgentSpaces {@code $id}.
 */
class VizCatalogTest {

    static final CborCodec CODEC = CborCodec.defaultCodec();
    static final Instant AT = Instant.parse("2026-11-06T09:00:00Z");

    @Test
    void theCatalogDeclaresItsComponentsAndTheirRules() {
        JsonNode catalog = VizCatalog.catalog();
        assertThat(VizCatalog.ID).isEqualTo("agentspaces-viz");
        assertThat(VizCatalog.CATALOG_PATH).isEqualTo("/catalogs/agentspaces-viz/catalog.json");
        assertThat(catalog.path("catalogId").asText()).isEqualTo(VizCatalog.ID);
        assertThat(catalog.path("title").asText()).isEqualTo("AgentSpaces visualisation catalog");
        assertThat(catalog.path("description").asText()).contains("the host's look and feel");
        assertThat(VizCatalog.componentNames()).containsExactlyElementsOf(VizCatalog.COMPONENTS);
        for (String name : VizCatalog.COMPONENTS) {
            JsonNode c = catalog.path("components").path(name);
            assertThat(c.path("description").asText()).as(name + " says what it is").isNotBlank();
            assertThat(c.path("required").isArray()).as(name + " names what it requires").isTrue();
        }
        assertThat(catalog.path("components").path("Chart").path("properties").path("kind").path("enum")).extracting(JsonNode::asText)
                .containsExactly("bar", "line", "area", "stat", "sparkline", "heatmap");
        // A2UI 1.0 (2026-11-12): the catalog is a 1.0 document; the rules ride in metadata.extensions and in instructions
        assertThat(catalog.path("protocolVersion").asText()).isEqualTo("1.0");
        JsonNode rules = catalog.path("metadata").path("extensions").path("agentspaces").path("rules");
        assertThat(rules.path("charts").asText()).contains("fixed order").contains("table view");
        assertThat(rules.path("noCode").asText()).contains("no script");
        assertThat(catalog.path("instructions").asText()).contains("fixed order");
        for (String name : List.of("Chart", "TitleBar", "Window")) {
            assertThat(catalog.path("components").path(name).path("properties").path("component").path("const").asText()).as(name).isEqualTo(name);
        }
        JsonNode title = Catalogs.resolve(catalog, catalog.path("components").path("TitleBar").path("properties").path("title"));
        assertThat(title.toString() + Catalogs.resolve(catalog, catalog.at("/$defs/DataBinding"))).as("a binding is 1.0's @path").contains("\"@path\"").doesNotContain("\"path\"");
    }

    /**
     * GEO-V-04: both catalogs of this jar are closed A2UI 1.0 documents in the basic catalog's shape: every component
     * schema says {@code additionalProperties: false} and declares the 1.0 envelope's own properties (id, catalogId,
     * accessibility, metadata), and {@code $defs} carries anyComponent (each component by reference), anyFunction, and
     * the shared types every local {@code $ref} reaches.
     */
    @Test
    void theOpenCatalogsAreClosedOnePointZeroDocumentsWithDefs() {
        for (JsonNode catalog : List.of(VizCatalog.catalog(), WidgetsCatalog.catalog())) {
            String id = catalog.path("catalogId").asText();
            List<String> refs = new java.util.ArrayList<>();
            catalog.path("$defs").path("anyComponent").path("oneOf").forEach(r -> refs.add(r.path("$ref").asText()));
            assertThat(refs).as(id).containsExactlyElementsOf(Catalogs.componentNames(catalog).stream().map(n -> "#/components/" + n).toList());
            assertThat(catalog.path("$defs").path("anyComponent").path("discriminator").path("propertyName").asText()).isEqualTo("component");
            assertThat(catalog.path("$defs").has("anyFunction")).as(id + " says it has no functions").isTrue();
            for (String name : Catalogs.componentNames(catalog)) {
                JsonNode c = catalog.path("components").path(name);
                assertThat(c.path("additionalProperties").isBoolean() && !c.path("additionalProperties").asBoolean()).as(id + "/" + name + " is closed").isTrue();
                assertThat(c.path("properties").has("id") && c.path("properties").has("catalogId") && c.path("properties").has("accessibility")
                        && c.path("properties").has("metadata")).as(id + "/" + name + " declares the 1.0 envelope's properties").isTrue();
                assertThat(c.has("oneOf") || c.has("anyOf")).as("a 1.0 component definition has no top-level oneOf or anyOf").isFalse();
            }
            everyLocalRefResolves(catalog, catalog, id);
        }
    }

    static void everyLocalRefResolves(JsonNode catalog, JsonNode node, String where) {
        if (node.has("$ref") && node.get("$ref").isTextual()) {
            String ref = node.get("$ref").asText();
            assertThat(ref.startsWith("#/") ? !catalog.at(ref.substring(1)).isMissingNode() : ref.startsWith("https://a2ui.org/specification/v1_0/common_types.json#/$defs/"))
                    .as(where + " reaches " + ref).isTrue();
        }
        node.fields().forEachRemaining(e -> everyLocalRefResolves(catalog, e.getValue(), where + "/" + e.getKey()));
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                everyLocalRefResolves(catalog, node.get(i), where + "/" + i);
            }
        }
    }

    @Test
    void theLookAndFeelSchemaBoundsTheTokens() {
        JsonNode schema = VizCatalog.lookAndFeelSchema();
        assertThat(schema.path("$id").asText()).isEqualTo("https://badmonkey.ai/agentspaces/a2ui/v1/look-and-feel.schema.json");
        assertThat(schema.path("required")).extracting(JsonNode::asText).contains("schemaVersion", "id", "tokens");
        JsonNode color = schema.path("$defs").path("mode").path("properties").path("color");
        assertThat(color.path("required")).extracting(JsonNode::asText).contains("categorical", "status");
        assertThat(color.path("properties").path("categorical").path("minItems").asInt()).isEqualTo(8);
        assertThat(schema.path("$defs").path("hex").path("pattern").asText()).isEqualTo("^#[0-9a-fA-F]{6}$");
    }

    @Test
    void theLineInkAndMonoRolesAreOptional() {
        // 0.7.0: four colour roles and a mono family a look may name; a look without them stays valid as it was
        JsonNode mode = VizCatalog.lookAndFeelSchema().path("$defs").path("mode");
        JsonNode color = mode.path("properties").path("color");
        for (String role : List.of("line", "lineStrong", "inkFaint", "accentInk")) {
            assertThat(color.path("properties").path(role).path("$ref").asText()).as(role).isEqualTo("#/$defs/hex");
            assertThat(color.path("properties").path(role).path("description").asText()).as(role).isNotBlank();
        }
        assertThat(color.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("surface", "ink", "accent", "categorical", "status");
        JsonNode type = mode.path("properties").path("type").path("properties");
        assertThat(type.path("mono").path("pattern").asText()).isEqualTo(type.path("family").path("pattern").asText());
        assertThat(mode.path("properties").path("type").has("required")).isFalse();
        assertThat(VizCatalog.lookAndFeelSchema().path("properties").path("schemaVersion").path("const").asInt()).isEqualTo(1);
    }

    @Test
    void everyBuilderNamesAComponentOfTheCatalogAndItsRequiredProperties() {
        Map<String, Supplier<Map<String, Object>>> builders = Map.ofEntries(
                Map.entry("Chart", () -> Surfaces.chart("spend", "bar", "Spend by group", List.of(Map.of("name", "spend", "path", "/spend")), "/groups", "USD")),
                Map.entry("Visualize", () -> Surfaces.visualize("spendChart", "/spend", "column", "region", "amount", Map.of("color", "quarter", "sort", "-y"))),
                Map.entry("GraphView", () -> Surfaces.graphView("links", "/nodes", "/edges", "layered", Map.of())),
                Map.entry("DataTable", () -> Surfaces.dataTable("claims", List.of(Map.of("key", "id", "title", "Claim")), "/claims", "open")),
                Map.entry("Map", () -> Surfaces.map("fleet", List.of(Map.of("kind", "markers", "path", "/positions", "key", "unit")), "inspect")),
                Map.entry("Graph", () -> Surfaces.graph("lineage", "/nodes", "/edges", "columns", "open")),
                Map.entry("Timeline", () -> Surfaces.timeline("events", "/events", "/lanes")),
                Map.entry("Report", () -> Surfaces.report("weekly", "Weekly report", List.of(Map.of("heading", "Spend", "kind", "text", "text", "Spend rose.")))),
                Map.entry("Notification", () -> Surfaces.notification("alerts", "/incidents", "acknowledge", "route")),
                Map.entry("Form", () -> Surfaces.form("claim", "https://acme.example/claims#RegisteredClaim", List.of("id", "amount"), "submit")),
                Map.entry("Stat", () -> Surfaces.stat("open", "Open claims", "/open", "", "/openDelta", "/openStatus")),
                Map.entry("Window", () -> Surfaces.window("box", "Truck 3", "note", "top-right")),
                Map.entry("Conversation", () -> Surfaces.conversation("chat", "/conversations", "fleet", "track-summary")),
                Map.entry("Notice", () -> Surfaces.notice("note", "warning", "No model bound", "/why", "dismiss")),
                Map.entry("Toolbar", () -> Surfaces.toolbar("bar", "Desk", List.of(Map.of("label", "Refresh", "action", "refresh")))),
                Map.entry("KeyValue", () -> Surfaces.keyValue("facts", "The desk", List.of(Map.of("label", "Open", "value", "/open")), "")),
                Map.entry("Prose", () -> Surfaces.prose("notes", "/notes")),
                Map.entry("TitleBar", () -> Surfaces.titleBar("bar", "Workbench", "", "", Map.of(), List.of(), "")),
                Map.entry("Menu", () -> Surfaces.menu("more", Surfaces.trigger("overflow", "More"), List.of(Surfaces.menuItem("help", "Help", "help")))),
                Map.entry("Split", () -> Surfaces.split("main", "horizontal", "", 0, List.of(Surfaces.pane("left", "tree", 0.5, 180, false, "Files"),
                        Surfaces.pane("right", "chat", 0.5, 300, true, "Chat")))));
        JsonNode catalog = VizCatalog.catalog();
        for (String name : VizCatalog.COMPONENTS) {
            Map<String, Object> component = builders.get(name).get();
            assertThat(component).containsEntry("component", name);
            for (JsonNode required : catalog.path("components").path(name).path("required")) {
                assertThat(component).as(name + " carries " + required.asText()).containsKey(required.asText());
            }
        }
    }

    @Test
    void visualizeAndGraphViewRefuseWhatTheCatalogDoesNotSay() {
        assertThatThrownBy(() -> Surfaces.visualize("v", "spend", "column", "x", "y", Map.of())).hasMessageContaining("/spend");
        assertThatThrownBy(() -> Surfaces.visualize("v", "/spend", "pie", "x", "y", Map.of())).hasMessageContaining("Visualize.mark takes");
        assertThatThrownBy(() -> Surfaces.visualize("v", "/spend", "column", "", "y", Map.of())).hasMessageContaining("x column");
        assertThatThrownBy(() -> Surfaces.visualize("v", "/spend", "column", "x", 3, Map.of())).hasMessageContaining("a column or a list");
        assertThatThrownBy(() -> Surfaces.visualize("v", "/spend", "column", "x", "y", Map.of("aggregate", "median"))).hasMessageContaining("Visualize.aggregate takes");
        assertThatThrownBy(() -> Surfaces.visualize("v", "/spend", "column", "x", "y", Map.of("palette", "rainbow"))).hasMessageContaining("no property palette");
        assertThat(Surfaces.visualize("v", "/spend", "line", "month", List.of("north", "south"), Map.of())).containsEntry("y", List.of("north", "south"));
        assertThatThrownBy(() -> Surfaces.graphView("g", "/nodes", null, "force", Map.of())).hasMessageContaining("nodes table and an edges table");
        assertThatThrownBy(() -> Surfaces.graphView("g", "/n", "/e", "circle", Map.of())).hasMessageContaining("GraphView.layout takes");
        assertThat(Surfaces.graphView("g", null, null, "force", Map.of("features", "/ships"))).containsEntry("features", "/ships").containsEntry("layout", "force");
    }

    @Test
    void theGoldenSurfacesAndThePublishArePinned() throws IOException {
        Surface chart = new Surface(Surface.VERSION, "app-ops", "spend", VizCatalog.ID, 2, Map.of("version", "1.0", "updateComponents",
                Map.of("surfaceId", "spend", "components", List.of(Surfaces.chart("spend", "bar", "Spend by group",
                        List.of(Map.of("name", "spend", "path", "/spend")), "/groups", "USD")))), "1.0", AT);
        pin("surface_viz_chart_cbor", chart, Surface.class);
        Surface map = new Surface(Surface.VERSION, "app-geo", "fleet", VizCatalog.ID, 2, Map.of("version", "1.0", "updateComponents",
                Map.of("surfaceId", "fleet", "components", List.of(Surfaces.map("fleet", List.of(Map.of("kind", "tracks", "path", "/tracks", "key", "unit")), "inspect")))), "1.0", AT);
        pin("surface_viz_map_cbor", map, Surface.class);
        Publish publish = new Publish(Publish.VERSION, "app-ops", "view-agent", List.of("spend", "overview"), "Operations", "", "idp", Map.of(), AT);
        pin("publish_cbor", publish, Publish.class);
        // AF-18: a conversation layout with a banner, the fifth golden of the follow-ups ticket
        Publish banner = new Publish(Publish.VERSION, "app-desk", "desk-agent", List.of("chat", "answer", "about"), "Claims desk", "", "idp",
                Map.of(Publish.LAYOUT, Publish.conversation("chat", 0.36, List.of(Publish.area("answer", "Answer", "file"), Publish.area("about", "About", "")),
                        "Claims", "Internal use only | Do not forward")), AT);
        pin("publish_conversation_banner_cbor", banner, Publish.class);
        assertThat(((Map<?, ?>) banner.options().get(Publish.LAYOUT)).get(Publish.BANNER)).isEqualTo("Internal use only | Do not forward");
        assertThat(Publish.SIGN_ONS).containsExactly("idp", "none");
        assertThat(new Publish(Publish.VERSION, "a", null, null, null, null, null, null, AT).signOn()).isEqualTo("idp");
        assertThatThrownBy(() -> new Publish(Publish.VERSION, "a", "", List.of(), "t", "", "password", Map.of(), AT)).hasMessageContaining("signOn");
        assertThatThrownBy(() -> new Publish(Publish.VERSION, "a", "", List.of(), "t", "", "tenant", Map.of(), AT))
                .as("v0.1.32: the retired sign-on word").hasMessageContaining("signOn");
        assertThatThrownBy(() -> new Publish(Publish.VERSION, "", "", List.of(), "t", "", "idp", Map.of(), AT)).hasMessageContaining("app");
    }

    /**
     * One golden surface per component of the catalogs a renderer draws, as JSON (the envelopes that
     * create, lay out, and feed each surface, and the text a person must find on the page), pinned in
     * {@code src/test/resources/golden/surfaces.json} for a renderer's conformance test to draw.
     */
    @Test
    void theGoldenSurfacesForTheRenderersArePinned() throws IOException {
        List<Map<String, Object>> surfaces = new java.util.ArrayList<>();
        surfaces.add(golden("Text", Surface.BASIC_CATALOG, List.of(Surfaces.column("root", List.of("title", "body")), Surfaces.text("title", "Claims this week", "h2"), Surfaces.bound("body", "/summary", "body")),
                Map.of("summary", "Twelve claims opened, nine closed."), List.of("Claims this week", "Twelve claims opened, nine closed.")));
        surfaces.add(golden("Chart", VizCatalog.ID, List.of(Surfaces.chart("spend", "bar", "Spend by group", List.of(Map.of("name", "spend", "path", "/spend"), Map.of("name", "budget", "path", "/budget")), "group", "USD")),
                Map.of("spend", List.of(Map.of("group", "claims-fleet", "y", 120), Map.of("group", "ops", "y", 80)), "budget", List.of(Map.of("group", "claims-fleet", "y", 150), Map.of("group", "ops", "y", 100))),
                List.of("Spend by group", "spend", "budget", "claims-fleet", "ops")));
        // GEO-D-01: a column chart split by a column, aggregated and sorted; GEO-G-01: a layered graph of two tables
        surfaces.addAll(chartGoldens());
        surfaces.add(golden("DataTable", VizCatalog.ID, List.of(Surfaces.dataTable("claims", List.of(Map.of("key", "id", "title", "Claim"), Map.of("key", "amount", "title", "Amount", "format", "number")), "/claims", "open")),
                Map.of("claims", List.of(Map.of("id", "C-1001", "amount", 1200), Map.of("id", "C-1002", "amount", 340))), List.of("Claim", "Amount", "C-1001", "C-1002")));
        // icons that move, tracks, areas, alert boxes, a highlight, both actions
        surfaces.add(golden("Map", VizCatalog.ID, List.of(Surfaces.map("fleet", List.of(
                        Surfaces.layer("icons", "/positions", "unit", "unit", Map.of("icon", "truck", "heading", "heading")),
                        Surfaces.layer("tracks", "/tracks", "unit", "", Map.of("trail", 20)),
                        Surfaces.layer("areas", "/areas", "id", "name", Map.of()),
                        Surfaces.layer("alerts", "/alerts", "key", "title", Map.of())), "inspect",
                        Map.of("highlight", "/highlight", "alertAction", "acknowledge", "transition", 800))),
                Map.of("positions", List.of(Map.of("unit", "truck-7", "lat", 51.5, "lon", -0.12, "heading", 45), Map.of("unit", "truck-9", "lat", 48.85, "lon", 2.35, "heading", 270)),
                        "tracks", List.of(Map.of("unit", "truck-7", "lat", 51.49, "lon", -0.13, "at", "2026-11-07T08:00:00Z"), Map.of("unit", "truck-7", "lat", 51.5, "lon", -0.12, "at", "2026-11-07T08:01:00Z")),
                        "areas", List.of(Map.of("id", "depot", "name", "Depot North", "polygon", List.of(List.of(51.52, -0.14), List.of(51.52, -0.10), List.of(51.48, -0.10), List.of(51.48, -0.14)))),
                        "alerts", List.of(Map.of("id", "a-1", "key", "truck-9", "severity", "serious", "title", "truck-9 left Depot North", "at", "2026-11-07T08:01:00Z")),
                        "highlight", List.of("truck-9")), List.of("truck-7", "truck-9", "left Depot North")));
        // AF-16: a DataTable whose row button reads its label, and a Graph that lights one path
        surfaces.add(golden("DataTable", "labelled", VizCatalog.ID, List.of(Surfaces.dataTable("pages", List.of(Map.of("key", "title", "title", "Page"), Map.of("key", "site", "title", "Site")),
                        "/pages", "pages.add", "Add the page")),
                Map.of("pages", List.of(Map.of("title", "Claims handbook", "site", "docs.example.com"), Map.of("title", "Receipts policy", "site", "docs.example.com"))),
                List.of("Page", "Site", "Claims handbook", "Receipts policy", "Add the page")));
        surfaces.add(golden("Graph", "lit", VizCatalog.ID, List.of(Surfaces.graph("route", "/nodes", "/edges", "columns", "")),
                Map.of("nodes", List.of(Map.of("id", "claim", "title", "Claim C-1001", "column", "asked", "lit", true), Map.of("id", "receipt", "title", "Missing receipt", "column", "found", "lit", true),
                                Map.of("id", "policy", "title", "Receipts policy", "column", "source", "lit", true), Map.of("id", "travel", "title", "Travel budget", "column", "found")),
                        "edges", List.of(Map.of("from", "claim", "to", "receipt", "label", "held for", "lit", true), Map.of("from", "receipt", "to", "policy", "label", "required by", "lit", true),
                                Map.of("from", "claim", "to", "travel", "label", "charged to"))),
                List.of("Claim C-1001", "Missing receipt", "Receipts policy", "Travel budget", "held for, on the path")));
        surfaces.add(golden("Graph", VizCatalog.ID, List.of(Surfaces.graph("lineage", "/nodes", "/edges", "columns", "open")),
                Map.of("nodes", List.of(Map.of("id", "claims-db", "title", "claims-db", "column", "source"), Map.of("id", "registrar", "title", "registrar", "column", "agent")), "edges", List.of(Map.of("from", "claims-db", "to", "registrar", "label", "reads"))),
                List.of("claims-db", "registrar")));
        surfaces.add(golden("Timeline", VizCatalog.ID, List.of(Surfaces.timeline("events", "/events", "/lanes")),
                Map.of("lanes", List.of("registrar", "adjuster"), "events", List.of(Map.of("at", "2026-11-06T09:00:00Z", "lane", "registrar", "title", "claim registered"), Map.of("at", "2026-11-06T09:05:00Z", "lane", "adjuster", "title", "claim adjusted"))),
                List.of("registrar", "adjuster", "claim registered", "claim adjusted")));
        surfaces.add(golden("Report", VizCatalog.ID, List.of(Surfaces.report("weekly", "Weekly report", List.of(Map.of("heading", "Spend", "kind", "text", "text", "Spend rose **four percent**.")))),
                Map.of(), List.of("Weekly report", "Spend", "Spend rose", "four percent")));
        surfaces.add(golden("Notification", VizCatalog.ID, List.of(Surfaces.notification("alerts", "/incidents", "acknowledge", "route")),
                Map.of("incidents", List.of(Map.of("id", "inc-1", "severity", "serious", "title", "Three refusals in a minute", "at", "2026-11-06T09:00:00Z", "group", "claims-fleet"))),
                List.of("Three refusals in a minute", "serious", "claims-fleet")));
        surfaces.add(golden("Form", VizCatalog.ID, List.of(Surfaces.form("claim", "https://acme.example/claims#RegisteredClaim", List.of("id", "amount"), "submit")),
                Map.of(), List.of("id", "amount")));
        surfaces.add(golden("Window", VizCatalog.ID, List.of(Surfaces.column("root", List.of("box")), Surfaces.window("box", "Truck 3", "note", "flow"), Surfaces.stat("note", "Speed", "/speed", "km/h", "", "")),
                Map.of("speed", 42), List.of("Truck 3", "42")));
        surfaces.add(golden("Conversation", VizCatalog.ID, List.of(Surfaces.conversation("chat", "/conversations", "fleet", "track-summary")),
                Map.of("conversations", List.of(Map.of("id", "m-1", "key", "fleet", "from", Map.of("kind", "person", "name", "ada"), "text", "Where is truck 7 heading?", "at", "2026-11-07T08:00:00Z"),
                        Map.of("id", "m-1-reply", "key", "fleet", "from", Map.of("kind", "agent", "name", "track-summary"), "text", "North on the A10, nothing to see.", "at", "2026-11-07T08:00:05Z", "inReplyTo", "m-1"))),
                List.of("Where is truck 7 heading?", "North on the A10")));
        surfaces.add(golden("Notice", VizCatalog.ID, List.of(Surfaces.notice("note", "warning", "No model bound", "/why", "dismiss")),
                Map.of("why", "Bind a provider on the Models screen."), List.of("warning", "No model bound", "Bind a provider")));
        surfaces.add(golden("Toolbar", VizCatalog.ID, List.of(Surfaces.toolbar("bar", "Desk", List.of(Map.of("label", "Refresh", "action", "refresh"), Map.of("label", "Export", "action", "export", "primary", true)))),
                Map.of(), List.of("Desk", "Refresh", "Export")));
        surfaces.add(golden("KeyValue", VizCatalog.ID, List.of(Surfaces.keyValue("facts", "The desk", List.of(Map.of("label", "Open", "value", "/open"), Map.of("label", "Region", "value", "north")), "")),
                Map.of("open", 42), List.of("The desk", "Open", "42", "Region", "north")));
        surfaces.add(golden("Prose", VizCatalog.ID, List.of(Surfaces.prose("notes", "/notes")),
                Map.of("notes", "Spend rose **four percent** this week."), List.of("Spend rose", "four percent")));
        surfaces.add(golden("Stat", VizCatalog.ID, List.of(Surfaces.stat("open", "Open claims", "/open", "claims", "/openDelta", "/openStatus")),
                Map.of("open", 42, "openDelta", -3, "openStatus", "good"), List.of("Open claims", "42", "claims")));
        surfaces.addAll(frameGoldens());
        surfaces.addAll(appGoldens());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("surfaces", surfaces)) + "\n";
        Path golden = Path.of("src/test/resources/golden/surfaces.json");
        if (!Files.exists(golden)) {
            Files.createDirectories(golden.getParent());
            Files.writeString(golden, json, StandardCharsets.UTF_8);
        }
        assertThat(json).as("the golden surfaces are pinned; delete the file to regenerate").isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
        assertThat(surfaces).extracting(m -> m.get("name")).contains("Text").containsAll(VizCatalog.COMPONENTS);
        for (String frame : List.of("Visualize", "GraphView", "TitleBar", "Menu", "Split", "Chat", "FileTree", "FilePreview")) {
            // the four states first; a variant after them shows one more way to use the component (Chat's follow-ups, GraphView's lit path)
            assertThat(surfaces.stream().filter(g -> frame.equals(g.get("name"))).map(g -> g.get("state")).toList()).as(frame + " in four states")
                    .startsWith("full", "empty", "loading", "error");
        }
        assertThat(surfaces.stream().filter(g -> "followups".equals(g.get("state")) || "lit".equals(g.get("state")) || "labelled".equals(g.get("state")))
                .map(g -> g.get("name") + "/" + g.get("state")).toList()).as("AF-16's goldens")
                .containsExactlyInAnyOrder("DataTable/labelled", "Graph/lit", "GraphView/lit", "Chat/followups");
        assertThat(surfaces.stream().map(g -> g.get("surfaceId")).distinct().count()).as("one surface id per golden").isEqualTo(surfaces.size());
    }

    /**
     * GEO-Q-31: the components of srad/agentspaces-geo.md in agentspaces-viz (Visualize, GraphView), each in four states:
     * the chart or the graph, a bound table with no row yet, the table not arrived yet, and a value at the path that does
     * not read as a table, so the gallery shows what a person sees before the data and when it is wrong.
     */
    static List<Map<String, Object>> chartGoldens() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        Map<String, Object> chart = Surfaces.visualize("spendChart", "/spend", "column", "region", "amount",
                Map.of("color", "quarter", "aggregate", "sum", "sort", "-y", "title", "Spend by region", "unit", "USD"));
        out.add(golden("Visualize", "full", VizCatalog.ID, List.of(chart),
                Map.of("spend", List.of(Map.of("region", "north", "quarter", "Q1", "amount", 120), Map.of("region", "north", "quarter", "Q2", "amount", 90),
                        Map.of("region", "south", "quarter", "Q1", "amount", 60), Map.of("region", "south", "quarter", "Q1", "amount", 15), Map.of("region", "south", "quarter", "Q2", "amount", 40))),
                List.of("Spend by region", "north", "south", "Q1", "Q2")));
        out.add(golden("Visualize", "empty", VizCatalog.ID, List.of(chart), Map.of("spend", List.of()), List.of("Spend by region", "No rows yet")));
        out.add(golden("Visualize", "loading", VizCatalog.ID, List.of(chart), Map.of(), List.of("Spend by region", "Loading the rows")));
        out.add(golden("Visualize", "error", VizCatalog.ID, List.of(chart), Map.of("spend", "not a list"), List.of("Spend by region", "Unavailable", "do not read as a table")));
        Map<String, Object> graph = Surfaces.graphView("lineage", "/nodes", "/edges", "layered", Map.of("title", "Who feeds whom"));
        out.add(golden("GraphView", "full", VizCatalog.ID, List.of(graph),
                Map.of("nodes", List.of(Map.of("id", "claimsDb", "label", "claims-db", "kind", "source"), Map.of("id", "registrar", "label", "registrar", "kind", "agent"),
                        Map.of("id", "adjuster", "label", "adjuster", "kind", "agent")),
                        "edges", List.of(Map.of("source", "claimsDb", "target", "registrar", "rel", "reads"), Map.of("source", "registrar", "target", "adjuster", "rel", "hands over"))),
                List.of("Who feeds whom", "claims-db", "registrar", "adjuster")));
        out.add(golden("GraphView", "empty", VizCatalog.ID, List.of(graph), Map.of("nodes", List.of(), "edges", List.of()), List.of("Who feeds whom", "No graph yet", "No nodes yet")));
        out.add(golden("GraphView", "loading", VizCatalog.ID, List.of(graph), Map.of(), List.of("Who feeds whom", "Loading the graph")));
        out.add(golden("GraphView", "error", VizCatalog.ID, List.of(graph), Map.of("nodes", "not a list", "edges", List.of()),
                List.of("Who feeds whom", "Unavailable", "do not read as a table")));
        // AF-11, AF-16: the rows on a path marked lit; the view draws them as selected, beside a person's own selection
        Map<String, Object> path = Surfaces.graphView("path", "/nodes", "/edges", "layered", Map.of("title", "Why the claim is held"));
        out.add(golden("GraphView", "lit", VizCatalog.ID, List.of(path),
                Map.of("nodes", List.of(Map.of("id", "claim", "label", "Claim C-1001", "kind", "claim", "lit", true), Map.of("id", "receipt", "label", "Missing receipt", "kind", "finding", "lit", true),
                                Map.of("id", "policy", "label", "Receipts policy", "kind", "source", "lit", true), Map.of("id", "travel", "label", "Travel budget", "kind", "finding")),
                        "edges", List.of(Map.of("source", "claim", "target", "receipt", "rel", "held for", "lit", true), Map.of("source", "receipt", "target", "policy", "rel", "required by", "lit", true),
                                Map.of("source", "claim", "target", "travel", "rel", "charged to"))),
                List.of("Why the claim is held", "Claim C-1001", "Missing receipt", "Receipts policy", "Travel budget")));
        return out;
    }

    /**
     * Spiral 6 S6-U-25: the frame components, each in four states (full, empty, loading, error), so the gallery shows what
     * a person sees before the data arrives and when it is wrong as well as when it is whole.
     */
    static List<Map<String, Object>> frameGoldens() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        Map<String, Object> status = Surfaces.titleStatus("/working", "Agent working", "Ready");
        List<Map<String, Object>> barMenu = List.of(Surfaces.menuItem("new-chat", "New chat", "chat.new"), Surfaces.menuItem("help", "Help", "help"));
        Map<String, Object> share = Surfaces.titleAction("share", "Share", "share", "share");
        out.add(golden("TitleBar", "full", VizCatalog.ID, List.of(Surfaces.column("root", List.of("bar")),
                        Surfaces.titleBar("bar", Map.of("@path", "/app/title"), "Claims desk", "briefcase", status, List.of(share), "barMenu"),
                        Surfaces.menu("barMenu", Surfaces.trigger("overflow", "More"), barMenu)),
                Map.of("app", Map.of("title", "Workbench"), "working", List.of(Map.of("id", "working-workbench-main", "agent", "workbench", "key", "main",
                        "files", List.of("data/q3-expenses.csv"), "task", "reading q3-expenses.csv", "state", "working", "since", "2026-11-10T09:00:00Z"))),
                List.of("Workbench", "Claims desk", "Agent working", "Share", "More")));
        out.add(golden("TitleBar", "empty", VizCatalog.ID, List.of(Surfaces.titleBar("bar", "Workbench", "", "", Map.of(), List.of(), "")), Map.of(), List.of("Workbench")));
        out.add(golden("TitleBar", "loading", VizCatalog.ID, List.of(Surfaces.titleBar("bar", Map.of("@path", "/app/title"), "", "", status, List.of(), "")),
                Map.of(), List.of("Loading")));
        out.add(golden("TitleBar", "error", VizCatalog.ID, List.of(Surfaces.titleBar("bar", "Workbench", "", "", status, List.of(), "")),
                Map.of("working", List.of(Map.of("id", "working-workbench-main", "agent", "workbench", "key", "main", "files", List.of(), "task", "",
                        "state", "failed", "since", "2026-11-10T09:00:00Z"))), List.of("Workbench", "The agent stopped")));
        List<Map<String, Object>> items = List.of(
                Surfaces.menuItem("new-chat", "New chat", "chat.new", Map.of("shortcut", "Ctrl+Shift+O")),
                Surfaces.menuItem("dark", "Dark mode", "view.dark", Map.of("checked", Map.of("@path", "/view/dark"))),
                Surfaces.menuItem("export", "Export", "export", Map.of("disabled", true)),
                Surfaces.submenu("share", "Share", List.of(Surfaces.menuItem("copy-link", "Copy link", "share.link"), Surfaces.menuItem("email", "Email", "share.email"))),
                Surfaces.menuItem("help", "Help", "help", Map.of("group", "about")));
        out.add(golden("Menu", "full", VizCatalog.ID, List.of(Surfaces.menu("menu", Surfaces.trigger("button", "Actions"), items)), Map.of("view", Map.of("dark", false)),
                List.of("Actions")));
        out.add(golden("Menu", "empty", VizCatalog.ID, List.of(Surfaces.menu("menu", Surfaces.trigger("button", "Actions"), List.of())), Map.of(), List.of("Actions", "None")));
        out.add(golden("Menu", "loading", VizCatalog.ID, List.of(Surfaces.menu("menu", Surfaces.trigger("button", "Actions"), "/menu")), Map.of(), List.of("Actions", "Loading")));
        out.add(golden("Menu", "error", VizCatalog.ID, List.of(Surfaces.menu("menu", Surfaces.trigger("button", "Actions"), "/menu")), Map.of("menu", "not a list"),
                List.of("Actions", "Unavailable")));
        List<Map<String, Object>> three = List.of(Surfaces.pane("left", "files", 0.25, 180, true, "Files"), Surfaces.pane("mid", "preview", 0.45, 320, false, "Preview"),
                Surfaces.pane("right", "chat", 0.30, 300, true, "Chat"));
        out.add(golden("Split", "full", VizCatalog.ID, List.of(Surfaces.column("root", List.of("main")), Surfaces.split("main", "horizontal", "workbench-main", 720, three),
                        Surfaces.text("files", "notes/summary.md", "body"), Surfaces.prose("preview", "/note"), Surfaces.text("chat", "Ask about a file", "body")),
                Map.of("note", "Spend rose **four percent**."), List.of("Files", "Preview", "Chat", "notes/summary.md", "Spend rose", "Ask about a file")));
        out.add(golden("Split", "empty", VizCatalog.ID, List.of(Surfaces.split("main", "horizontal", "", 0, List.of(Surfaces.pane("left", "", 0.5, 0, false, "Files"),
                Surfaces.pane("right", "", 0.5, 0, false, "Chat")))), Map.of(), List.of("Files", "Chat", "Nothing here yet")));
        out.add(golden("Split", "loading", VizCatalog.ID, List.of(Surfaces.column("root", List.of("main")), Surfaces.split("main", "vertical", "", 0,
                        List.of(Surfaces.pane("top", "open", 0.5, 0, false, "Open"), Surfaces.pane("bottom", "closed", 0.5, 0, false, "Closed"))),
                Surfaces.stat("open", "Open claims", "/open", "", "", ""), Surfaces.stat("closed", "Closed claims", "/closed", "", "", "")),
                Map.of(), List.of("Open claims", "Closed claims")));
        out.add(golden("Split", "error", VizCatalog.ID, List.of(Surfaces.split("main", "horizontal", "", 0, List.of(Surfaces.pane("left", "gone", 0.5, 0, false, "Files"),
                Surfaces.pane("right", "", 0.5, 0, false, "Chat")))), Map.of(), List.of("Files", "Chat", "Missing content: gone")));
        return out;
    }

    /**
     * Spiral 6 S6-U-25 for the application components of {@code agentspaces-widgets} (Chat, FileTree, FilePreview),
     * each in four states: what a person sees with the data, before it, while the agent works or the rows load, and
     * when it goes wrong (the agent stopped, a list that does not read, a file too large to preview).
     */
    static List<Map<String, Object>> appGoldens() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        String w = WidgetsCatalog.ID;
        Map<String, Object> q3 = row("schemaVersion", 2, "id", "f-q3", "name", "q3-expenses.csv", "mediaType", "text/csv", "bytes", 1840, "root", "r-q3",
                "sha256", "sha256:00", "by", "ana", "sharedWith", List.of(), "at", "2026-11-10T09:00:00Z", "path", "data/q3-expenses.csv", "replaces", "");
        Map<String, Object> note = row("schemaVersion", 2, "id", "f-note", "name", "overspend.md", "mediaType", "text/markdown", "bytes", 420, "root", "r-note",
                "sha256", "sha256:01", "by", "workbench", "sharedWith", List.of(), "at", "2026-11-10T09:01:00Z", "path", "notes/overspend.md", "replaces", "");
        Map<String, Object> question = row("schemaVersion", 1, "id", "m-1", "key", "main", "from", Map.of("kind", "person", "name", "ana", "peer", ""),
                "text", "Where did we overspend in Q3?", "attachments", List.of("f-q3"), "inReplyTo", "", "at", "2026-11-10T09:00:30Z");
        Map<String, Object> answer = row("schemaVersion", 2, "id", "m-2", "key", "main", "from", Map.of("kind", "agent", "name", "workbench", "peer", ""),
                "text", "Travel ran **18 percent** over its budget.", "attachments", List.of(), "inReplyTo", "m-1", "at", "2026-11-10T09:01:00Z",
                "format", "text/markdown", "cites", List.of(Map.of("file", "f-q3", "path", "data/q3-expenses.csv", "name", "q3-expenses.csv")));
        Map<String, Object> working = row("schemaVersion", 1, "id", "progress-m-1", "key", "main", "inReplyTo", "m-1", "agent", "workbench", "state", "working",
                "steps", List.of(Map.of("label", "read q3-expenses.csv", "kind", "read", "ref", "f-q3", "at", "2026-11-10T09:00:31Z")), "at", "2026-11-10T09:00:31Z");
        Map<String, Object> options = Map.of("suggestions", List.of("Summarise the newest file"), "attach", true, "format", "text/markdown",
                "controls", List.of("copy", "retry", "stop", "feedback"));
        out.add(golden("Chat", "full", w, List.of(Surfaces.chat("chat", "workbench", "main", options)),
                Map.of("conversations", List.of(question, answer), "files", List.of(q3, note), "progress", List.of()),
                List.of("workbench", "Where did we overspend in Q3?", "Travel ran", "18 percent", "q3-expenses.csv", "Send")));
        out.add(golden("Chat", "empty", w, List.of(Surfaces.chat("chat", "workbench", "main", options)), Map.of(),
                List.of("workbench", "No message yet", "Summarise the newest file", "Send")));
        out.add(golden("Chat", "loading", w, List.of(Surfaces.chat("chat", "workbench", "main", options)),
                Map.of("conversations", List.of(question), "files", List.of(q3), "progress", List.of(working)),
                List.of("Where did we overspend in Q3?", "working: read q3-expenses.csv")));
        Map<String, Object> failed = new java.util.LinkedHashMap<>(working);
        failed.put("state", "failed");
        out.add(golden("Chat", "error", w, List.of(Surfaces.chat("chat", "workbench", "main", options)),
                Map.of("conversations", List.of(question), "files", List.of(q3), "progress", List.of(failed)),
                List.of("Where did we overspend in Q3?", "The agent stopped", "Retry")));
        // AF-1 to AF-8, AF-16, AF-19: the follow-ups bound to the agent's rows, and the thumbs; a row with no line always
        // draws, a row of the newest answer draws, a row of an older answer does not, and a label stands for a long text
        Map<String, Object> followups = Surfaces.chat("chat", "workbench", "main", Map.of("suggestions", Map.of("@path", "/followups"),
                "controls", List.of("copy", "feedback"), "format", "text/markdown"));
        out.add(golden("Chat", "followups", w, List.of(followups),
                Map.of("conversations", List.of(question, answer), "files", List.of(q3), "progress", List.of(),
                        "followups", List.of(row("text", "Which receipts are missing?", "line", "m-2"),
                                row("text", "Which travel claims of Q3 ran over their budget, which receipts does each lack, and who approved each of them?",
                                        "label", "Travel claims over budget", "line", "m-2"),
                                row("text", "What does the receipts policy say?"),
                                row("text", "What was the Q2 total?", "line", "m-0"))),
                List.of("Travel ran", "Which receipts are missing?", "Travel claims over budget", "What does the receipts policy say?")));
        Map<String, Object> holding = row("schemaVersion", 1, "id", "working-workbench-main", "agent", "workbench", "key", "main",
                "files", List.of("data/q3-expenses.csv"), "task", "reading q3-expenses.csv", "state", "working", "since", "2026-11-10T09:00:31Z");
        Map<String, Object> treeOptions = Map.of("working", "/working", "upload", true, "label", "Files", "actions", List.of("ask", "download", "share", "rename", "remove"));
        out.add(golden("FileTree", "full", w, List.of(Surfaces.fileTree("tree", "/files", treeOptions)), Map.of("files", List.of(q3, note), "working", List.of(holding)),
                List.of("Files", "data", "q3-expenses.csv", "notes", "overspend.md", "workbench")));
        out.add(golden("FileTree", "empty", w, List.of(Surfaces.fileTree("tree", "/files", treeOptions)), Map.of("files", List.of()), List.of("Files", "No file yet", "Upload")));
        out.add(golden("FileTree", "loading", w, List.of(Surfaces.fileTree("tree", "/files", treeOptions)), Map.of(), List.of("Files", "Loading files")));
        out.add(golden("FileTree", "error", w, List.of(Surfaces.fileTree("tree", "/files", treeOptions)), Map.of("files", "not a list"), List.of("Files", "Unavailable")));
        Map<String, Object> pick = Map.of("@path", "/selected/file");
        out.add(golden("FilePreview", "full", w, List.of(Surfaces.filePreview("preview", pick, Map.of("files", "/files"))), Map.of("files", List.of(q3, note), "selected", Map.of("file", "f-q3")),
                List.of("q3-expenses.csv", "text/csv", "1.8 KiB", "Download")));
        out.add(golden("FilePreview", "empty", w, List.of(Surfaces.filePreview("preview", pick, Map.of("files", "/files"))), Map.of("files", List.of(q3)),
                List.of("Pick a file to preview")));
        out.add(golden("FilePreview", "loading", w, List.of(Surfaces.filePreview("preview", pick, Map.of("files", "/files"))), Map.of("selected", Map.of("file", "f-q3")),
                List.of("Loading")));
        Map<String, Object> large = new java.util.LinkedHashMap<>(q3);
        large.put("bytes", 9_000_000);
        out.add(golden("FilePreview", "error", w, List.of(Surfaces.filePreview("preview", pick, Map.of("files", "/files"))), Map.of("files", List.of(large), "selected", Map.of("file", "f-q3")),
                List.of("q3-expenses.csv", "Too large to preview", "Download")));
        return out;
    }

    /** A record's fields as a page reads them, in the order given. */
    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            m.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> golden(String name, String state, String catalogId, List<Map<String, Object>> components, Map<String, Object> data, List<String> expectText) {
        Map<String, Object> g = golden(name, catalogId, components, data, expectText);
        g.put("state", state);
        if (!"full".equals(state)) {
            String id = name.toLowerCase() + "-" + state;
            g.put("surfaceId", id);
            g.put("envelopes", ((List<?>) g.get("envelopes")).stream().map(e -> retarget((Map<?, ?>) e, id)).toList());
        }
        return g;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> retarget(Map<?, ?> envelope, String surfaceId) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        envelope.forEach((k, v) -> {
            if (v instanceof Map<?, ?> body) {
                Map<String, Object> b = new java.util.LinkedHashMap<>((Map<String, Object>) body);
                b.put("surfaceId", surfaceId);
                out.put((String) k, b);
            } else {
                out.put((String) k, v);
            }
        });
        return out;
    }

    /** AF-9 to AF-14: the catalog says what a lit row does in Graph and GraphView, and DataTable takes a row button's label. */
    @Test
    void aPathLightsAndARowButtonReadsItsLabel() {
        JsonNode c = VizCatalog.catalog().path("components");
        for (String name : List.of("Graph", "GraphView")) {
            assertThat(c.path(name).path("properties").path("nodes").path("description").asText()).as(name + " nodes").contains("lit: true");
            assertThat(c.path(name).path("properties").path("edges").path("description").asText()).as(name + " edges").contains("lit: true");
            assertThat(c.path(name).path("description").asText()).as(name).contains(", on the path");
        }
        assertThat(c.path("GraphView").path("description").asText()).contains("selected style", "accent stroke");
        assertThat(c.path("Graph").path("description").asText()).contains("only when it is lit, hovered, or touches the hovered or focused node");
        JsonNode label = c.path("DataTable").path("properties").path("rowActionLabel");
        assertThat(label.path("type").asText()).isEqualTo("string");
        assertThat(label.path("description").asText()).contains("first column");
        // the builder: a label beside its action, the four-argument form as before, and no label without an action
        Map<String, Object> table = Surfaces.dataTable("pages", List.of(Map.of("key", "title")), "/pages", "pages.add", "  Add the page ");
        assertThat(table).containsEntry("rowAction", "pages.add").containsEntry("rowActionLabel", "Add the page");
        assertThat(Surfaces.dataTable("pages", List.of(Map.of("key", "title")), "/pages", "pages.add")).doesNotContainKey("rowActionLabel");
        assertThat(Surfaces.dataTable("pages", List.of(Map.of("key", "title")), "/pages", "pages.add", "")).doesNotContainKey("rowActionLabel");
        assertThat(Surfaces.dataTable("pages", List.of(Map.of("key", "title")), "/pages", "", null)).doesNotContainKey("rowAction").doesNotContainKey("rowActionLabel");
        assertThatThrownBy(() -> Surfaces.dataTable("pages", List.of(Map.of("key", "title")), "/pages", "", "Add the page")).hasMessageContaining("rowActionLabel names the button of a rowAction");
        // lit is row data: Graph and GraphView take no new option (AF-15)
        assertThatThrownBy(() -> Surfaces.graphView("g", "/n", "/e", "force", Map.of("lit", true))).hasMessageContaining("no property lit");
    }

    @Test
    void theFrameComponentsCarryTheirShapes() {
        JsonNode c = VizCatalog.catalog().path("components");
        assertThat(c.path("TitleBar").path("required")).extracting(JsonNode::asText).containsExactly("component", "title");   // the 1.0 form names the component
        assertThat(c.path("Menu").path("properties").path("trigger").path("properties").path("kind").path("enum")).extracting(JsonNode::asText)
                .containsExactly("button", "overflow", "context");
        assertThat(c.path("Split").path("properties").path("panes").path("minItems").asInt()).isEqualTo(2);
        assertThat(c.path("Split").path("properties").path("panes").path("maxItems").asInt()).isEqualTo(3);
        assertThat(c.path("Split").path("properties").path("orientation").path("enum")).extracting(JsonNode::asText).containsExactly("horizontal", "vertical");
        assertThat(c.path("Window").path("properties").has("actions")).as("S6-U-03").isTrue();
        assertThat(Catalogs.resolve(VizCatalog.catalog(), c.path("Toolbar").path("properties").path("menu")).path("type").asText()).as("S6-U-02").isEqualTo("string");
        // the builders' shapes, as SRAD §3.5 gives them
        Map<String, Object> bar = Surfaces.titleBar("bar", Map.of("@path", "/app/title"), "Claims desk", "briefcase", Surfaces.titleStatus("/working", "Agent working", "Ready"),
                List.of(Surfaces.titleAction("share", "Share", "share", "share")), "barMenu");
        assertThat(bar).containsEntry("title", Map.of("@path", "/app/title")).containsEntry("menu", "barMenu")
                .containsEntry("status", Map.of("from", "/working", "labels", Map.of("working", "Agent working", "idle", "Ready")));
        assertThat(bar.get("actions")).isEqualTo(List.of(Map.of("id", "share", "label", "Share", "icon", "share", "action", Map.of("name", "share"))));
        assertThat(Surfaces.titleBar("bar", "Workbench", null, null, null, null, null)).containsOnlyKeys("id", "component", "title");
        Map<String, Object> sub = Surfaces.submenu("share", "Share", List.of(Surfaces.menuItem("copy", "Copy link", "share.link")));
        assertThat(sub).containsEntry("items", List.of(Map.of("id", "copy", "label", "Copy link", "action", Map.of("name", "share.link")))).doesNotContainKey("action");
        assertThatThrownBy(() -> Surfaces.submenu("a", "A", List.of(sub))).as("one level of submenu").hasMessageContaining("one level");
        assertThat(Surfaces.pane("left", "tree", 0.25, 180, true, "Files")).isEqualTo(Map.of("id", "left", "child", "tree", "size", 0.25, "min", 180, "collapsible", true, "label", "Files"));
        assertThat(Surfaces.split("main", "horizontal", "workbench-main", 720, List.of(Surfaces.pane("a", "x", 0.5, 0, false, ""), Surfaces.pane("b", "y", 0.5, 0, false, ""))))
                .containsEntry("persist", "workbench-main").containsEntry("stackBelow", 720).containsEntry("orientation", "horizontal");
        assertThatThrownBy(() -> Surfaces.split("main", "horizontal", "", 0, List.of(Surfaces.pane("a", "x", 1.0, 0, false, "")))).hasMessageContaining("two or three");
        assertThatThrownBy(() -> Surfaces.split("main", "diagonal", "", 0, List.of(Surfaces.pane("a", "x", 0.5, 0, false, ""), Surfaces.pane("b", "y", 0.5, 0, false, ""))))
                .hasMessageContaining("orientation");
        assertThat(Surfaces.window("box", "Truck 3", "note", "flow", List.of(Surfaces.titleAction("ack", "Acknowledge", "", "acknowledge"))).get("actions"))
                .isEqualTo(List.of(Map.of("id", "ack", "label", "Acknowledge", "action", Map.of("name", "acknowledge"))));
        assertThat(Surfaces.toolbar("tools", "Desk", List.of(), "tools-menu")).containsEntry("menu", "tools-menu");
    }

    /** One golden surface: its envelopes (create, components, data) and the text a person must find when it is drawn. */
    private static Map<String, Object> golden(String name, String catalogId, List<Map<String, Object>> components, Map<String, Object> data, List<String> expectText) {
        String id = name.toLowerCase();
        List<Map<String, Object>> envelopes = new java.util.ArrayList<>();
        envelopes.add(Map.of("version", Surface.A2UI_VERSION, "createSurface", Map.of("surfaceId", id, "catalogId", catalogId)));
        List<Map<String, Object>> all = new java.util.ArrayList<>(components);
        if (components.stream().noneMatch(c -> "root".equals(c.get("id")))) {
            all.add(0, Surfaces.column("root", components.stream().map(c -> String.valueOf(c.get("id"))).toList()));
        }
        envelopes.add(Map.of("version", Surface.A2UI_VERSION, "updateComponents", Map.of("surfaceId", id, "components", all)));
        if (!data.isEmpty()) {
            envelopes.add(Map.of("version", Surface.A2UI_VERSION, "updateDataModel", Map.of("surfaceId", id, "path", "/", "value", new java.util.TreeMap<>(data))));
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("name", name);
        out.put("surfaceId", id);
        out.put("catalogId", catalogId);
        out.put("envelopes", envelopes);
        out.put("expectText", expectText);
        return out;
    }

    private static <T> void pin(String name, T value, Class<T> type) throws IOException {
        byte[] bytes = CODEC.toBytes(value);
        assertThat(CODEC.fromBytes(bytes, type)).isEqualTo(value);
        Path vector = Path.of("src/test/resources/vectors/" + name + ".hex");
        String hex = HexFormat.of().formatHex(bytes);
        if (!Files.exists(vector)) {
            Files.writeString(vector, hex + "\n", StandardCharsets.UTF_8);
        }
        assertThat(hex).as(name + " pins the canonical bytes").isEqualTo(Files.readString(vector, StandardCharsets.UTF_8).trim());
    }
}
