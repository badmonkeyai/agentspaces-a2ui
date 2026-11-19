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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GEO-V-05: no builder writes A2UI v0.9.1's {@code {"path": ...}} binding or {@code {"call": ...}} function call in any
 * property or option, however deep it sits; the wire carries 1.0 alone. A property the catalog declares as {@code path}
 * (a Map layer's, a Chart series') is not a binding and passes.
 */
class BuilderBindingsTest {

    static final Map<String, Object> OLD_PATH = Map.of("path", "/x");
    static final Map<String, Object> OLD_CALL = Map.of("call", "formatDate", "args", Map.of());

    /**
     * A2UI 1.0 final (2026-11-10, spec §2.9): a component's envelope keys are {@code @catalogId}, {@code @accessibility},
     * {@code @metadata} and {@code @checks}; their bare names are refused unless the component declares them, and below
     * a component's root a single-{@code @} key other than {@code @path} or {@code @call} is reserved ({@code @@} escapes).
     */
    @Test
    void theEnvelopeKeysTakeTheAtPrefixAndOtherSingleAtKeysAreRefused() {
        Map<String, Object> chart = new java.util.LinkedHashMap<>(Surfaces.visualize("v", "/spend", "column", "month", "amount", Map.of()));
        Map<String, Object> labelled = new java.util.LinkedHashMap<>(chart);
        labelled.put("@accessibility", Map.of("label", "Spend by month"));
        labelled.put("@catalogId", VizCatalog.ID);
        assertThatCode(() -> Surfaces.bindings(VizCatalog.CATALOG, labelled)).doesNotThrowAnyException();
        for (String bare : List.of("catalogId", "accessibility", "metadata", "checks")) {
            Map<String, Object> old = new java.util.LinkedHashMap<>(chart);
            old.put(bare, Map.of());
            assertThatThrownBy(() -> Surfaces.bindings(VizCatalog.CATALOG, old)).hasMessageContaining("Visualize." + bare).hasMessageContaining("@" + bare);
        }
        Map<String, Object> unknown = new java.util.LinkedHashMap<>(chart);
        unknown.put("@theme", "dark");
        assertThatThrownBy(() -> Surfaces.bindings(VizCatalog.CATALOG, unknown)).hasMessageContaining("Visualize.@theme").hasMessageContaining("reserved");
        assertThatThrownBy(() -> Surfaces.visualize("v", "/spend", "column", "month", "amount", Map.of("accessibility", Map.of("label", "x"))))
                .hasMessageContaining("@accessibility");
        // nested: a literal key that starts with @ is doubled; @path and @call are the protocol's own
        assertThatThrownBy(() -> Surfaces.button("b", "Go", "go", Map.of("row", Map.of("@type", "Claim")), null))
                .hasMessageContaining("context.row.@type").hasMessageContaining("@@type");
        assertThatCode(() -> Surfaces.button("b", "Go", "go", Map.of("row", Map.of("@@type", "Claim"), "at", Map.of("@path", "/x")), null)).doesNotThrowAnyException();
    }

    @Test
    void theBasicBuildersRefuseAnOldFormInAnActionContext() {
        assertThatThrownBy(() -> Surfaces.button("b", "Go", "go", Map.of("row", OLD_PATH), null)).hasMessageContaining("context.row").hasMessageContaining("@path");
        assertThatThrownBy(() -> Surfaces.button("b", "Go", "go", Map.of("rows", List.of(OLD_CALL)), null)).hasMessageContaining("context.rows[0]").hasMessageContaining("@call");
        // a context's keys are names: an entry called path with a literal is not a binding, and 1.0's forms pass
        assertThatCode(() -> Surfaces.button("b", "Go", "go", Map.of("path", "/docs/a", "row", Map.of("@path", "/row")), null)).doesNotThrowAnyException();
    }

    @Test
    void theChartAndTableBuildersRefuseAnOldFormInAnyOption() {
        assertThatThrownBy(() -> Surfaces.visualize("v", "/spend", "column", "month", "amount", Map.of("title", OLD_CALL)))
                .hasMessageContaining("Visualize.title").hasMessageContaining("@call");
        assertThatThrownBy(() -> Surfaces.graphView("g", "/n", "/e", "force", Map.of("title", OLD_PATH))).hasMessageContaining("GraphView.title");
        assertThatThrownBy(() -> Surfaces.chart("c", "bar", "t", List.of(Map.of("name", "s", "path", "/s", "color", OLD_PATH)), "x", "")).hasMessageContaining("Chart.series[0].color");
        assertThatThrownBy(() -> Surfaces.dataTable("t", List.of(Map.of("key", "k", "title", OLD_CALL)), "/rows", "")).hasMessageContaining("DataTable.columns[0].title");
        assertThatThrownBy(() -> Surfaces.keyValue("kv", "", List.of(Map.of("label", "Owner", "value", OLD_PATH)), "")).hasMessageContaining("KeyValue.items[0].value");
        // a series' path is the catalog's own property, not a binding; a Report section's component is read by its own schema
        assertThatCode(() -> Surfaces.chart("c", "bar", "t", List.of(Map.of("name", "s", "path", "/s")), "x", "")).doesNotThrowAnyException();
        Map<String, Object> embedded = Surfaces.chart("c", "bar", "t", List.of(Map.of("name", "s", "path", "/s")), "x", "");
        assertThatCode(() -> Surfaces.report("r", "Report", List.of(Map.of("kind", "chart", "component", embedded)))).doesNotThrowAnyException();
        assertThatThrownBy(() -> Surfaces.report("r", "Report", List.of(Map.of("kind", "text", "text", OLD_PATH)))).hasMessageContaining("Report.sections[0].text");
    }

