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

import ai.badmonkey.agentspaces.api.ad.CardSpill;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * An agent's request that a static app be served for end users: files the agent assembled from extracted data, a
 * visualization people view, published as one {@link StaticBundle} on the group's content swarm and served as is by its
 * content root, reading nothing live. The record travels beside {@link Publish} in the group's {@code views} space and
 * is a new record type, because the strict wire refuses a field {@code Publish} does not name (SPEC §9.1, P1).
 *
 * <p>{@code root} is the bundle's content root and {@code size} its length in bytes, so a host bounds the fetch before
 * it asks for a piece and every published version is immutable. {@code entry} is the file a person opens first.
 * {@code files} maps each file's path in the bundle to the media type a host serves it under: RFC 6838's
 * {@code type/subtype} in lower case, with at most a {@code charset} parameter. A path is one to {@link #MAX_PATH}
 * characters of segments joined by {@code /}, each segment a letter or digit followed by letters, digits, {@code .},
 * {@code _}, {@code ~} or {@code -}, so no path can leave the app's directory. {@code signOn} is {@code idp} (the
 * default: the identity provider of whoever runs the host guards the app) or {@code none} (anyone may view it). The
 * newest record per app, a {@code Publish} included, is the one in force, and a {@code Publish} with
 * {@code unpublish: true} takes a static app down too. Version 1.
 */
public record PublishStatic(int schemaVersion, String app, String from, String title, String root, long size, String entry,
                            Map<String, String> files, String signOn, String host, Instant at) {

    public static final int VERSION = 1;
    public static final List<String> SIGN_ONS = Publish.SIGN_ONS;
    /** The file a person opens first when the publisher names none. */
    public static final String DEFAULT_ENTRY = "index.html";
    /** The most files one bundle holds. */
    public static final int MAX_FILES = 256;
    /** The longest path, in characters. */
    public static final int MAX_PATH = 256;

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._~-]*");
    private static final String NAME = "[a-z0-9][a-z0-9!#$&^_.+-]{0,126}";
    private static final Pattern MEDIA_TYPE = Pattern.compile(NAME + "/" + NAME + "(; ?charset=[a-z0-9_-]{1,40})?");

    public PublishStatic {
        Objects.requireNonNull(app, "app");
        if (app.isBlank()) {
            throw new IllegalArgumentException("a static publish names its app");
        }
        from = from == null ? "" : from;
        title = title == null || title.isBlank() ? app : title;
        Objects.requireNonNull(root, "root");
        if (!CardSpill.isRoot(root)) {
            throw new IllegalArgumentException("root is not a content root of codec raw over sha2-256: " + root);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size is the bundle's length in bytes, at least 1: " + size);
        }
        entry = entry == null || entry.isBlank() ? DEFAULT_ENTRY : entry;
        Objects.requireNonNull(files, "files");
        if (files.isEmpty() || files.size() > MAX_FILES) {
            throw new IllegalArgumentException("files names 1 to " + MAX_FILES + " files: " + files.size());
        }
        for (Map.Entry<String, String> f : files.entrySet()) {
            if (!isPath(f.getKey())) {
                throw new IllegalArgumentException("files names a path outside the app's directory or its form: " + f.getKey());
            }
            if (f.getValue() == null || !MEDIA_TYPE.matcher(f.getValue()).matches()) {
                throw new IllegalArgumentException("files names a media type outside RFC 6838's lower-case form for " + f.getKey() + ": " + f.getValue());
            }
        }
        files = java.util.Collections.unmodifiableMap(new TreeMap<>(files));
        if (!files.containsKey(entry)) {
            throw new IllegalArgumentException("the entry " + entry + " is not one of the files");
        }
        signOn = signOn == null || signOn.isBlank() ? "idp" : signOn;
        if (!SIGN_ONS.contains(signOn)) {
            throw new IllegalArgumentException("signOn must be one of " + SIGN_ONS + ": " + signOn);
        }
        host = host == null ? "" : host;
        Objects.requireNonNull(at, "at");
    }

    /**
     * Whether a text is a path a static bundle may hold: 1 to {@link #MAX_PATH} characters, segments joined by
     * {@code /}, each a letter or digit followed by letters, digits, {@code .}, {@code _}, {@code ~} or {@code -}.
     * No such path starts at the root, climbs out with {@code ..}, names a hidden file, or needs escaping in a URL.
     *
     * @param path the text
     * @return true when a bundle may hold it
     */
    public static boolean isPath(String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_PATH) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (!SEGMENT.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }
}
