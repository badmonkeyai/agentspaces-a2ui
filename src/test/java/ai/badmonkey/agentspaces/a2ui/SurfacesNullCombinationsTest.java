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

import ai.badmonkey.agentspaces.api.space.Lease;
import ai.badmonkey.agentspaces.api.space.Space;
import ai.badmonkey.agentspaces.api.space.Template;
import ai.badmonkey.agentspaces.identity.PeerIdentity;
import ai.badmonkey.agentspaces.space.local.LocalSpace;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pen's action reader: a surface or an action without its app and surface id is refused where it is built, and
 * every combination of an action's optional components (the session, the action map, the instant) is handed out by
 * {@link Surfaces#actions()} once, without an exception.
 */
class SurfacesNullCombinationsTest {

    private static final Instant FIXED = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    void aSurfaceOrAnActionWithoutItsIdentityIsRefusedWhereItIsBuilt() {
        assertThatThrownBy(() -> new Surface(Surface.VERSION, null, "s-1", "", 1, Map.of(), null, FIXED)).isInstanceOf(NullPointerException.class).hasMessageContaining("app");
        assertThatThrownBy(() -> new Surface(Surface.VERSION, "app-desk", null, "", 1, Map.of(), null, FIXED)).isInstanceOf(NullPointerException.class).hasMessageContaining("surfaceId");
        assertThatThrownBy(() -> new SurfaceAction(SurfaceAction.VERSION, null, "s-1", "", Map.of(), FIXED)).isInstanceOf(NullPointerException.class).hasMessageContaining("app");
        assertThatThrownBy(() -> new SurfaceAction(SurfaceAction.VERSION, "app-desk", null, "", Map.of(), FIXED)).isInstanceOf(NullPointerException.class).hasMessageContaining("surfaceId");
    }

    @Test
    void everyCombinationOfAnActionsOptionalComponentsIsHandedOutOnce() {
        Space views = LocalSpace.builder("views", PeerIdentity.generate().agent("test")).build();
        int n = 0;
        for (String session : Arrays.asList("s-1", null)) {
            for (Map<String, Object> action : Arrays.<Map<String, Object>>asList(Map.of("name", "tap"), null)) {
                for (Instant at : Arrays.asList(FIXED, null)) {
                    views.write(new SurfaceAction(SurfaceAction.VERSION, "app-desk", "s-" + n++, session, action, at), Lease.of(Duration.ofMinutes(5)), Map.of("kind", "surface-action"));
                }
            }
        }
        views.write(new SurfaceAction(SurfaceAction.VERSION, "app-other", "s-x", null, null, null), Lease.of(Duration.ofMinutes(5)), Map.of("kind", "surface-action"));
        assertThat(views.readAll(Template.of(SurfaceAction.class), 20)).hasSize(n + 1);
        Surfaces pen = new Surfaces(views, "app-desk", InstantSource.fixed(FIXED));
        List<SurfaceAction> actions = pen.actions();
        assertThat(actions).as("this app's actions, each once").hasSize(n);
        assertThat(actions).allSatisfy(a -> {
            assertThat(a.session()).isNotNull();
            assertThat(a.action()).isNotNull();
            assertThat(a.at()).isNotNull();
        });
        assertThat(actions.stream().filter(a -> a.action().isEmpty()).count()).as("a left-out action map is empty").isEqualTo(4);
        assertThat(actions.stream().filter(a -> a.at().equals(FIXED)).count()).as("the others got the clock").isEqualTo(4);
        assertThat(pen.actions()).as("handed out once").isEmpty();
    }
}
