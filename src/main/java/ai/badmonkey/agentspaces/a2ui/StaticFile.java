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

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One file of a {@link StaticBundle}: its path, under the rules of {@link PublishStatic#isPath}, and its bytes as the
 * publisher assembled them. Version 1, with the bundle.
 */
public record StaticFile(String path, byte[] bytes) {

    public StaticFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(bytes, "bytes");
        if (!PublishStatic.isPath(path)) {
            throw new IllegalArgumentException("a static file's path stays inside the app's directory and its form: " + path);
        }
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StaticFile f && path.equals(f.path) && Arrays.equals(bytes, f.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * path.hashCode() + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "StaticFile[path=" + path + ", bytes=" + bytes.length + " (" + HexFormat.of().formatHex(bytes, 0, Math.min(8, bytes.length)) + (bytes.length > 8 ? "...)" : ")") + "]";
    }
}
