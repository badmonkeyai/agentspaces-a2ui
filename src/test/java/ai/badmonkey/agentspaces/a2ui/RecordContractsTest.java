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

import ai.badmonkey.agentspaces.a2ui.records.ChatMessage;
import ai.badmonkey.agentspaces.a2ui.records.ChatProgress;
import ai.badmonkey.agentspaces.a2ui.records.SharedFile;
import ai.badmonkey.agentspaces.a2ui.records.TaskStates;
import ai.badmonkey.agentspaces.a2ui.records.WorkingOn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The null policy of the A2UI records: a surface and a surface action refuse a null app and surface id (hosts
 * sort surfaces by the surface id and tag actions by both) and default the instant to the clock; a
 * publish refuses a null app and instant and defaults the rest. The test scans the package's compiled classes for
 * records, so a new record without a row here fails: a policy change is a test change. R refuses naming the component,
 * D yields the default, T keeps the null. The open records of {@code a2ui.records} (spiral 6) join the scan, a version
 * 1 and a version 2 row each where the version decides a policy.
 */
class RecordContractsTest {

    private static final Instant FIXED = Instant.parse("2026-11-07T12:00:00Z");
    private static final Object NOW = new Object();
    private static final Object R = new Object();
    private static final Object T = new Object();

    private record SameAs(String component) {
    }

    private record Row(Class<?> type, Map<String, Object> samples, Map<String, Object> policies) {
    }

