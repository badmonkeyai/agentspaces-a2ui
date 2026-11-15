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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The open catalog {@code agentspaces-widgets} carries the raw widgets and the application components of spiral 6
 * (Chat, FileTree, FilePreview), each with its {@code reads} path, its open {@code entry} IRI, and its
 * {@code agentspaces-*} element, and the catalog's document is pinned.
 */
class WidgetsCatalogTest {

    @Test
    void theCatalogCarriesTheRawWidgetsAndTheApplicationComponents() {
        JsonNode catalog = WidgetsCatalog.catalog();
        assertThat(WidgetsCatalog.ID).isEqualTo("agentspaces-widgets");
        assertThat(catalog.path("catalogId").asText()).isEqualTo(WidgetsCatalog.ID);
        assertThat(catalog.path("title").asText()).isEqualTo("AgentSpaces widgets catalog");
        assertThat(WidgetsCatalog.componentNames()).containsExactlyElementsOf(WidgetsCatalog.COMPONENTS)
                .containsExactly("ChatInput", "FileShare", "ApprovalCard", "TimelineStrip", "Chat", "FileTree", "FilePreview");
        Map<String, List<String>> expected = Map.of(
                "ChatInput", List.of("/conversations", WidgetsCatalog.NAMESPACE + "ChatMessage", "agentspaces-chat-input"),
                "FileShare", List.of("/files", WidgetsCatalog.NAMESPACE + "SharedFile", "agentspaces-file-share"),
                "ApprovalCard", List.of("/approvals", "", "agentspaces-approval-card"),
                "TimelineStrip", List.of("/events", "", "agentspaces-timeline-strip"),
                "Chat", List.of("/conversations", WidgetsCatalog.NAMESPACE + "ChatMessage", "agentspaces-chat"),
                "FileTree", List.of("/files", WidgetsCatalog.NAMESPACE + "SharedFile", "agentspaces-file-tree"),
                "FilePreview", List.of("/files", WidgetsCatalog.NAMESPACE + "SharedFile", "agentspaces-file-preview"));
        for (Map.Entry<String, List<String>> e : expected.entrySet()) {
            JsonNode c = catalog.path("components").path(e.getKey());
            assertThat(c.path("reads").asText()).as(e.getKey()).isEqualTo(e.getValue().get(0));
            assertThat(c.path("entry").asText()).as(e.getKey()).isEqualTo(e.getValue().get(1));
            assertThat(c.path("element").asText()).as(e.getKey()).isEqualTo(e.getValue().get(2));
            assertThat(c.path("description").asText()).as(e.getKey()).isNotBlank();
        }
        assertThat(WidgetsCatalog.NAMESPACE).isEqualTo("https://badmonkey.ai/agentspaces/a2ui/v1/");
        // every IRI of ours in the catalog sits under the open AgentSpaces namespace, never under a host's own
        assertThat(Pattern.compile("https?://badmonkey\\.ai/[^\"]*").matcher(catalog.toString()).results().map(MatchResult::group))
                .isNotEmpty().allMatch(iri -> iri.startsWith("https://badmonkey.ai/agentspaces/"), "under https://badmonkey.ai/agentspaces/");
    }

    /**
     * Spiral 6 S6-U-06, U-07, U-09, U-10, U-13, U-14, U-15, U-32 (SRAD §3.5, decision 4): the shapes of the three
     * application components, and ChatInput, Conversation and FileShare named as their aliases through 0.6.x.
     */
    @Test
    void theApplicationComponentsCarryTheirShapes() {
        JsonNode c = WidgetsCatalog.catalog().path("components");
        JsonNode chat = c.path("Chat").path("properties");
        assertThat(chat.path("format").path("enum")).extracting(JsonNode::asText).containsExactly("text/plain", "text/markdown");
        assertThat(chat.path("controls").path("items").path("enum")).extracting(JsonNode::asText).containsExactly("copy", "retry", "stop", "feedback");
        assertThat(chat.path("max").path("default").asInt()).isEqualTo(100);
        assertThat(chat.path("reads").path("default").asText()).isEqualTo("/conversations");
        assertThat(chat.path("progress").path("default").asText()).isEqualTo("/progress");
        assertThat(chat.path("files").path("default").asText()).isEqualTo("/files");
        assertThat(chat.path("writeAction").path("default").asText()).isEqualTo("write");
        assertThat(chat.has("agent") && chat.has("key") && chat.has("suggestions") && chat.has("attach") && chat.has("placeholder") && chat.has("preview")).isTrue();
        JsonNode tree = c.path("FileTree").path("properties");
        assertThat(tree.path("actions").path("items").path("enum")).extracting(JsonNode::asText).containsExactly("ask", "download", "share", "rename", "remove");
        assertThat(tree.path("badges").path("items").path("enum")).extracting(JsonNode::asText).containsExactly("working", "new", "changed", "agent");
        assertThat(tree.has("working") && tree.has("upload") && tree.has("preview") && tree.has("chat") && tree.has("agents") && tree.has("flat")).isTrue();
        JsonNode preview = c.path("FilePreview").path("properties");
        assertThat(preview.path("maxBytes").path("default").asLong()).isEqualTo(2_097_152L);
        assertThat(preview.path("maxBytes").path("maximum").asLong()).isEqualTo(16_777_216L);
        assertThat(preview.path("csvRows").path("default").asInt()).isEqualTo(500);
        assertThat(preview.path("kinds").path("items").path("enum")).extracting(JsonNode::asText)
                .containsExactly("text/plain", "text/markdown", "text/csv", "image/png", "image/jpeg", "application/pdf");
        assertThat(c.path("ChatInput").path("description").asText()).contains("an alias of Chat");
        assertThat(c.path("FileShare").path("description").asText()).contains("an alias of FileTree");
        assertThat(VizCatalog.catalog().path("components").path("Conversation").path("description").asText()).contains("alias of the widgets catalog's Chat");
    }

