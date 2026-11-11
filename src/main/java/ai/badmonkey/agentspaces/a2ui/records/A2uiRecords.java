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

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The open records of {@code agentspaces-a2ui}: the
 * entries a chat, a file tree and a progress line read, under the open namespace {@link #NAMESPACE}. A peer that maps
 * this package names each record {@code NAMESPACE + simple name} on the wire.
 *
 * <p>{@link ChatMessage} and {@link SharedFile} have a version 2 whose new fields are omitted while null, so a version 1
 * value encodes to exactly the version 1 bytes. SPEC §9.1 rule P1 lets a strict reader refuse a field it does not know,
 * so a member that reads version 2 advertises the member hint {@code a2ui.records=2} ({@link #hint()}), and a writer
 * writes version 2 into a group only when every member that reads the type advertises it ({@link #writesV2}).
 * Readers take both versions through 0.6.x.
 */
public final class A2uiRecords {

    /** The open namespace of the records. */
    public static final String NAMESPACE = "https://badmonkey.ai/agentspaces/a2ui/v1/";
    /** The member hint a reader of version 2 advertises, and its value. */
    public static final String HINT = "a2ui.records";
    public static final String HINT_VALUE = "2";
    /** The four records, in the order the SPEC lists them. */
    public static final List<Class<?>> RECORDS = List.of(ChatMessage.class, SharedFile.class, WorkingOn.class, ChatProgress.class);

    private A2uiRecords() {
    }

    /** The hint a member that reads version 2 advertises (for a node's credential hints). */
    public static Map.Entry<String, String> hint() {
        return Map.entry(HINT, HINT_VALUE);
    }

    /**
     * Whether a writer may write version 2 of {@link ChatMessage} or {@link SharedFile}: true when every reader's hints
     * carry {@code a2ui.records} at 2 or later, and when no member reads the type.
     *
     * @param readerHints the hints of each member that reads the type
     */
    public static boolean writesV2(Collection<Map<String, String>> readerHints) {
        for (Map<String, String> hints : readerHints) {
            String v = hints.get(HINT);
            if (v == null || !v.matches("\\d{1,9}") || Integer.parseInt(v) < 2) {
                return false;
            }
        }
        return true;
    }

    /** The version a writer writes for these readers: 2 when {@link #writesV2} allows it, else 1. */
    public static int versionFor(Collection<Map<String, String>> readerHints) {
        return writesV2(readerHints) ? 2 : 1;
    }

    /** A path in a tree of files: relative, {@code /}-separated, at most 512 bytes, no empty, {@code .} or {@code ..} segment, no backslash or control character. */
    static boolean validPath(String path) {
        if (path.isEmpty() || path.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 512 || path.startsWith("/")) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' || c < 0x20 || c == 0x7f) {
                return false;
            }
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    static String requirePath(String path, String what) {
        if (!validPath(path)) {
            throw new IllegalArgumentException(what + " path must be relative and /-separated, at most 512 bytes, with no empty, . or .. segment"
                    + " and no backslash or control character: " + path);
        }
        return path;
    }
}
