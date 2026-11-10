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

/**
 * The open catalog {@code agentspaces-widgets} carries the four raw widgets, each with its {@code reads} path, its open
 * {@code entry} IRI, and its {@code agentspaces-*} element, and the catalog's document is pinned.
 */
class WidgetsCatalogTest {

    @Test
    void theCatalogCarriesTheFourRawWidgets() {
        JsonNode catalog = WidgetsCatalog.catalog();
        assertThat(WidgetsCatalog.ID).isEqualTo("agentspaces-widgets");
        assertThat(catalog.path("catalogId").asText()).isEqualTo(WidgetsCatalog.ID);
        assertThat(catalog.path("title").asText()).isEqualTo("AgentSpaces widgets catalog");
        assertThat(WidgetsCatalog.componentNames()).containsExactlyElementsOf(WidgetsCatalog.COMPONENTS)
                .containsExactly("ChatInput", "FileShare", "ApprovalCard", "TimelineStrip");
        Map<String, List<String>> expected = Map.of(
                "ChatInput", List.of("/conversations", WidgetsCatalog.NAMESPACE + "ChatMessage", "agentspaces-chat-input"),
                "FileShare", List.of("/files", WidgetsCatalog.NAMESPACE + "SharedFile", "agentspaces-file-share"),
                "ApprovalCard", List.of("/approvals", "", "agentspaces-approval-card"),
                "TimelineStrip", List.of("/events", "", "agentspaces-timeline-strip"));
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
}
