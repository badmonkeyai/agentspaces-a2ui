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

import ai.badmonkey.agentspaces.common.codec.CborCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The open records of spiral 6 (S6-U-17, ISSUE-A2uiAppComponents FR-9, FR-10, SR-3): each version pinned as canonical
 * CBOR beside its JSON, the bounds every constructor enforces, a v1 value encoding to exactly the v1 bytes (so an older
 * reader never meets a field it does not know), and the writer's rule over the member hint {@code a2ui.records=2}.
 */
class OpenRecordsTest {

    static final CborCodec CODEC = CborCodec.defaultCodec();
    static final Instant AT = Instant.parse("2026-11-10T09:00:00Z");

    static ChatMessage chatV1() {
        return new ChatMessage(ChatMessage.V1, "m-1", "main", new ChatMessage.From(ChatMessage.From.PERSON, "ada", ""), "What changed in q3?",
                List.of("file-q3"), "", AT, null, null);
    }

    static ChatMessage chatV2() {
        return new ChatMessage(ChatMessage.V2, "m-1-reply", "main", new ChatMessage.From(ChatMessage.From.AGENT, "workbench", "peer-7"),
                "Spend rose **four percent**.", List.of(), "m-1", AT, ChatMessage.MARKDOWN,
                List.of(new ChatMessage.Cite("file-q3", "data/q3-expenses.csv", "q3-expenses.csv")));
    }

    /** A desk's request in a chat (version 3, srad/hitl.md §3.8). */
    static ChatMessage chatV3Confirm() {
        return new ChatMessage(ChatMessage.V3, "m-desk-1", "leads", new ChatMessage.From(ChatMessage.From.AGENT, "refunds-desk", "peer-9"),
                "Refund 250 dollars on order o-7? Reply approve or reject (code K7Q2).", List.of(), "", AT, ChatMessage.PLAIN, List.of(),
                new ChatMessage.Confirm("inv-7", "#refunds", "approval", "Refund 250 dollars on order o-7?", "orders#refund", "{\"amount\":250,\"order\":\"o-7\"}",
                        "Refunds order o-7 in full.", AT.plusSeconds(3600), List.of("approve", "reject"), "optional", "K7Q2", "pending", "", List.of(), "", 0, 2),
                null);
    }

    /** A person's answer to it (version 3). */
    static ChatMessage chatV3Answer() {
        return new ChatMessage(ChatMessage.V3, "m-ada-2", "leads", new ChatMessage.From(ChatMessage.From.PERSON, "ada", ""), "approve K7Q2 within policy",
                List.of(), "m-desk-1", AT.plusSeconds(60), ChatMessage.PLAIN, List.of(), null, new ChatMessage.Answer("inv-7", "approve", "within policy", ""));
    }

    static SharedFile fileV1() {
        return new SharedFile(SharedFile.V1, "file-q3", "q3-expenses.csv", "text/csv", 2048, "bafyroot", "sha256:ab", "ada", List.of("claims-desk"), AT, null, null);
    }

    static SharedFile fileV2() {
        return new SharedFile(SharedFile.V2, "file-note", "summary.md", "text/markdown", 512, "bafynote", "sha256:cd", "workbench", List.of(), AT,
                "notes/summary.md", "file-note-0");
    }

    static WorkingOn workingOn() {
        return new WorkingOn(WorkingOn.VERSION, "working-workbench-main", "workbench", "main", List.of("data/q3-expenses.csv"), "reading q3-expenses.csv",
                TaskStates.WORKING, AT);
    }

    static ChatProgress progress() {
        return new ChatProgress(ChatProgress.VERSION, ChatProgress.idFor("m-1"), "main", "m-1", "workbench", TaskStates.WORKING,
                List.of(new ChatProgress.Step("read q3-expenses.csv", ChatProgress.Step.READ, "data/q3-expenses.csv", AT),
                        new ChatProgress.Step("summarise", ChatProgress.Step.TOOL, "", AT.plusSeconds(2))), AT.plusSeconds(2));
    }