    private static Row of(Class<?> type, Map<String, Object> samples, Object... pairs) {
        Map<String, Object> policies = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            policies.put((String) pairs[i], pairs[i + 1]);
        }
        return new Row(type, samples, policies);
    }

    @Test
    void everyRecordOfThePackageHoldsItsDeclaredNullPolicy() throws Exception {
        List<Row> table = List.of(
                of(Surface.class, Map.of(), "app", R, "surfaceId", R, "catalogId", "", "message", Map.of(), "version", Surface.A2UI_VERSION, "at", NOW),
                of(SurfaceAction.class, Map.of(), "app", R, "surfaceId", R, "session", "", "action", Map.of(), "at", NOW),
                of(Publish.class, Map.of("signOn", "idp"), "app", R, "from", "", "surfaces", List.of(), "title", new SameAs("app"), "host", "", "signOn", "idp",
                        "options", Map.of(), "at", R),
                of(PublishStatic.class, Map.of("root", StaticAppTest.ROOT, "entry", "index.html", "files", Map.of("index.html", "text/html"), "signOn", "none"),
                        "app", R, "from", "", "title", new SameAs("app"), "root", R, "entry", "index.html", "files", R, "signOn", "idp", "host", "", "at", R),
                of(StaticBundle.class, Map.of("files", List.of(new StaticFile("index.html", new byte[1]))), "files", R),
                of(StaticFile.class, Map.of("path", "index.html"), "path", R, "bytes", R),
                // spiral 6 S6-U-17: the open records; a v1 value keeps its v2 fields null, a v2 one takes their defaults
                of(ChatMessage.class, nullable("format", null, "cites", null), "id", R, "key", R, "from", new ChatMessage.From("person", "", ""), "text", "", "attachments", List.of(),
                        "inReplyTo", "", "at", T, "format", T, "cites", T),
                of(ChatMessage.class, Map.of("schemaVersion", 2, "format", ChatMessage.PLAIN), "id", R, "key", R, "from", new ChatMessage.From("person", "", ""), "text", "",
                        "attachments", List.of(), "inReplyTo", "", "at", T, "format", ChatMessage.PLAIN, "cites", List.of()),
                of(ChatMessage.From.class, Map.of("kind", "agent"), "kind", "person", "name", "", "peer", ""),
                of(ChatMessage.Cite.class, Map.of(), "file", R, "path", "", "name", ""),
                of(SharedFile.class, nullable("path", null, "replaces", null), "id", R, "name", R, "mediaType", "application/octet-stream", "root", "", "sha256", "", "by", "",
                        "sharedWith", List.of(), "at", NOW, "path", T, "replaces", T),
                of(SharedFile.class, Map.of("schemaVersion", 2), "id", R, "name", R, "mediaType", "application/octet-stream", "root", "", "sha256", "",
                        "by", "", "sharedWith", List.of(), "at", NOW, "path", "", "replaces", ""),
                of(WorkingOn.class, Map.of("state", TaskStates.WORKING), "id", R, "agent", R, "key", "", "files", List.of(), "task", "", "state", TaskStates.WORKING,
                        "since", R),
                of(ChatProgress.class, Map.of("state", TaskStates.WORKING), "id", R, "key", R, "inReplyTo", R, "agent", R, "state", TaskStates.SUBMITTED,
                        "steps", List.of(), "at", R),
                of(ChatProgress.Step.class, Map.of("kind", ChatProgress.Step.READ), "label", "", "kind", R, "ref", "", "at", R));
        List<Class<?>> records = new ArrayList<>(recordsOf(Surface.class));
        records.addAll(recordsOf(ChatMessage.class));
        assertThat(table.stream().map(Row::type).distinct().toList()).as("every record of the two packages has a row").containsExactlyInAnyOrderElementsOf(records);
        for (Row row : table) {
            check(row);
        }
    }

    private static void check(Row row) throws Exception {
        RecordComponent[] components = row.type().getRecordComponents();
        Object[] base = new Object[components.length];
        List<String> references = new ArrayList<>();
        for (int i = 0; i < components.length; i++) {
            base[i] = row.samples().containsKey(components[i].getName()) ? row.samples().get(components[i].getName()) : sample(components[i].getType());
            if (!components[i].getType().isPrimitive()) {
                references.add(components[i].getName());
            }
        }
        String where = row.type().getSimpleName();
        assertThat(row.policies().keySet()).as(where + ": a policy for every reference component").containsExactlyInAnyOrderElementsOf(references);
        if (construct(row.type(), components, base) instanceof Throwable t) {
            fail(where + ": the samples do not make a valid record: " + t);
        }
        for (int i = 0; i < components.length; i++) {
            RecordComponent rc = components[i];
            if (rc.getType().isPrimitive()) {
                continue;
            }
            Object[] args = base.clone();
            args[i] = null;
            Object policy = row.policies().get(rc.getName());
            Object outcome = construct(row.type(), components, args);
            String at = where + "." + rc.getName() + " null";
            if (policy == R) {
                assertThat(outcome).as(at + ": refused").isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
                assertThat(((Throwable) outcome).getMessage()).as(at + ": the refusal names it").contains(rc.getName());
                continue;
            }
            assertThat(outcome).as(at + ": accepted").isNotInstanceOf(Throwable.class);
            Object got = row.type().getMethod(rc.getName()).invoke(outcome);
            if (policy == T) {
                assertThat(got).as(at + ": kept").isNull();
            } else if (policy == NOW) {
                assertThat(Duration.between((Instant) got, Instant.now()).abs()).as(at + ": the clock").isLessThan(Duration.ofMinutes(1));
            } else if (policy instanceof SameAs s) {
                assertThat(got).as(at + ": copies " + s.component()).isEqualTo(row.type().getMethod(s.component()).invoke(outcome));
            } else {
                assertThat(got).as(at + ": the default").isEqualTo(policy);
            }
        }
    }

    /** Samples that may hold a null (a version 1 value's version 2 fields). */
    private static Map<String, Object> nullable(Object... pairs) {
        Map<String, Object> out = new java.util.HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);
        }
        return out;
    }

    private static Object construct(Class<?> type, RecordComponent[] components, Object[] args) throws Exception {
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
        }
        Constructor<?> ctor = type.getDeclaredConstructor(types);
        try {
            return ctor.newInstance(args);
        } catch (InvocationTargetException e) {
            return e.getCause();
        }
    }

    private static Object sample(Class<?> type) {
        if (type == String.class) {
            return "x";
        }
        if (type == int.class) {
            return 1;
        }
        if (type == long.class) {
            return 1L;
        }
        if (type == Instant.class) {
            return FIXED;
        }
        if (type == List.class) {
            return List.of();
        }
        if (type == Map.class) {
            return Map.of();
        }
        if (type == ChatMessage.From.class) {
            return new ChatMessage.From("person", "ada", "");
        }
        if (type == byte[].class) {
            return new byte[1];
        }
        throw new IllegalStateException("no sample for a component of type " + type.getName());
    }

    /** Every record class compiled into the package of {@code anchor}. */
    private static List<Class<?>> recordsOf(Class<?> anchor) throws Exception {
        Path dir = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI()).resolve(anchor.getPackageName().replace('.', '/'));
        List<Class<?>> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.sorted().toList()) {
                String name = f.getFileName().toString();
                if (!name.endsWith(".class") || name.matches(".*\\$\\d.*")) {
                    continue;
                }
                Class<?> c = Class.forName(anchor.getPackageName() + "." + name.substring(0, name.length() - ".class".length()), false, anchor.getClassLoader());
                if (c.isRecord()) {
                    out.add(c);
                }
            }
        }
        return out;
    }
}
