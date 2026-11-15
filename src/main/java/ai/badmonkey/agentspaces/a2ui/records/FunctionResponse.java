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

import ai.badmonkey.agentspaces.a2ui.SurfaceAction;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An agent's answer to a call a page made to one of its functions. A2UI 1.0's {@code callAgentFunction} reaches the
 * agent as a {@link SurfaceAction} whose action carries a {@code functionCallId} ({@link SurfaceAction#callsFunction()});
 * the agent answers with this entry in the group's {@code views} space, copying the app, the surface, the session and the
 * call's id from the action, and carrying the function's value or an error. A host delivers it, as 1.0's
 * {@code agentFunctionResponse}, to the page of that session alone, and drops an answer to a call it does not hold (one it
 * never took, one already answered, or one past its time). Type IRI
 * {@code https://badmonkey.ai/agentspaces/a2ui/v1/FunctionResponse}.
 *
 * <p>The entry is small and bounded, since every member of the group replicates {@code views}: the value is JSON (a map
 * with text keys, a list, text, a finite number, a boolean or null) of at most {@value #MAX_VALUE} characters as JSON,
 * counted with JSON's escapes, and {@value #MAX_DEPTH} levels deep, and a call's id is at most {@value #MAX_CALL_ID}
 * characters.
 *
 * @param schemaVersion  1
 * @param app            the app the call came through
 * @param surfaceId      the surface whose component made the call
 * @param session        the session the call came through, as the action carried it
 * @param functionCallId the call's id, as the action carried it
 * @param value          the function's value when it succeeded (null is a value: a function that returns nothing)
 * @param error          why it failed, or null when it succeeded; never beside a value
 * @param at             when the agent answered
 */
public record FunctionResponse(int schemaVersion, String app, String surfaceId, String session, String functionCallId, Object value, Failure error, Instant at) {

    public static final int VERSION = 1;
    public static final String IRI = A2uiRecords.NAMESPACE + "FunctionResponse";
    public static final String SPACE = "views";
    /** Long enough for a host to take it on its next look, short enough that answers do not pile up in a replicated space. */
    public static final Duration LEASE = Duration.ofMinutes(1);
    public static final int MAX_CALL_ID = 128;
    public static final int MAX_VALUE = 32_768;
    public static final int MAX_DEPTH = 16;
    public static final int MAX_CODE = 64;
    public static final int MAX_MESSAGE = 512;

    /** Why a function failed, in 1.0's shape: a code a page can branch on and a sentence a person reads. */
    public record Failure(String code, String message) {
        public Failure {
            Objects.requireNonNull(code, "code");
            if (code.isBlank() || code.length() > MAX_CODE || code.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("a failure's code is 1 to 64 characters with no control character: " + code);
            }
            message = message == null ? "" : message;
            if (message.length() > MAX_MESSAGE) {
                throw new IllegalArgumentException("a failure's message is at most 512 characters: " + message.length());
            }
        }
    }

    public FunctionResponse {
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(surfaceId, "surfaceId");
        session = session == null ? "" : session;
        requireCallId(functionCallId);
        if (error != null && value != null) {
            throw new IllegalArgumentException("an answer carries a value or an error, not both");
        }
        int[] size = {0};
        json(value, 0, size);
        at = at == null ? Instant.now() : at;
    }

    /** The answer that a call succeeded with {@code value}, to the call the action carried. */
    public static FunctionResponse answering(SurfaceAction call, Object value, Instant at) {
        return new FunctionResponse(VERSION, call.app(), call.surfaceId(), call.session(), requireCall(call), value, null, at);
    }

    /** The answer that a call failed, with a code and a sentence, to the call the action carried. */
    public static FunctionResponse failing(SurfaceAction call, String code, String message, Instant at) {
        return new FunctionResponse(VERSION, call.app(), call.surfaceId(), call.session(), requireCall(call), null, new Failure(code, message), at);
    }

    /** 1.0's {@code FunctionResponse}: the call's id and the value, or the call's id and the error. */
    public Map<String, Object> message() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("functionCallId", functionCallId);
        if (error != null) {
            m.put("error", Map.of("code", error.code(), "message", error.message()));
        } else {
            m.put("value", value);
        }
        return m;
    }

    /** A call's id as a page names it: 1 to 128 characters, no control character; returns it. */
    public static String requireCallId(String id) {
        Objects.requireNonNull(id, "functionCallId");
        if (id.isBlank() || id.length() > MAX_CALL_ID || id.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("a functionCallId is 1 to 128 characters with no control character");
        }
        return id;
    }

    private static String requireCall(SurfaceAction call) {
        if (!call.callsFunction()) {
            throw new IllegalArgumentException("the action on " + call.surfaceId() + " is no function call: it carries no functionCallId");
        }
        return call.functionCallId();
    }

    /**
     * Walks a value as JSON, counting its characters as JSON writes them, escapes included (review of 2026-11-13: text of
     * control characters counted one each passed the bound and was six times it as JSON), and refuses what JSON cannot
     * carry, a NaN or an infinity among them (JSON has no such number, so a page could not read the answer), or what is
     * too large.
     */
    private static void json(Object v, int depth, int[] size) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("a function's value is at most " + MAX_DEPTH + " levels deep");
        }
        if (v == null || v instanceof Boolean) {
            size[0] += 5;
        } else if (v instanceof Number n) {
            if ((n instanceof Double d && !Double.isFinite(d)) || (n instanceof Float f && !Float.isFinite(f))) {
                throw new IllegalArgumentException("a function's value is JSON, which has no NaN or infinite number: " + n);
            }
            size[0] += String.valueOf(n).length();
        } else if (v instanceof String s) {
            size[0] += escaped(s) + 2;
        } else if (v instanceof Map<?, ?> m) {
            size[0] += 2;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!(e.getKey() instanceof String k)) {
                    throw new IllegalArgumentException("a function's value names each member with text");
                }
                size[0] += escaped(k) + 4;
                json(e.getValue(), depth + 1, size);
            }
        } else if (v instanceof List<?> l) {
            size[0] += 2;
            for (Object o : l) {
                size[0] += 1;
                json(o, depth + 1, size);
            }
        } else {
            throw new IllegalArgumentException("a function's value is JSON: a map, a list, text, a number, a boolean or null, not a " + v.getClass().getSimpleName());
        }
        if (size[0] > MAX_VALUE) {
            throw new IllegalArgumentException("a function's value is at most " + MAX_VALUE + " characters as JSON");
        }
    }

    /**
     * The characters a text takes inside JSON's quotes: a quote, a backslash and the control characters with a short
     * escape take two, every other control character six (a backslash, a u and four hex digits), and the rest one. A text
     * past the bound is not walked to the end.
     */
    private static int escaped(String s) {
        int n = 0;
        for (int i = 0; i < s.length() && n <= MAX_VALUE; i++) {
            char c = s.charAt(i);
            n += c == '"' || c == '\\' || c == '\b' || c == '\f' || c == '\n' || c == '\r' || c == '\t' ? 2 : c < 0x20 ? 6 : 1;
        }
        return n;
    }
}