    @Test
    void everyVersionIsPinnedAsCanonicalCborBesideItsJson() throws IOException {
        pin("chat_message_v1", chatV1(), ChatMessage.class);
        pin("chat_message_v2", chatV2(), ChatMessage.class);
        pin("chat_message_v3_confirm", chatV3Confirm(), ChatMessage.class);
        pin("chat_message_v3_answer", chatV3Answer(), ChatMessage.class);
        pin("shared_file_v1", fileV1(), SharedFile.class);
        pin("shared_file_v2", fileV2(), SharedFile.class);
        pin("working_on_v1", workingOn(), WorkingOn.class);
        pin("chat_progress_v1", progress(), ChatProgress.class);
    }

    @Test
    void aVersionOneValueCarriesNoFieldOfVersionTwo() {
        assertThat(CODEC.toJson(chatV1())).doesNotContain("format").doesNotContain("cites");
        assertThat(CODEC.toJson(fileV1())).doesNotContain("\"path\"").doesNotContain("replaces");
        assertThat(chatV2().asVersion(ChatMessage.V1)).isEqualTo(new ChatMessage(ChatMessage.V1, "m-1-reply", "main", chatV2().from(), chatV2().text(), List.of(), "m-1", AT, null, null));
        assertThat(fileV2().asVersion(SharedFile.V1).path()).isNull();
        assertThat(chatV1().asVersion(ChatMessage.V1)).isEqualTo(chatV1());
        assertThat(chatV1().asVersion(ChatMessage.V2).format()).as("a v1 value raised to v2 takes the defaults").isEqualTo(ChatMessage.PLAIN);
        // a v1 document reads as a v1 value (the v2 fields stay absent), and a v2 one keeps its fields
        assertThat(CODEC.fromDocument(CODEC.toBytes(chatV1()), ChatMessage.class)).isEqualTo(chatV1());
        assertThat(CODEC.fromDocument(CODEC.toBytes(fileV2()), SharedFile.class)).isEqualTo(fileV2());
        assertThatThrownBy(() -> new ChatMessage(ChatMessage.V1, "m", "k", null, "", List.of(), "", AT, ChatMessage.MARKDOWN, null))
                .hasMessageContaining("version 1").hasMessageContaining("format");
        assertThatThrownBy(() -> new SharedFile(SharedFile.V1, "f", "a.txt", "", 1, "", "", "", List.of(), AT, "a.txt", null))
                .hasMessageContaining("version 1").hasMessageContaining("path");
        assertThatThrownBy(() -> new ChatMessage(4, "m", "k", null, "", List.of(), "", AT, null, null)).hasMessageContaining("schemaVersion");
    }

