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

import ai.badmonkey.agentspaces.api.ad.GroupAdvertisement;
import ai.badmonkey.agentspaces.api.space.ConflictStrategyType;
import ai.badmonkey.agentspaces.api.space.Lease;
import ai.badmonkey.agentspaces.api.space.Template;
import ai.badmonkey.agentspaces.common.id.GroupId;
import ai.badmonkey.agentspaces.identity.PeerIdentity;
import ai.badmonkey.agentspaces.peering.membership.GroupMembership;
import ai.badmonkey.agentspaces.peering.node.GroupRuntime;
import ai.badmonkey.agentspaces.peering.node.PeerNode;
import ai.badmonkey.agentspaces.peering.transport.TcpTransport;
import ai.badmonkey.agentspaces.space.replicated.ReplicatedSpace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The example: an agent draws a surface into a plain group and a renderer answers it, with nothing but this module
 * and the runtime. Two peers over TCP share a {@code views} space; the desk peer draws one card per decision with a
 * {@link Surfaces} pen, and the renderer peer reads the envelopes back in order, then presses the card's button, which
 * arrives at the desk as a {@link SurfaceAction}. Any A2UI renderer that joins the group draws the same envelopes.
 */
class SurfaceDeskExampleTest {

    @Test
    @Timeout(90)
    void aDeskDrawsASurfaceAndARendererAnswersIt() throws Exception {
        String founding = "surface-desk-example-v1";
        GroupId groupId = GroupId.fromFounding(founding.getBytes(StandardCharsets.UTF_8));
        GroupAdvertisement ad = new GroupAdvertisement("aspace://" + groupId.value(), PeerIdentity.generate().peerId(), groupId,
                Instant.EPOCH, Duration.ofDays(365), "desk", GroupAdvertisement.MembershipPolicy.OPEN, ConflictStrategyType.LEASE_RACE,
                GroupAdvertisement.GossipParameters.defaults());
        PeerIdentity deskId = PeerIdentity.generate();
        PeerIdentity rendererId = PeerIdentity.generate();
        PeerNode desk = PeerNode.builder(deskId).build();
        PeerNode renderer = PeerNode.builder(rendererId).build();
        int deskPort = freePort();
        desk.listen(new TcpTransport(), "127.0.0.1:" + deskPort);
        renderer.listen(new TcpTransport(), "127.0.0.1:" + freePort());
        GroupRuntime deskRuntime = desk.joinGroup(ad, GroupMembership.Config.defaults(), List.of());
        GroupRuntime rendererRuntime = renderer.joinGroup(ad, GroupMembership.Config.defaults(),
                List.of(new ai.badmonkey.agentspaces.api.ad.PeerAdvertisement.Endpoint("tcp", "127.0.0.1:" + deskPort, 0)));
        try (ReplicatedSpace deskViews = ReplicatedSpace.builder(deskRuntime, "views", deskId, "desk").settleWindow(Duration.ofMillis(100)).build();
             ReplicatedSpace rendererViews = ReplicatedSpace.builder(rendererRuntime, "views", rendererId, "renderer").settleWindow(Duration.ofMillis(100)).build()) {
            desk.startTicking(Duration.ofMillis(100));
            renderer.startTicking(Duration.ofMillis(100));

            // The desk draws: one card for decision 77, its verdict bound to the data model, and an Acknowledge button.
            Surfaces pen = new Surfaces(deskViews, "desk", InstantSource.system());
            pen.create("decision-77", null)
               .data("decision-77", "/", Map.of("id", "77", "verdict", "approve"))
               .components("decision-77", List.of(
                       Surfaces.card("root", "col"),
                       Surfaces.column("col", List.of("title", "verdict", "ack")),
                       Surfaces.text("title", "Decision 77", "h3"),
                       Surfaces.bound("verdict", "/verdict", "body"),
                       Surfaces.button("ack", "Acknowledge", "acknowledge", Map.of("decision", "77"), "primary")));

            // The renderer reads the three envelopes back in order, as any A2UI renderer would.
            await(() -> rendererViews.readAll(Template.of(Surface.class), 10).size() >= 3, Duration.ofSeconds(30),
                    () -> "the renderer sees " + rendererViews.readAll(Template.of(Surface.class), 10).size() + " envelope(s)");
            List<Surface> seen = rendererViews.readAll(Template.of(Surface.class), 10).stream().sorted((a, b) -> Long.compare(a.seq(), b.seq())).toList();
            assertThat(seen).extracting(Surface::kind).containsExactly("createSurface", "updateDataModel", "updateComponents");
            assertThat(seen.get(0).catalogId()).contains(Surface.BASIC_CATALOG);
            assertThat(seen.get(2).message().get("updateComponents")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .extractingByKey("components").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(5);

            // The renderer presses the button: a SurfaceAction the desk reads from its pen.
            rendererViews.write(new SurfaceAction(SurfaceAction.VERSION, "desk", "decision-77", "session-1",
                    Map.of("name", "acknowledge", "surfaceId", "decision-77", "sourceComponentId", "ack", "timestamp", Instant.now().toString(),
                            "context", Map.of("decision", "77")), Instant.now()), Lease.of(Duration.ofMinutes(5)));
            await(() -> !pen.actions().isEmpty(), Duration.ofSeconds(30), () -> "no action reached the desk");
            // actions() hands each action over once: the earlier call consumed it
            List<SurfaceAction> actions = deskViews.readAll(Template.of(SurfaceAction.class), 10);
            assertThat(actions).hasSize(1);
            assertThat(actions.get(0).action()).containsEntry("name", "acknowledge");
            assertThat(actions.get(0).session()).contains("session-1");
            assertThat(pen.actions()).as("an action is handed over once").isEmpty();

            // A template drawn under another id: the same authored envelopes serve decision 78.
            pen.draw("decision-78", seen.stream().map(Surface::message).toList());
            await(() -> rendererViews.readAll(Template.of(Surface.class), 20).stream().filter(x -> x.surfaceId().equals("decision-78")).count() >= 3,
                    Duration.ofSeconds(30), () -> "decision-78 not drawn");
            assertThat(rendererViews.readAll(Template.of(Surface.class), 20).stream().filter(x -> x.surfaceId().equals("decision-78")).map(Surface::kind))
                    .containsExactlyInAnyOrder("createSurface", "updateDataModel", "updateComponents");
        } finally {
            renderer.close();
            desk.close();
        }
    }

    static void await(BooleanSupplier check, Duration timeout, java.util.function.Supplier<String> diagnostics) throws InterruptedException {
        long until = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < until) {
            if (check.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(check.getAsBoolean()).as(diagnostics.get()).isTrue();
    }

    static int freePort() throws java.io.IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