    @Test
    void theMapBuildersRefuseAnOldFormInAnOptionOrALayer() {
        Map<String, Object> layer = Surfaces.layer("icons", "/positions", "unit", "unit", Map.of());
        assertThatThrownBy(() -> Surfaces.map("m", List.of(layer), null, Map.of("follow", OLD_PATH))).hasMessageContaining("Map.follow").hasMessageContaining("@path");
        assertThatThrownBy(() -> Surfaces.layer("icons", "/positions", "unit", "unit", Map.of("heading", OLD_CALL))).hasMessageContaining("Map.layers[].heading");
        assertThatThrownBy(() -> Surfaces.map("m", List.of(Map.of("kind", "icons", "path", "/p", "label", OLD_PATH)), null)).hasMessageContaining("Map.layers[0].label");
        assertThatCode(() -> Surfaces.map("m", List.of(layer), "inspect", Map.of("follow", "/follow", "center", List.of(-3.0, 53.4)))).doesNotThrowAnyException();
    }

    @Test
    void theFrameBuildersRefuseAnOldFormInABarAMenuOrAWindow() {
        assertThatThrownBy(() -> Surfaces.titleBar("b", "App", "", "", Map.of("from", OLD_PATH), List.of(), "")).hasMessageContaining("TitleBar.status.from");
        assertThatThrownBy(() -> Surfaces.titleBar("b", "App", "", "", null, List.of(Map.of("id", "a", "label", "A", "action", Map.of("name", "a", "context", Map.of("k", OLD_PATH)))), ""))
                .hasMessageContaining("TitleBar.actions[0].action.context.k");
        assertThatThrownBy(() -> Surfaces.menuItem("open", "Open", "open", Map.of("checked", OLD_PATH))).hasMessageContaining("Menu.items[].checked");
        assertThatThrownBy(() -> Surfaces.menuItem("open", "Open", "open", Map.of("context", Map.of("file", OLD_CALL)))).hasMessageContaining("Menu.items[].action.context.file");
        assertThatThrownBy(() -> Surfaces.submenu("more", "More", List.of(Map.of("id", "x", "label", "X", "checked", OLD_PATH)))).hasMessageContaining("Menu.items[].items[0].checked");
        assertThatThrownBy(() -> Surfaces.menu("m", Surfaces.trigger("button", "Menu"), List.of(Map.of("id", "x", "label", OLD_PATH)))).hasMessageContaining("Menu.items[0].label");
        assertThatThrownBy(() -> Surfaces.window("w", "W", "c", "flow", List.of(Map.of("id", "a", "label", OLD_CALL, "action", Map.of("name", "a"))))).hasMessageContaining("Window.actions[0].label");
        assertThatThrownBy(() -> Surfaces.toolbar("t", "T", List.of(Map.of("label", "Go", "action", "go", "context", Map.of("k", OLD_PATH))))).hasMessageContaining("Toolbar.actions[0].context.k");
        // 1.0's binding passes where a property takes one, and a context entry named path holds a literal
        assertThatCode(() -> Surfaces.menuItem("open", "Open", "open", Map.of("checked", Map.of("@path", "/open"), "context", Map.of("path", "/docs/a")))).doesNotThrowAnyException();
        // a submenu's item is read by the same item schema (the review of 2026-11-12): its context's keys are names too
        assertThatCode(() -> Surfaces.submenu("more", "More", List.of(Surfaces.menuItem("open", "Open", "open",
                Map.of("context", Map.of("path", Map.of("folder", "/docs"))))))).doesNotThrowAnyException();
        assertThatThrownBy(() -> Surfaces.submenu("more", "More", List.of(Surfaces.menuItem("open", "Open", "open",
                Map.of("context", Map.of("file", OLD_CALL)))))).hasMessageContaining("@call");
        assertThatCode(() -> Surfaces.titleBar("b", Map.of("@path", "/title"), "", "", Surfaces.titleStatus("/working", "", ""), List.of(), "")).doesNotThrowAnyException();
    }

    @Test
    void theWidgetBuildersRefuseAnOldFormInAnyOption() {
        assertThatThrownBy(() -> Surfaces.chat("c", "agent", "k", Map.of("placeholder", OLD_CALL))).hasMessageContaining("Chat.placeholder").hasMessageContaining("@call");
        assertThatThrownBy(() -> Surfaces.chat("c", "agent", "k", Map.of("suggestions", List.of("a", OLD_PATH)))).hasMessageContaining("Chat.suggestions[1]");
        assertThatThrownBy(() -> Surfaces.fileTree("t", "/files", Map.of("label", OLD_PATH))).hasMessageContaining("FileTree.label");
        assertThatThrownBy(() -> Surfaces.filePreview("p", "f1", Map.of("files", OLD_PATH))).hasMessageContaining("FilePreview.files");
        assertThatCode(() -> Surfaces.filePreview("p", Map.of("@path", "/selected"), Map.of("files", "/files"))).doesNotThrowAnyException();
    }

    @Test
    void anOptionCannotOverwriteTheIdTheCatalogNowDeclares() {
        assertThatThrownBy(() -> Surfaces.chat("c", "agent", "k", Map.of("id", "other"))).hasMessageContaining("builder's own argument");
        assertThatThrownBy(() -> Surfaces.visualize("v", "/spend", "column", "month", "amount", Map.of("component", "Chart"))).hasMessageContaining("builder's own argument");
        assertThat(Surfaces.chat("c", "agent", "k", Map.of())).containsEntry("id", "c");
    }
}