    @Test
    void versionThreeCarriesARequestOrAnAnswerAndOlderVersionsNeither() {
        ChatMessage ask = chatV3Confirm();
        assertThat(ask.confirm().pending()).isTrue();
        assertThat(CODEC.toJson(chatV2().asVersion(ChatMessage.V3))).as("a v3 line with neither block").doesNotContain("confirm").doesNotContain("answer");
        // to an older reader the desk writes the text alone, which says how to reply in words
        assertThat(ask.asVersion(ChatMessage.V2).confirm()).isNull();
        assertThat(ask.asVersion(ChatMessage.V1).text()).contains("Reply approve or reject");
        assertThatThrownBy(() -> new ChatMessage(ChatMessage.V2, "m", "k", null, "", List.of(), "", AT, null, null, ask.confirm(), null))
                .hasMessageContaining("version 3");
        assertThatThrownBy(() -> new ChatMessage(ChatMessage.V3, "m", "k", null, "", List.of(), "", AT, null, null, ask.confirm(),
                new ChatMessage.Answer("inv-7", "approve", "", ""))).hasMessageContaining("not both");
        // the decision, as the desk's later line shows it
        ChatMessage.Confirm done = ask.confirm().decided("approved", List.of("person:ada", "person:bo"), "ok", 2);
        assertThat(done.pending()).isFalse();
        assertThat(done.by()).containsExactly("person:ada", "person:bo");
        assertThat(done.question()).isEqualTo(ask.confirm().question());
        // the defaults, and the bounds
        ChatMessage.Confirm bare = new ChatMessage.Confirm("inv-1", null, null, "Go?", null, null, null, null, null, null, null, null, null, null, null, 0, 0);
        assertThat(bare.kind()).isEqualTo("approval");
        assertThat(bare.options()).containsExactly("approve", "reject");
        assertThat(bare.reasons()).isEqualTo("optional");
        assertThat(bare.arguments()).isEqualTo("{}");
        assertThat(bare.quorum()).isEqualTo(1);
        assertThatThrownBy(() -> new ChatMessage.Confirm("", null, null, "Go?", null, null, null, null, null, null, null, null, null, null, null, 0, 0))
                .hasMessageContaining("request");
        assertThatThrownBy(() -> new ChatMessage.Confirm("i", null, null, " ", null, null, null, null, null, null, null, null, null, null, null, 0, 0))
                .hasMessageContaining("question");
        assertThatThrownBy(() -> new ChatMessage.Confirm("i", null, "vote", "Go?", null, null, null, null, null, null, null, null, null, null, null, 0, 0))
                .hasMessageContaining("kind");
        assertThatThrownBy(() -> new ChatMessage.Confirm("i", null, null, "Go?", null, null, null, null, List.of("maybe"), null, null, null, null, null, null, 0, 0))
                .hasMessageContaining("options");
        assertThatThrownBy(() -> new ChatMessage.Confirm("i", null, null, "Go?", null, null, null, null, null, "always", null, null, null, null, null, 0, 0))
                .hasMessageContaining("reasons");
        assertThatThrownBy(() -> new ChatMessage.Confirm("i", null, null, "Go?", null, null, null, null, null, null, null, "decided", "", null, null, 0, 0))
                .hasMessageContaining("verdict");
        assertThatThrownBy(() -> new ChatMessage.Confirm("i", null, null, "Go?", null, null, null, null, null, null, null, "pending", "approved", null, null, 0, 0))
                .hasMessageContaining("verdict");
        assertThatThrownBy(() -> new ChatMessage.Confirm("i", null, null, "x".repeat(2001), null, null, null, null, null, null, null, null, null, null, null, 0, 0))
                .hasMessageContaining("2000");
        assertThatThrownBy(() -> new ChatMessage.Answer("inv-7", "yes", "", "")).hasMessageContaining("verdict");
        assertThatThrownBy(() -> new ChatMessage.Answer("inv-7", "approve", "", "{}")).hasMessageContaining("amend");
        assertThatThrownBy(() -> new ChatMessage.Answer(" ", "approve", "", "")).hasMessageContaining("request");
    }

    @Test
    void theVersionTwoFieldsTakeTheirDefaults() {
        ChatMessage m = new ChatMessage(ChatMessage.V2, "m", "k", null, null, null, null, AT, null, null);
        assertThat(m.format()).isEqualTo(ChatMessage.PLAIN);
        assertThat(m.cites()).isEmpty();
        SharedFile f = new SharedFile(SharedFile.V2, "f", "a.txt", null, 1, null, null, null, null, AT, null, null);
        assertThat(f.path()).as("an empty path puts the file at the top under its name").isEmpty();
        assertThat(f.replaces()).isEmpty();
        assertThat(f.mediaType()).isEqualTo("application/octet-stream");
    }

