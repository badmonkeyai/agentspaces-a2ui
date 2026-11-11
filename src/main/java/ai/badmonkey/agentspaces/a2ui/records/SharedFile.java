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
package ai.badmonkey.agentspaces.a2ui.records;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A file on the content swarm, named by its root and digest, with who shared it and with whom, in the group's
 * {@code shared} space; the bytes never enter an entry (a record that began on a platform and moved to the
 * open namespace with its fields unchanged). Type IRI {@code https://badmonkey.ai/agentspaces/a2ui/v1/SharedFile}.
 *
 * <p>Version 2 adds {@code path}, the file's place in a tree (relative, {@code /}-separated, at most 512 bytes, no
 * empty, {@code .} or {@code ..} segment, no leading {@code /}, backslash or control character; empty puts the file at
 * the top under its name), and {@code replaces}, the id of the version this one succeeds. Both are omitted from the
 * wire while null, so a version 1 value encodes to the version 1 bytes.
 *
 * @param schemaVersion 1 or 2
 * @param id            the share id
 * @param name          the file name
 * @param mediaType     the media type (RFC 6838)
 * @param bytes         the size
 * @param root          the content swarm root
 * @param sha256        the digest, {@code sha256:<hex>}
 * @param by            who shared it
 * @param sharedWith    principals, groups, or apps
 * @param at            when
 * @param path          version 2: the place in the tree
 * @param replaces      version 2: the share id this version succeeds, empty for a first version
 */
public record SharedFile(int schemaVersion, String id, String name, String mediaType, long bytes, String root, String sha256, String by,
                         List<String> sharedWith, Instant at,
                         @JsonInclude(JsonInclude.Include.NON_NULL) String path,
                         @JsonInclude(JsonInclude.Include.NON_NULL) String replaces) {

    public static final int V1 = 1;
    public static final int V2 = 2;
    public static final String IRI = A2uiRecords.NAMESPACE + "SharedFile";
    public static final String SPACE = "shared";

    public SharedFile {
        if (schemaVersion != V1 && schemaVersion != V2) {
            throw new IllegalArgumentException("a SharedFile has schemaVersion 1 or 2: " + schemaVersion);
        }
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a shared file has a name");
        }
        mediaType = mediaType == null || mediaType.isBlank() ? "application/octet-stream" : mediaType;
        if (bytes < 0) {
            throw new IllegalArgumentException("a file's size is not negative");
        }
        root = root == null ? "" : root;
        sha256 = sha256 == null ? "" : sha256;
        by = by == null ? "" : by;
        sharedWith = sharedWith == null ? List.of() : List.copyOf(sharedWith);
        // a page's write gets the instant from the feed and an SDK's may leave it out, so a missing instant is the time the share was seen
        at = at == null ? Instant.now() : at;
        if (schemaVersion == V1) {
            if (path != null || replaces != null) {
                throw new IllegalArgumentException("a version 1 SharedFile carries no path and no replaces; write version 2 for them");
            }
        } else {
            path = path == null || path.isEmpty() ? "" : A2uiRecords.requirePath(path, "a shared file's");
            replaces = replaces == null ? "" : replaces;
        }
    }

    /** Whether a path is one {@code path} takes, so no path walks out of the tree: the feed answers 422 to the rest. */
    public static boolean validPath(String path) {
        return path != null && A2uiRecords.validPath(path);
    }

    /** This file in the version a writer chose: version 1 drops {@code path} and {@code replaces}; version 2 takes their defaults. */
    public SharedFile asVersion(int version) {
        if (version == schemaVersion) {
            return this;
        }
        return version == V1 ? new SharedFile(V1, id, name, mediaType, bytes, root, sha256, by, sharedWith, at, null, null)
                : new SharedFile(version, id, name, mediaType, bytes, root, sha256, by, sharedWith, at, path, replaces);
    }
}
