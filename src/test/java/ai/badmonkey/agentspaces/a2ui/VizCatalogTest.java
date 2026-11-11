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
        assertThat(catalog.path("rules").path("charts").asText()).contains("fixed order").contains("table view");
        assertThat(catalog.path("rules").path("noCode").asText()).contains("no script");
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
    void everyBuilderNamesAComponentOfTheCatalogAndItsRequiredProperties() {
        Map<String, Supplier<Map<String, Object>>> builders = Map.ofEntries(
                Map.entry("Chart", () -> Surfaces.chart("spend", "bar", "Spend by group", List.of(Map.of("name", "spend", "path", "/spend")), "/groups", "USD")),
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
    void theGoldenSurfacesAndThePublishArePinned() throws IOException {
        Surface chart = new Surface(Surface.VERSION, "app-ops", "spend", VizCatalog.ID, 2, Map.of("version", "v0.9.1", "updateComponents",
                Map.of("surfaceId", "spend", "components", List.of(Surfaces.chart("spend", "bar", "Spend by group",
                        List.of(Map.of("name", "spend", "path", "/spend")), "/groups", "USD")))), "v0.9.1", AT);
        pin("surface_viz_chart_cbor", chart, Surface.class);
        Surface map = new Surface(Surface.VERSION, "app-geo", "fleet", VizCatalog.ID, 2, Map.of("version", "v0.9.1", "updateComponents",
                Map.of("surfaceId", "fleet", "components", List.of(Surfaces.map("fleet", List.of(Map.of("kind", "tracks", "path", "/tracks", "key", "unit")), "inspect")))), "v0.9.1", AT);
        pin("surface_viz_map_cbor", map, Surface.class);
        Publish publish = new Publish(Publish.VERSION, "app-ops", "view-agent", List.of("spend", "overview"), "Operations", "", "idp", Map.of(), AT);
        pin("publish_cbor", publish, Publish.class);
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
        for (String frame : List.of("TitleBar", "Menu", "Split", "Chat", "FileTree", "FilePreview")) {
            assertThat(surfaces.stream().filter(g -> frame.equals(g.get("name"))).map(g -> g.get("state")).toList()).as(frame + " in four states")
                    .containsExactly("full", "empty", "loading", "error");
        }
        assertThat(surfaces.stream().map(g -> g.get("surfaceId")).distinct().count()).as("one surface id per golden").isEqualTo(surfaces.size());
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
                        Surfaces.titleBar("bar", Map.of("path", "/app/title"), "Claims desk", "briefcase", status, List.of(share), "bar-menu"),
                        Surfaces.menu("bar-menu", Surfaces.trigger("overflow", "More"), barMenu)),
                Map.of("app", Map.of("title", "Workbench"), "working", List.of(Map.of("id", "working-workbench-main", "agent", "workbench", "key", "main",
                        "files", List.of("data/q3-expenses.csv"), "task", "reading q3-expenses.csv", "state", "working", "since", "2026-11-10T09:00:00Z"))),
                List.of("Workbench", "Claims desk", "Agent working", "Share", "More")));
        out.add(golden("TitleBar", "empty", VizCatalog.ID, List.of(Surfaces.titleBar("bar", "Workbench", "", "", Map.of(), List.of(), "")), Map.of(), List.of("Workbench")));
        out.add(golden("TitleBar", "loading", VizCatalog.ID, List.of(Surfaces.titleBar("bar", Map.of("path", "/app/title"), "", "", status, List.of(), "")),
                Map.of(), List.of("Loading")));
        out.add(golden("TitleBar", "error", VizCatalog.ID, List.of(Surfaces.titleBar("bar", "Workbench", "", "", status, List.of(), "")),
                Map.of("working", List.of(Map.of("id", "working-workbench-main", "agent", "workbench", "key", "main", "files", List.of(), "task", "",
                        "state", "failed", "since", "2026-11-10T09:00:00Z"))), List.of("Workbench", "The agent stopped")));
        List<Map<String, Object>> items = List.of(
                Surfaces.menuItem("new-chat", "New chat", "chat.new", Map.of("shortcut", "Ctrl+Shift+O")),
                Surfaces.menuItem("dark", "Dark mode", "view.dark", Map.of("checked", Map.of("path", "/view/dark"))),
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
        Map<String, Object> holding = row("schemaVersion", 1, "id", "working-workbench-main", "agent", "workbench", "key", "main",
                "files", List.of("data/q3-expenses.csv"), "task", "reading q3-expenses.csv", "state", "working", "since", "2026-11-10T09:00:31Z");
        Map<String, Object> treeOptions = Map.of("working", "/working", "upload", true, "label", "Files", "actions", List.of("ask", "download", "share", "rename", "remove"));
        out.add(golden("FileTree", "full", w, List.of(Surfaces.fileTree("tree", "/files", treeOptions)), Map.of("files", List.of(q3, note), "working", List.of(holding)),
                List.of("Files", "data", "q3-expenses.csv", "notes", "overspend.md", "workbench")));
        out.add(golden("FileTree", "empty", w, List.of(Surfaces.fileTree("tree", "/files", treeOptions)), Map.of("files", List.of()), List.of("Files", "No file yet", "Upload")));
        out.add(golden("FileTree", "loading", w, List.of(Surfaces.fileTree("tree", "/files", treeOptions)), Map.of(), List.of("Files", "Loading files")));
        out.add(golden("FileTree", "error", w, List.of(Surfaces.fileTree("tree", "/files", treeOptions)), Map.of("files", "not a list"), List.of("Files", "Unavailable")));
        Map<String, Object> pick = Map.of("path", "/selected/file");
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

    @Test
    void theFrameComponentsCarryTheirShapes() {
        JsonNode c = VizCatalog.catalog().path("components");
        assertThat(c.path("TitleBar").path("required")).extracting(JsonNode::asText).containsExactly("title");
        assertThat(c.path("Menu").path("properties").path("trigger").path("properties").path("kind").path("enum")).extracting(JsonNode::asText)
                .containsExactly("button", "overflow", "context");
        assertThat(c.path("Split").path("properties").path("panes").path("minItems").asInt()).isEqualTo(2);
        assertThat(c.path("Split").path("properties").path("panes").path("maxItems").asInt()).isEqualTo(3);
        assertThat(c.path("Split").path("properties").path("orientation").path("enum")).extracting(JsonNode::asText).containsExactly("horizontal", "vertical");
        assertThat(c.path("Window").path("properties").has("actions")).as("S6-U-03").isTrue();
        assertThat(c.path("Toolbar").path("properties").path("menu").path("type").asText()).as("S6-U-02").isEqualTo("string");
        // the builders' shapes, as SRAD §3.5 gives them
        Map<String, Object> bar = Surfaces.titleBar("bar", Map.of("path", "/app/title"), "Claims desk", "briefcase", Surfaces.titleStatus("/working", "Agent working", "Ready"),
                List.of(Surfaces.titleAction("share", "Share", "share", "share")), "bar-menu");
        assertThat(bar).containsEntry("title", Map.of("path", "/app/title")).containsEntry("menu", "bar-menu")
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