    @Test
    void theBoundsHoldInEveryConstructor() {
        // SR-3 and S6-Q-SEC-04: a path that walks out of the tree
        for (String bad : List.of("/etc/passwd", "a/../b", "./a", "a//b", "a/", "..", "a\\b", "a\u0001b", "a/\u007fb", "x".repeat(513))) {
            assertThatThrownBy(() -> new SharedFile(SharedFile.V2, "f", "a", "", 1, "", "", "", List.of(), AT, bad, ""))
                    .as("path %s", bad).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("path");
        }
        assertThat(new SharedFile(SharedFile.V2, "f", "a", "", 1, "", "", "", List.of(), AT, "é".repeat(256), "").path()).as("512 bytes").hasSize(256);
        assertThatThrownBy(() -> new SharedFile(SharedFile.V2, "f", "a", "", 1, "", "", "", List.of(), AT, "é".repeat(257), "")).hasMessageContaining("512");
        assertThat(SharedFile.validPath("notes/2026/summary.md")).isTrue();
        assertThat(SharedFile.validPath("notes/../summary.md")).isFalse();
        // seventeen cites, an unknown format
        List<ChatMessage.Cite> seventeen = Collections.nCopies(17, new ChatMessage.Cite("f", "", ""));
        assertThatThrownBy(() -> new ChatMessage(ChatMessage.V2, "m", "k", null, "", List.of(), "", AT, null, seventeen)).hasMessageContaining("16");
        assertThatThrownBy(() -> new ChatMessage(ChatMessage.V2, "m", "k", null, "", List.of(), "", AT, "text/html", null)).hasMessageContaining("format");
        assertThatThrownBy(() -> new ChatMessage.Cite("f", "../x", "")).hasMessageContaining("path");
        assertThatThrownBy(() -> new ChatMessage.Cite("", "", "")).hasMessageContaining("file");
        assertThatThrownBy(() -> new ChatMessage.From("robot", "", "")).hasMessageContaining("person or an agent");
        // twenty-one steps, a task past 120 characters, a state outside the six, a step kind outside the three
        List<ChatProgress.Step> twentyOne = Collections.nCopies(21, new ChatProgress.Step("read", ChatProgress.Step.READ, "", AT));
        assertThatThrownBy(() -> new ChatProgress(1, "p", "k", "m", "a", TaskStates.WORKING, twentyOne, AT)).hasMessageContaining("20");
        assertThatThrownBy(() -> new ChatProgress(1, "p", "k", "m", "a", "thinking", List.of(), AT)).hasMessageContaining("state");
        assertThatThrownBy(() -> new ChatProgress.Step("x", "think", "", AT)).hasMessageContaining("kind");
        assertThatThrownBy(() -> new WorkingOn(1, "w", "a", "k", List.of(), "t".repeat(121), TaskStates.WORKING, AT)).hasMessageContaining("120");
        assertThatThrownBy(() -> new WorkingOn(1, "w", "a", "k", List.of(), "", "busy", AT)).hasMessageContaining("state");
        assertThatThrownBy(() -> new WorkingOn(1, "w", "a", "k", List.of("../x"), "", TaskStates.WORKING, AT)).hasMessageContaining("path");
        assertThat(new WorkingOn(1, "w", "a", "k", List.of(), "t".repeat(120), TaskStates.WORKING, AT).task()).hasSize(120);
    }

    @Test
    void theStatesAreTheTaskStatesOfA2a() {
        assertThat(TaskStates.ALL).containsExactly("submitted", "working", "input-required", "completed", "failed", "canceled");
        assertThat(TaskStates.isFinal(TaskStates.COMPLETED)).isTrue();
        assertThat(TaskStates.isFinal(TaskStates.CANCELED)).isTrue();
        assertThat(TaskStates.isFinal(TaskStates.WORKING)).isFalse();
        assertThat(TaskStates.isFinal(TaskStates.INPUT_REQUIRED)).isFalse();
    }

    @Test
    void theLeasesAreAFewCadences() {
        // PR-1, the cadence rule: a lease of a few renewals, never a day
        assertThat(WorkingOn.LEASE).isEqualTo(Duration.ofSeconds(60));
        assertThat(WorkingOn.RENEW).isEqualTo(Duration.ofSeconds(20));
        assertThat(ChatProgress.LEASE).isEqualTo(Duration.ofSeconds(30));
        assertThat(ChatProgress.RENEW).isEqualTo(Duration.ofSeconds(10));
        assertThat(ChatProgress.idFor("m-1")).as("one entry per line, updated in place").isEqualTo(ChatProgress.idFor("m-1")).isEqualTo("progress-m-1");
        assertThat(WorkingOn.idFor("workbench", "main")).isEqualTo("working-workbench-main");
    }

