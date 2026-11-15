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

import ai.badmonkey.agentspaces.a2ui.records.FunctionResponse;
import ai.badmonkey.agentspaces.api.space.Lease;
import ai.badmonkey.agentspaces.api.space.Space;
import ai.badmonkey.agentspaces.api.space.Template;
import ai.badmonkey.agentspaces.common.codec.CborCodec;
import ai.badmonkey.agentspaces.identity.PeerIdentity;
import ai.badmonkey.agentspaces.space.local.LocalSpace;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GEO-V-07: a component's action may call one of the drawing agent's functions (A2UI 1.0's {@code callAgentFunction}).
 * The builder writes 1.0's {@code functionCall} with the binding checks of every other builder; a host carries the call
 * to the agent as a {@link SurfaceAction} with a {@code functionCallId}; the agent answers with a bounded
 * {@link FunctionResponse} the host delivers to the calling page as 1.0's {@code agentFunctionResponse}.
 */
class AgentFunctionsTest {

    private static final Instant FIXED = Instant.parse("2026-11-13T09:00:00Z");
    private static final CborCodec CODEC = CborCodec.defaultCodec();

    static SurfaceAction call(String id, String function, Map<String, Object> args) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("name", function);
        action.put("surfaceId", "port");
        action.put("timestamp", FIXED.toString());
        action.put("context", args);
        action.put(SurfaceAction.FUNCTION_CALL_ID, id);
        return new SurfaceAction(SurfaceAction.VERSION, "app-harbour", "port", "as-ada", action, FIXED);
    }

    @Test
    void theBuilderWritesA10FunctionCallWithItsArgumentsChecked() {
        Map<String, Object> button = Surfaces.functionButton("lookUp", "Look up", "berthOf", Map.of("vessel", Map.of("@path", "/selected"), "limit", 3), "primary");
        assertThat(button).containsEntry("component", "Button").containsEntry("child", "lookUpText").containsEntry("variant", "primary");
        assertThat(button.get("action")).isEqualTo(Map.of("functionCall", Map.of("@call", "berthOf", "args", Map.of("vessel", Map.of("@path", "/selected"), "limit", 3))));
        assertThat(Surfaces.functionCall("refresh", Map.of())).as("no arguments, no args key").isEqualTo(Map.of("functionCall", Map.of("@call", "refresh")));
        // the binding checks of every builder: v0.9.1's forms are refused, naming where they sit
        assertThatThrownBy(() -> Surfaces.functionCall("berthOf", Map.of("vessel", Map.of("path", "/selected")))).hasMessageContaining("functionCall.args.vessel").hasMessageContaining("@path");
        assertThatThrownBy(() -> Surfaces.functionCall("berthOf", Map.of("when", List.of(Map.of("call", "formatDate"))))).hasMessageContaining("functionCall.args.when[0]").hasMessageContaining("@call");
        // the name: an identifier, never one the renderer runs itself
        assertThatThrownBy(() -> Surfaces.functionCall("berth-of", Map.of())).hasMessageContaining("identifier");
        assertThatThrownBy(() -> Surfaces.functionCall("@index", Map.of())).hasMessageContaining("identifier");
        assertThatThrownBy(() -> Surfaces.functionCall("x".repeat(65), Map.of())).hasMessageContaining("64");
        assertThatThrownBy(() -> Surfaces.functionCall("openUrl", Map.of())).hasMessageContaining("renderer runs itself");
        assertThatThrownBy(() -> Surfaces.functionButton("look-up", "Look up", "berthOf", Map.of(), null)).hasMessageContaining("UAX #31");
    }

    @Test
    void anActionThatCarriesACallIdIsAFunctionCall() {
        SurfaceAction c = call("c-1", "berthOf", Map.of("vessel", "Aurora"));
        assertThat(c.callsFunction()).isTrue();
        assertThat(c.functionCallId()).isEqualTo("c-1");
        assertThat(c.function()).isEqualTo("berthOf");
        assertThat(c.args()).isEqualTo(Map.of("vessel", "Aurora"));
        SurfaceAction press = new SurfaceAction(SurfaceAction.VERSION, "app-harbour", "port", "as-ada", Map.of("name", "acknowledge"), FIXED);
        assertThat(press.callsFunction()).isFalse();
        assertThat(press.functionCallId()).isEmpty();
        assertThat(press.args()).isEmpty();
        // the accessors add nothing to the record's bytes: an action reads back as it was written
        assertThat(CODEC.toJson(c)).doesNotContain("callsFunction");
        assertThat(CODEC.fromDocument(CODEC.toBytes(c), SurfaceAction.class)).isEqualTo(c);
    }

    @Test
    void anAnswerCarriesAValueOrAnErrorAndIsBounded() {
        SurfaceAction c = call("c-1", "berthOf", Map.of("vessel", "Aurora"));
        FunctionResponse ok = FunctionResponse.answering(c, Map.of("berth", "B4", "eta", List.of(9, 30)), FIXED);
        assertThat(ok.session()).as("the session the call came through").isEqualTo("as-ada");
        assertThat(ok.app()).isEqualTo("app-harbour");
        assertThat(ok.surfaceId()).isEqualTo("port");
        assertThat(ok.message()).isEqualTo(Map.of("functionCallId", "c-1", "value", Map.of("berth", "B4", "eta", List.of(9, 30))));
        assertThat(CODEC.fromDocument(CODEC.toBytes(ok), FunctionResponse.class)).as("an application document round-trips").isEqualTo(ok);
        FunctionResponse nothing = FunctionResponse.answering(c, null, FIXED);
        Map<String, Object> none = new LinkedHashMap<>();
        none.put("functionCallId", "c-1");
        none.put("value", null);
        assertThat(nothing.message()).as("a function that returns nothing answers null, which 1.0 reads as a value").isEqualTo(none);
        FunctionResponse failed = FunctionResponse.failing(c, "NOT_FOUND", "No vessel Aurora in port.", FIXED);
        assertThat(failed.message()).isEqualTo(Map.of("functionCallId", "c-1", "error", Map.of("code", "NOT_FOUND", "message", "No vessel Aurora in port.")));
        assertThat(CODEC.fromDocument(CODEC.toBytes(failed), FunctionResponse.class)).isEqualTo(failed);

        // the bounds: one of value and error; a call's id; JSON alone, and not too large or deep
        assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "c", "v", new FunctionResponse.Failure("E", ""), FIXED)).hasMessageContaining("not both");
        assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "", "v", null, FIXED)).hasMessageContaining("functionCallId");
        assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "c".repeat(129), "v", null, FIXED)).hasMessageContaining("functionCallId");
        assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "c\n1", "v", null, FIXED)).hasMessageContaining("control");
        assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "c", "x".repeat(FunctionResponse.MAX_VALUE), null, FIXED)).hasMessageContaining("characters as JSON");
        assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "c", new Object(), null, FIXED)).hasMessageContaining("JSON");
        assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "c", Map.of(1, "one"), null, FIXED)).hasMessageContaining("text");
        Object deep = "leaf";
        for (int i = 0; i <= FunctionResponse.MAX_DEPTH; i++) {
            deep = List.of(deep);
        }
        Object tooDeep = deep;
        assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "c", tooDeep, null, FIXED)).hasMessageContaining("levels deep");
        assertThatThrownBy(() -> new FunctionResponse.Failure("", "m")).hasMessageContaining("code");
        assertThatThrownBy(() -> new FunctionResponse.Failure("E", "m".repeat(513))).hasMessageContaining("512");
        assertThatCode(() -> new FunctionResponse(1, "a", "s", "", "c", "x".repeat(FunctionResponse.MAX_VALUE - 100), null, FIXED)).doesNotThrowAnyException();
        // the bound is on the value as JSON writes it, escapes included: a control character without a short escape takes six
        assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "c", "\u0001".repeat(FunctionResponse.MAX_VALUE / 6 + 1), null, FIXED))
                .hasMessageContaining("characters as JSON");
        assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "c", "\n".repeat(FunctionResponse.MAX_VALUE / 2), null, FIXED)).hasMessageContaining("characters as JSON");
        assertThatCode(() -> new FunctionResponse(1, "a", "s", "", "c", "\n".repeat(FunctionResponse.MAX_VALUE / 2 - 2), null, FIXED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "c", Map.of("\"".repeat(FunctionResponse.MAX_VALUE / 2), 1), null, FIXED))
                .as("a member's name is escaped too").hasMessageContaining("characters as JSON");
        // JSON has no NaN or infinity, so a page could not read such an answer
        for (Object notJson : List.<Object>of(Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Map.of("tide", Double.NaN))) {
            assertThatThrownBy(() -> new FunctionResponse(1, "a", "s", "", "c", notJson, null, FIXED)).as(String.valueOf(notJson)).hasMessageContaining("NaN or infinite");
        }
        assertThatCode(() -> new FunctionResponse(1, "a", "s", "", "c", List.of(2.4, 1e300, -0.0, 7L), null, FIXED)).doesNotThrowAnyException();
        // an action that calls no function has nothing to answer
        SurfaceAction press = new SurfaceAction(SurfaceAction.VERSION, "app-harbour", "port", "as-ada", Map.of("name", "acknowledge"), FIXED);
        assertThatThrownBy(() -> FunctionResponse.answering(press, "x", FIXED)).hasMessageContaining("no function call");
    }

    @Test
    void thePenHandsOutEachCallOnceAndWritesItsAnswer() {
        Space views = LocalSpace.builder("views", PeerIdentity.generate().agent("test")).build();
        // two calls of one function stamped with the same instant are two calls
        views.write(call("c-1", "berthOf", Map.of("vessel", "Aurora")), Lease.of(Duration.ofMinutes(5)), Map.of("kind", "surface-action"));
        views.write(call("c-2", "berthOf", Map.of("vessel", "Borealis")), Lease.of(Duration.ofMinutes(5)), Map.of("kind", "surface-action"));
        Surfaces pen = new Surfaces(views, "app-harbour", InstantSource.fixed(FIXED));
        List<SurfaceAction> calls = pen.actions();
        assertThat(calls).extracting(SurfaceAction::functionCallId).containsExactlyInAnyOrder("c-1", "c-2");
        assertThat(pen.actions()).as("each once").isEmpty();
        List<FunctionResponse> written = new ArrayList<>();
        for (SurfaceAction c : calls) {
            written.add("Aurora".equals(c.args().get("vessel")) ? pen.answer(c, Map.of("berth", "B4")) : pen.fail(c, "NOT_FOUND", "No vessel Borealis in port."));
        }
        assertThat(views.readAll(Template.of(FunctionResponse.class), 10)).containsExactlyInAnyOrderElementsOf(written);
        assertThat(written).allSatisfy(r -> assertThat(r.at()).isEqualTo(FIXED));
    }
}
