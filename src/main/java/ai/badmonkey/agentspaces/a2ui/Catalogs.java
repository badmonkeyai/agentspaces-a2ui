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
import java.util.Map;

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

    /**
     * Checks a builder's options against a component's property schemas: each key is a property the component
     * declares, a value of an {@code enum} property (or of an array whose items carry one) is one of its words, and a
     * number keeps the property's {@code minimum} and {@code maximum}; a builder then cannot write what the catalog,
     * and so the feed's validation, would refuse.
     */
    static void checkOptions(JsonNode catalog, String component, Map<String, Object> options) {
        JsonNode properties = catalog.path("components").path(component).path("properties");
        for (Map.Entry<String, Object> option : options.entrySet()) {
            JsonNode schema = properties.path(option.getKey());
            if (schema.isMissingNode()) {
                throw new IllegalArgumentException(component + " has no property " + option.getKey() + "; it takes " + names(properties));
            }
            Object value = option.getValue();
            JsonNode words = schema.has("enum") ? schema.get("enum") : schema.path("items").path("enum");
            if (words.isArray()) {
                for (Object v : value instanceof List<?> list ? list : List.of(value)) {
                    boolean known = false;
                    for (JsonNode w : words) {
                        known |= w.asText().equals(String.valueOf(v));
                    }
                    if (!known) {
                        throw new IllegalArgumentException(component + "." + option.getKey() + " takes " + words + ", not " + v);
                    }
                }
            }
            if (value instanceof Number n && schema.has("maximum") && n.doubleValue() > schema.get("maximum").asDouble()) {
                throw new IllegalArgumentException(component + "." + option.getKey() + " is at most " + schema.get("maximum").asLong()
                        + (schema.get("maximum").asLong() == 16_777_216L ? " (16 MiB)" : "") + ": " + n);
            }
            if (value instanceof Number n && schema.has("minimum") && n.doubleValue() < schema.get("minimum").asDouble()) {
                throw new IllegalArgumentException(component + "." + option.getKey() + " is at least " + schema.get("minimum").asLong() + ": " + n);
            }
        }
    }

    private static List<String> names(JsonNode properties) {
        List<String> out = new ArrayList<>();
        properties.fieldNames().forEachRemaining(out::add);
        return out;
    }

    /** The component names a catalog declares, in the catalog's order. */
    static List<String> componentNames(JsonNode catalog) {
        List<String> out = new ArrayList<>();
        catalog.path("components").fieldNames().forEachRemaining(out::add);
        return out;
    }
}