    @Test
    void theOpenNamespaceNamesTheFiveRecords() {
        assertThat(A2uiRecords.NAMESPACE).isEqualTo("https://badmonkey.ai/agentspaces/a2ui/v1/");
        assertThat(ChatMessage.IRI).isEqualTo(A2uiRecords.NAMESPACE + "ChatMessage");
        assertThat(SharedFile.IRI).isEqualTo(A2uiRecords.NAMESPACE + "SharedFile");
        assertThat(WorkingOn.IRI).isEqualTo(A2uiRecords.NAMESPACE + "WorkingOn");
        assertThat(ChatProgress.IRI).isEqualTo(A2uiRecords.NAMESPACE + "ChatProgress");
        assertThat(List.of(ChatMessage.SPACE, SharedFile.SPACE, WorkingOn.SPACE, ChatProgress.SPACE)).containsExactly("conversations", "shared", "views", "views");
        assertThat(FunctionResponse.IRI).isEqualTo(A2uiRecords.NAMESPACE + "FunctionResponse");
        assertThat(FunctionResponse.SPACE).isEqualTo("views");
        assertThat(A2uiRecords.RECORDS).containsExactly(ChatMessage.class, SharedFile.class, WorkingOn.class, ChatProgress.class, FunctionResponse.class);
    }

    @Test
    void aWriterWritesVersionTwoOnlyWhenEveryReaderAdvertisesTheHint() {
        Map.Entry<String, String> hint = A2uiRecords.hint();
        assertThat(hint.getKey()).isEqualTo("a2ui.records");
        assertThat(hint.getValue()).isEqualTo("3");
        Map<String, String> v2 = Map.of("a2ui.records", "2", "wire.hashes", "1");
        Map<String, String> later = Map.of("a2ui.records", "3");
        Map<String, String> old = Map.of("wire.hashes", "1");
        assertThat(A2uiRecords.writesV2(List.of(v2, later))).isTrue();
        assertThat(A2uiRecords.writesV2(List.of(v2, old))).as("one older reader holds the group at v1").isFalse();
        assertThat(A2uiRecords.writesV2(List.of(Map.of("a2ui.records", "two")))).isFalse();
        assertThat(A2uiRecords.writesV2(List.of())).as("no reader meets the field").isTrue();
        assertThat(A2uiRecords.versionFor(List.of(v2, old))).isEqualTo(1);
        assertThat(A2uiRecords.versionFor(List.of(v2))).isEqualTo(2);
        // by level: the lowest any reader advertises, never above the type's newest
        assertThat(A2uiRecords.versionFor(List.of(later), A2uiRecords.latest("ChatMessage"))).isEqualTo(3);
        assertThat(A2uiRecords.versionFor(List.of(later, v2), A2uiRecords.latest(ChatMessage.IRI))).as("a reader at 2 holds the line at 2").isEqualTo(2);
        assertThat(A2uiRecords.versionFor(List.of(later), A2uiRecords.latest("SharedFile"))).as("SharedFile has no version 3").isEqualTo(2);
        assertThat(A2uiRecords.versionFor(List.of(later, old), 3)).isEqualTo(1);
        assertThat(A2uiRecords.versionFor(List.of(Map.of("a2ui.records", "9")), 3)).isEqualTo(3);
        assertThat(A2uiRecords.versionFor(List.of(), 3)).isEqualTo(3);
    }

    private static <T> void pin(String name, T value, Class<T> type) throws IOException {
        byte[] bytes = CODEC.toBytes(value);
        assertThat(CODEC.fromDocument(bytes, type)).as(name + " round-trips").isEqualTo(value);
        String hex = HexFormat.of().formatHex(bytes);
        String json = CODEC.toJson(value) + "\n";
        Path vector = Path.of("src/test/resources/vectors/" + name + "_cbor.hex");
        Path beside = Path.of("src/test/resources/vectors/" + name + ".json");
        if (!Files.exists(vector)) {
            Files.writeString(vector, hex + "\n", StandardCharsets.UTF_8);
            Files.writeString(beside, json, StandardCharsets.UTF_8);
        }
        assertThat(hex).as(name + " pins the canonical bytes").isEqualTo(Files.readString(vector, StandardCharsets.UTF_8).trim());
        assertThat(json).as(name + " has its JSON beside it").isEqualTo(Files.readString(beside, StandardCharsets.UTF_8));
    }
}
