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

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The document a {@link PublishStatic} names by its content root: every file of a static app, in path order, each path
 * once. A publisher encodes it with {@link CborCodec#toBytes} and publishes the bytes on the content swarm; a host
 * fetches them by the root, which the swarm verifies piece by piece, reads them back with {@link #decode} under the
 * strict wire read, and serves the files {@link #open} returns. Version 1.
 */
public record StaticBundle(int schemaVersion, List<StaticFile> files) {

    public static final int VERSION = 1;

    public StaticBundle {
        Objects.requireNonNull(files, "files");
        files = List.copyOf(files);
        if (files.isEmpty() || files.size() > PublishStatic.MAX_FILES) {
            throw new IllegalArgumentException("a bundle holds 1 to " + PublishStatic.MAX_FILES + " files: " + files.size());
        }
        for (int i = 1; i < files.size(); i++) {
            if (files.get(i - 1).path().compareTo(files.get(i).path()) >= 0) {
                throw new IllegalArgumentException("a bundle holds its files in path order, each path once: " + files.get(i).path());
            }
        }
    }

    /**
     * The bundle of some files, put in path order.
     *
     * @param files each file's bytes by its path
     * @return the bundle
     */
    public static StaticBundle of(Map<String, byte[]> files) {
        List<StaticFile> out = new ArrayList<>();
        new TreeMap<>(files).forEach((path, bytes) -> out.add(new StaticFile(path, bytes)));
        return new StaticBundle(VERSION, out);
    }

    /**
     * Reads a bundle from the bytes a fetch delivered, under the strict wire read: one canonical item, no field the
     * schema does not name, and an encoding that gives back exactly these bytes (SPEC §9.1).
     *
     * @param bytes the document
     * @return the bundle
     * @throws IllegalArgumentException when the bytes are not a bundle's canonical encoding
     */
    public static StaticBundle decode(byte[] bytes) {
        try {
            return CborCodec.defaultCodec().fromWire(bytes, StaticBundle.class);
        } catch (UncheckedIOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("the bytes are not a static bundle: " + e.getMessage(), e);
        }
    }

    /**
     * The files to serve for a publish: each file's bytes by its path, when the bundle holds exactly the files the
     * publish names.
     *
     * @param publish the record that names this bundle
     * @return the files by path
     * @throws IllegalArgumentException naming a file the publish names and the bundle lacks, or the reverse
     */
    public Map<String, byte[]> open(PublishStatic publish) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (StaticFile f : files) {
            if (!publish.files().containsKey(f.path())) {
                throw new IllegalArgumentException("the bundle holds " + f.path() + ", which the publish of " + publish.app() + " does not name");
            }
            out.put(f.path(), f.bytes());
        }
        for (String path : publish.files().keySet()) {
            if (!out.containsKey(path)) {
                throw new IllegalArgumentException("the publish of " + publish.app() + " names " + path + ", which the bundle lacks");
            }
        }
        return Collections.unmodifiableMap(out);
    }
}
