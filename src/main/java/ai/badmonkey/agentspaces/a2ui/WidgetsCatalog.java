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

import java.util.List;

/**
 * The open {@code agentspaces-widgets} catalog: the raw widgets of an application, each of which reads a list from the
 * surface's data model and writes an entry back through the host's callbacks with no dependency on a platform record.
 * ChatInput, FileShare, ApprovalCard, and TimelineStrip, as one JSON document in A2UI's catalog shape beside
 * {@link VizCatalog}. A widget's {@code entry} names a record type under {@link #NAMESPACE}, and its {@code element} is
 * the custom element a renderer draws it with.
 */
public final class WidgetsCatalog {

    public static final String ID = "agentspaces-widgets";
    public static final String CATALOG_PATH = "/catalogs/agentspaces-widgets/catalog.json";
    /** The namespace of the widgets' entry types. */
    public static final String NAMESPACE = "https://badmonkey.ai/agentspaces/a2ui/v1/";
    public static final List<String> COMPONENTS = List.of("ChatInput", "FileShare", "ApprovalCard", "TimelineStrip");

    private WidgetsCatalog() {
    }

    /** The catalog document. */
    public static JsonNode catalog() {
        return Catalogs.read(CATALOG_PATH);
    }

    /** The component names the catalog declares, in the catalog's order. */
    public static List<String> componentNames() {
        return Catalogs.componentNames(catalog());
    }
}
