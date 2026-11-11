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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The two {@code Publish.options} keys of spiral 6 (S6-U-04, S6-U-05; SRAD §3.6): pages in panes, and pages a person moves between. */
class PublishOptionsTest {

    @Test
    void navigationDrawsTheSurfacesAsPages() {
        Map<String, Object> nav = Publish.navigation("tabs", List.of(Publish.navPage("claims", "Claims", "list"), Publish.navPage("chat", "Ask", "")));
        assertThat(nav).isEqualTo(Map.of("kind", "tabs", "pages", List.of(Map.of("page", "claims", "title", "Claims", "icon", "list"), Map.of("page", "chat", "title", "Ask"))));
        assertThat(Publish.navigation("rail", List.of(Publish.navPage("a", "A", ""))).get("kind")).isEqualTo("rail");
        assertThatThrownBy(() -> Publish.navigation("menu", List.of())).hasMessageContaining("tabs or rail");
        assertThatThrownBy(() -> Publish.navPage("", "A", "")).hasMessageContaining("page");
    }

    @Test
    void layoutPlacesWholeSurfacesInPanes() {
        Map<String, Object> layout = Publish.layout(List.of(Publish.layoutPane("claims", 0.7, 400, false), Publish.layoutPane("chat", 0.3, 280, true)));
        assertThat(layout).isEqualTo(Map.of("kind", "split", "panes", List.of(Map.of("page", "claims", "size", 0.7, "min", 400),
                Map.of("page", "chat", "size", 0.3, "min", 280, "collapsible", true))));
        assertThatThrownBy(() -> Publish.layout(List.of(Publish.layoutPane("a", 1, 0, false)))).hasMessageContaining("two or three");
    }

    @Test
    void aPublishCarriesThemInItsOptions() {
        Map<String, Object> options = Map.of(Publish.NAVIGATION, Publish.navigation("tabs", List.of(Publish.navPage("claims", "Claims", ""))));
        Publish p = new Publish(Publish.VERSION, "desk", "", List.of("claims"), "Desk", "", "idp", options, Instant.EPOCH);
        assertThat(p.options()).containsKey("navigation");
        assertThat(Publish.LAYOUT).isEqualTo("layout");
    }
}