    /** S6-U-26: the builders of the three components, their options checked against the catalog's words. */
    @Test
    void theApplicationBuildersShapeTheirComponents() {
        Map<String, Object> chat = Surfaces.chat("chat", "workbench", "main", Map.of("suggestions", List.of("Summarise the newest file"), "attach", true,
                "format", "text/markdown", "controls", List.of("copy", "retry", "stop", "feedback"), "preview", "preview"));
        assertThat(chat).containsEntry("component", "Chat").containsEntry("agent", "workbench").containsEntry("key", "main").containsEntry("attach", true)
                .containsEntry("controls", List.of("copy", "retry", "stop", "feedback")).containsEntry("preview", "preview");
        assertThat(Surfaces.chat("chat", "", "main", Map.of())).containsOnlyKeys("id", "component", "key");
        assertThatThrownBy(() -> Surfaces.chat("chat", "a", "main", Map.of("controls", List.of("delete")))).hasMessageContaining("controls");
        assertThatThrownBy(() -> Surfaces.chat("chat", "a", "main", Map.of("format", "text/html"))).hasMessageContaining("format");
        assertThatThrownBy(() -> Surfaces.chat("chat", "a", "main", Map.of("colour", "red"))).hasMessageContaining("colour");
        Map<String, Object> tree = Surfaces.fileTree("tree", "/files", Map.of("working", "/working", "upload", true, "preview", "preview", "chat", "chat",
                "actions", List.of("ask", "download", "share", "rename", "remove")));
        assertThat(tree).containsEntry("component", "FileTree").containsEntry("reads", "/files").containsEntry("chat", "chat");
        assertThatThrownBy(() -> Surfaces.fileTree("tree", "/files", Map.of("actions", List.of("burn")))).hasMessageContaining("actions");
        assertThatThrownBy(() -> Surfaces.fileTree("tree", "/files", Map.of("badges", List.of("shiny")))).hasMessageContaining("badges");
        Map<String, Object> preview = Surfaces.filePreview("preview", Map.of("@path", "/selected/file"), Map.of("files", "/files", "maxBytes", 2_097_152, "csvRows", 500));
        assertThat(preview).containsEntry("component", "FilePreview").containsEntry("file", Map.of("@path", "/selected/file")).containsEntry("csvRows", 500);
        assertThatThrownBy(() -> Surfaces.filePreview("preview", "f-1", Map.of("maxBytes", 17_000_000))).hasMessageContaining("16 MiB");
        assertThatThrownBy(() -> Surfaces.filePreview("preview", "f-1", Map.of("kinds", List.of("text/html")))).hasMessageContaining("kinds");
    }

    @Test
    void noCatalogTextNamesAnInternalPlanOrTicket() {
        // a catalog's descriptions are what an agent author and a model read, so they say what a component does in
        // plain words and never point at a plan, a ticket or a review the reader cannot open
        Pattern internal = Pattern.compile("ISSUE-\\w+|\\b(?:FR|NFR|SR|AC)-\\d+\\b|\\b[A-Z][A-Z0-9]{0,3}(-[A-Z0-9]{1,5}){1,2}-\\d{2}\\b|\\b[A-Z]-\\d{2}\\b|(?i:\\bspiral \\d|\\bphase \\d)|SRAD");
        for (JsonNode catalog : List.of(WidgetsCatalog.catalog(), VizCatalog.catalog())) {
            assertThat(internal.matcher(catalog.toString()).results().map(MatchResult::group))
                    .as(catalog.path("catalogId").asText()).isEmpty();
        }
    }

    @Test
    void theCatalogDocumentIsPinned() throws IOException {
        String json = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writerWithDefaultPrettyPrinter()
                .writeValueAsString(new ObjectMapper().treeToValue(WidgetsCatalog.catalog(), Map.class)) + "\n";
        Path golden = Path.of("src/test/resources/golden/widgets_catalog.json");
        if (!Files.exists(golden)) {
            Files.createDirectories(golden.getParent());
            Files.writeString(golden, json, StandardCharsets.UTF_8);
        }
        assertThat(json).as("the open widgets catalog is pinned; delete the file to regenerate").isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
    }

    @org.junit.jupiter.api.Test
    void aBindingInTheOldFormIsRefused() {
        // A2UI 1.0 (2026-11-12): {"path": ...} is v0.9.1's binding; the builders refuse it by name
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Surfaces.filePreview("p", java.util.Map.of("path", "/selected/file"), java.util.Map.of()))
                .hasMessageContaining("file binds with A2UI 1.0");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Surfaces.titleBar("b", java.util.Map.of("path", "/t"), "", "", null, java.util.List.of(), ""))
                .hasMessageContaining("title binds with A2UI 1.0");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Surfaces.titleBar("b", java.util.Map.of("call", "formatDate", "args", java.util.Map.of()), "", "", null, java.util.List.of(), ""))
                .hasMessageContaining("@call");
    }
}
