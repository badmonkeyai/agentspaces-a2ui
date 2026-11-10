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
 * The open {@code agentspaces-viz} catalog: the components people look at (Chart, DataTable, Map, Graph, Timeline,
 * Report, Notification, Form, Stat, and the panels beside them), as one JSON document in A2UI's catalog shape, carried
 * in this jar so a renderer, a host, and a catalog policy read the same file. The look-and-feel schema beside it says
 * what a look and feel document may carry. {@link WidgetsCatalog} is the open catalog of raw widgets beside it.
 */
public final class VizCatalog {

    public static final String ID = "agentspaces-viz";
    public static final String CATALOG_PATH = "/catalogs/agentspaces-viz/catalog.json";
    public static final String LOOK_AND_FEEL_SCHEMA_PATH = "/catalogs/look-and-feel.schema.json";
    public static final List<String> COMPONENTS = List.of("Chart", "DataTable", "Map", "Graph", "Timeline", "Report", "Notification", "Form", "Stat", "Window", "Conversation", "Notice", "Toolbar", "KeyValue", "Prose");
    private VizCatalog() {
    }

    /** The catalog document. */
    public static JsonNode catalog() {
        return Catalogs.read(CATALOG_PATH);
    }

    /** The look-and-feel JSON Schema. */
    public static JsonNode lookAndFeelSchema() {
        return Catalogs.read(LOOK_AND_FEEL_SCHEMA_PATH);
    }

    /** The component names the catalog declares, in the catalog's order. */
    public static List<String> componentNames() {
        return Catalogs.componentNames(catalog());
    }
}
