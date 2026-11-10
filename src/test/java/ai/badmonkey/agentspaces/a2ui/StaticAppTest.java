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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The static app records: a {@link PublishStatic} names a bundle of files on the content swarm by its root, and a
 * {@link StaticBundle} is that bundle's document. Both round-trip through the strict wire read and keep their pinned
 * bytes; the records refuse a path that could leave the app's directory, a media type outside RFC 6838's form, a root
 * that is no content root, and a bundle whose files are not exactly the ones its publish names.
 */
class StaticAppTest {

    static final CborCodec CODEC = CborCodec.defaultCodec();
    static final Instant AT = Instant.parse("2026-11-09T12:00:00Z");
    /** A content root as the swarm writes one: {@code b}, then base32 of a CIDv1 of codec raw over sha2-256. */
    static final String ROOT = "bafkreiebfkjczhm7yk2hhhbcfmmp7gw7om6vvwercqq2nvdxenhxnrkf44";

    static Map<String, String> files() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("index.html", "text/html; charset=utf-8");
        files.put("assets/status.css", "text/css; charset=utf-8");
        files.put("assets/status.svg", "image/svg+xml");
        return files;
    }

    static PublishStatic publish(String signOn) {
        return new PublishStatic(PublishStatic.VERSION, "app-status", "snapshot", "Greeter status", ROOT, 812, "index.html", files(), signOn, "", AT);
    }

    static StaticBundle bundle() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("index.html", "<!doctype html><title>Greeter status</title><link rel=stylesheet href=assets/status.css><img src=assets/status.svg alt=status>".getBytes(StandardCharsets.UTF_8));
        files.put("assets/status.css", "body{font-family:sans-serif}".getBytes(StandardCharsets.UTF_8));
        files.put("assets/status.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"/>".getBytes(StandardCharsets.UTF_8));
        return StaticBundle.of(files);
    }

    @Test
    void theRecordsRoundTripThroughTheStrictReadAndMatchTheirVectors() throws IOException {
        pin("publish_static_cbor", publish("none"), PublishStatic.class);
        pin("static_bundle_cbor", bundle(), StaticBundle.class);
        assertThat(PublishStatic.SIGN_ONS).containsExactly("idp", "none");
        assertThat(new PublishStatic(PublishStatic.VERSION, "a", null, null, ROOT, 1, null, Map.of("index.html", "text/html"), null, null, AT))
                .satisfies(p -> {
                    assertThat(p.signOn()).as("the default guards the app, as Publish does").isEqualTo("idp");
                    assertThat(p.entry()).isEqualTo("index.html");
                    assertThat(p.title()).isEqualTo("a");
                });
    }

    @Test
    void aBundleOpensAgainstItsPublishOnlyWhenItHoldsExactlyTheNamedFiles() {
        byte[] bytes = CODEC.toBytes(bundle());
        StaticBundle read = StaticBundle.decode(bytes);
        Map<String, byte[]> opened = read.open(publish("none"));
        assertThat(opened).containsOnlyKeys("index.html", "assets/status.css", "assets/status.svg");
        assertThat(new String(opened.get("assets/status.css"), StandardCharsets.UTF_8)).isEqualTo("body{font-family:sans-serif}");
        // a bundle with a file the publish does not name, and one without a file it names, open nothing
        Map<String, String> fewer = new LinkedHashMap<>(files());
        fewer.remove("assets/status.svg");
        PublishStatic narrower = new PublishStatic(PublishStatic.VERSION, "app-status", "snapshot", "t", ROOT, 812, "index.html", fewer, "none", "", AT);
        assertThatThrownBy(() -> read.open(narrower)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("assets/status.svg");
        Map<String, String> more = new LinkedHashMap<>(files());
        more.put("extra.js", "text/javascript");
        PublishStatic wider = new PublishStatic(PublishStatic.VERSION, "app-status", "snapshot", "t", ROOT, 812, "index.html", more, "none", "", AT);
        assertThatThrownBy(() -> read.open(wider)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("extra.js");
        // bytes that are not a bundle, and a bundle relabelled out of its canonical form, decode to nothing
        assertThatThrownBy(() -> StaticBundle.decode("<html>".getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        byte[] trailing = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        assertThatThrownBy(() -> StaticBundle.decode(trailing)).isInstanceOf(IllegalArgumentException.class);
        // the files travel in path order, once each
        assertThatThrownBy(() -> new StaticBundle(StaticBundle.VERSION, List.of(new StaticFile("b.html", new byte[1]), new StaticFile("a.html", new byte[1]))))
                .hasMessageContaining("path order");
        assertThatThrownBy(() -> new StaticBundle(StaticBundle.VERSION, List.of(new StaticFile("a.html", new byte[1]), new StaticFile("a.html", new byte[1]))))
                .hasMessageContaining("path order");
        assertThatThrownBy(() -> new StaticBundle(StaticBundle.VERSION, List.of())).hasMessageContaining("files");
    }

    @Test
    void thePublishRefusesWhatCouldLeaveTheAppOrMisstateItsFiles() {
        for (String path : List.of("../etc/passwd", "/index.html", "a/../b.html", "./index.html", "a//b.html", "a\\b.html", ".hidden", "a/.hidden",
                "index.html/", "", "in dex.html", "index.html?x=1", "%2e%2e/x", "a\u0000b", "x".repeat(PublishStatic.MAX_PATH + 1))) {
            assertThat(PublishStatic.isPath(path)).as(path).isFalse();
            assertThatThrownBy(() -> new PublishStatic(PublishStatic.VERSION, "a", "", "t", ROOT, 1, "index.html", Map.of("index.html", "text/html", path, "text/plain"), "none", "", AT))
                    .as(path).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new StaticFile(path, new byte[1])).as(path).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(PublishStatic.isPath("assets/status-2026.11_09~a.svg")).isTrue();
        for (String type : List.of("text/HTML", "text", "text/html; charset=", "text/html;charset=utf-8;x=1", "text/html\r\nSet-Cookie: x=1", "*/*")) {
            assertThatThrownBy(() -> new PublishStatic(PublishStatic.VERSION, "a", "", "t", ROOT, 1, "index.html", Map.of("index.html", type), "none", "", AT))
                    .as(type).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("media type");
        }
        assertThatThrownBy(() -> new PublishStatic(PublishStatic.VERSION, "a", "", "t", ROOT, 1, "main.html", Map.of("index.html", "text/html"), "none", "", AT))
                .hasMessageContaining("entry");
        assertThatThrownBy(() -> new PublishStatic(PublishStatic.VERSION, "a", "", "t", "not-a-root", 1, "index.html", Map.of("index.html", "text/html"), "none", "", AT))
                .hasMessageContaining("root");
        assertThatThrownBy(() -> new PublishStatic(PublishStatic.VERSION, "a", "", "t", ROOT, 0, "index.html", Map.of("index.html", "text/html"), "none", "", AT))
                .hasMessageContaining("size");
        assertThatThrownBy(() -> new PublishStatic(PublishStatic.VERSION, "a", "", "t", ROOT, 1, "index.html", Map.of(), "none", "", AT))
                .hasMessageContaining("files");
        assertThatThrownBy(() -> new PublishStatic(PublishStatic.VERSION, "a", "", "t", ROOT, 1, "index.html", Map.of("index.html", "text/html"), "tenant", "", AT))
                .hasMessageContaining("signOn");
        Map<String, String> many = new LinkedHashMap<>();
        for (int i = 0; i <= PublishStatic.MAX_FILES; i++) {
            many.put("f" + i + ".html", "text/html");
        }
        assertThatThrownBy(() -> new PublishStatic(PublishStatic.VERSION, "a", "", "t", ROOT, 1, "f0.html", many, "none", "", AT))
                .hasMessageContaining("files");
    }

    private static <T> void pin(String name, T value, Class<T> type) throws IOException {
        byte[] bytes = CODEC.toBytes(value);
        assertThat(CODEC.fromWire(bytes, type)).isEqualTo(value);
        Path vector = Path.of("src/test/resources/vectors/" + name + ".hex");
        String hex = HexFormat.of().formatHex(bytes);
        if (!Files.exists(vector)) {
            Files.createDirectories(vector.getParent());
            Files.writeString(vector, hex + "\n", StandardCharsets.UTF_8);
        }
        assertThat(hex).as(name + " pins the canonical bytes").isEqualTo(Files.readString(vector, StandardCharsets.UTF_8).trim());
    }
}
