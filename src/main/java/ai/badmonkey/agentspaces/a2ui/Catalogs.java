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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Reads the catalog documents this jar carries. */
final class Catalogs {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Catalogs() {
    }

    /** A JSON document from the jar. */
    static JsonNode read(String path) {
        try (InputStream in = Catalogs.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no " + path + " in the a2ui jar");
            }
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The component names a catalog declares, in the catalog's order. */
    static List<String> componentNames(JsonNode catalog) {
        List<String> out = new ArrayList<>();
        catalog.path("components").fieldNames().forEachRemaining(out::add);
        return out;
    }
}
