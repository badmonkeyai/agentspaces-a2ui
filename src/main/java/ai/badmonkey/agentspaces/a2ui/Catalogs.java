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
import com.fasterxml.jackson.databind.node.MissingNode;

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
            // the catalog declares id and component on every component since it closes them (A2UI 1.0's envelope);
            // the builder writes both from its own arguments, so an option must not overwrite them
            if ("id".equals(option.getKey()) || "component".equals(option.getKey())) {
                throw new IllegalArgumentException(component + "'s " + option.getKey() + " is the builder's own argument, not an option");
            }
            JsonNode schema = resolve(catalog, properties.path(option.getKey()));
            if (properties.path(option.getKey()).isMissingNode()) {
                throw new IllegalArgumentException(component + " has no property " + option.getKey() + "; it takes " + names(properties));
            }
            Object value = option.getValue();
            JsonNode words = schema.has("enum") ? schema.get("enum") : items(catalog, schema).path("enum");
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

    /**
     * A schema with its {@code $ref} followed while the reference points into the same catalog ({@code #/$defs/...} or
     * {@code #/components/...}); a reference outside it (A2UI 1.0's common types) reads as missing, so a walk that
     * cannot see a type treats the value as unknown rather than guessing.
     */
    static JsonNode resolve(JsonNode catalog, JsonNode schema) {
        JsonNode s = schema == null ? MissingNode.getInstance() : schema;
        for (int hops = 0; s.has("$ref") && hops < 16; hops++) {
            String ref = s.get("$ref").asText();
            s = catalog != null && ref.startsWith("#/") ? catalog.at(ref.substring(1)) : MissingNode.getInstance();
        }
        return s;
    }

    /** The alternatives a schema offers (its own shape and each of {@code oneOf}, {@code anyOf} and {@code allOf}), resolved. */
    private static List<JsonNode> shapes(JsonNode catalog, JsonNode schema) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode s = resolve(catalog, schema);
        out.add(s);
        for (String key : List.of("oneOf", "anyOf", "allOf")) {
            for (JsonNode alternative : s.path(key)) {
                out.addAll(shapes(catalog, alternative));
            }
        }
        return out;
    }

    /** Whether a schema, or one of its alternatives, declares {@code name} as a property of an object. */
    static boolean declares(JsonNode catalog, JsonNode schema, String name) {
        return shapes(catalog, schema).stream().anyMatch(s -> s.path("properties").has(name));
    }

    /** The schema of one property of an object, from the schema or the first alternative that declares it, else its additionalProperties schema; missing when none says. */
    static JsonNode property(JsonNode catalog, JsonNode schema, String name) {
        for (JsonNode s : shapes(catalog, schema)) {
            if (s.path("properties").has(name)) {
                return resolve(catalog, s.path("properties").get(name));
            }
        }
        for (JsonNode s : shapes(catalog, schema)) {
            if (s.path("additionalProperties").isObject()) {
                return resolve(catalog, s.get("additionalProperties"));
            }
        }
        return MissingNode.getInstance();
    }

    /** The schema of an array's items, from the schema or the first alternative that has them; missing when none says. */
    static JsonNode items(JsonNode catalog, JsonNode schema) {
        for (JsonNode s : shapes(catalog, schema)) {
            if (s.has("items")) {
                return resolve(catalog, s.get("items"));
            }
        }
        return MissingNode.getInstance();
    }

    /** A free-form record: an object schema that names no property and no alternative, so its keys are names (an action's context), not a binding's. */
    static boolean record(JsonNode catalog, JsonNode schema) {
        JsonNode s = resolve(catalog, schema);
        return "object".equals(s.path("type").asText()) && !s.has("properties") && !s.has("oneOf") && !s.has("anyOf") && !s.has("allOf");
    }

    private static List<String> names(JsonNode properties) {
        List<String> out = new ArrayList<>();
        properties.fieldNames().forEachRemaining(n -> {
            if (!"id".equals(n) && !"component".equals(n)) {
                out.add(n);
            }
        });
        return out;
    }

    /** The component names a catalog declares, in the catalog's order. */
    static List<String> componentNames(JsonNode catalog) {
        List<String> out = new ArrayList<>();
        catalog.path("components").fieldNames().forEachRemaining(out::add);
        return out;
    }
}
