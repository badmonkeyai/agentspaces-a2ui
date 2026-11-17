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
    void aConversationPutsTheChatLeftAndTheAreasRight() {
        // the standard page of the advanced apps (2026-11-11): a top bar with the areas, the chat on the left growing down
        // the page, the current area's page on the right
        Map<String, Object> layout = Publish.conversation("chat", 0.4, List.of(Publish.area("documents", "Documents", "list"), Publish.area("answer", "How I found it", "")));
        assertThat(layout).isEqualTo(Map.of("kind", "conversation", "chat", "chat", "size", 0.4,
                "areas", List.of(Map.of("page", "documents", "label", "Documents", "icon", "list"), Map.of("page", "answer", "label", "How I found it"))));
        assertThat(Publish.conversation("chat", 0, List.of(Publish.area("a", "A", ""))).get("size")).isEqualTo(0.4);
        assertThatThrownBy(() -> Publish.conversation("", 0.4, List.of(Publish.area("a", "A", "")))).hasMessageContaining("chat");
        assertThatThrownBy(() -> Publish.conversation("chat", 0.4, List.of())).hasMessageContaining("area");
        assertThatThrownBy(() -> Publish.conversation("chat", 0.4, List.of(Publish.area("chat", "Chat", "")))).hasMessageContaining("the chat's page");
        assertThatThrownBy(() -> Publish.conversation("chat", 0.9, List.of(Publish.area("a", "A", "")))).hasMessageContaining("0.2 to 0.6");
        assertThatThrownBy(() -> Publish.area("a", "", "")).hasMessageContaining("label");
    }

    @Test
    void aGeoLayoutPutsTheMapUnderTheBarAndTheWindowsOverIt() {
        // srad/agentspaces-geo.md §3.3, GEO-L-01: the map's page full screen, each window a page opened from the bar
        Map<String, Object> layout = Publish.geo("watch", List.of(Publish.geoWindow("ask", "Ask", "chat", true, "left"),
                Publish.geoWindow("incidents", "Incidents", "", true, "right"), Publish.geoWindow("spend", "Spend by region", "chart", false, "")));
        assertThat(layout).isEqualTo(Map.of("kind", "geo", "map", "watch", "windows", List.of(
                Map.of("page", "ask", "label", "Ask", "icon", "chat", "open", true, "dock", "left"),
                Map.of("page", "incidents", "label", "Incidents", "open", true, "dock", "right"),
                Map.of("page", "spend", "label", "Spend by region", "icon", "chart", "open", false, "dock", "right"))));
        assertThat(Publish.geo("watch", List.of())).containsEntry("windows", List.of());
        assertThatThrownBy(() -> Publish.geo("", List.of())).hasMessageContaining("map's page");
        assertThatThrownBy(() -> Publish.geo("watch", List.of(Publish.geoWindow("watch", "Map", "", true, "left")))).hasMessageContaining("not the map's page");
        assertThatThrownBy(() -> Publish.geo("watch", List.of(Publish.geoWindow("a", "A", "", true, "left"), Publish.geoWindow("a", "B", "", true, "right"))))
                .hasMessageContaining("one window");
        assertThatThrownBy(() -> Publish.geoWindow("a", "A", "", true, "top")).hasMessageContaining("left, right, bottom or free");
        assertThatThrownBy(() -> Publish.geoWindow("a", "", "", true, "left")).hasMessageContaining("label");
        assertThat(Publish.DOCKS).containsExactly("left", "right", "bottom", "free");
    }

    @Test
    void aKickerRidesInTheLayoutMapAboveTheTitle() {
        // an optional line above the app's title in the bar: inside options.layout, so no entry carries a new field
        List<Map<String, Object>> areas = List.of(Publish.area("a", "A", ""));
        assertThat(Publish.conversation("chat", 0.4, areas, "  Operations  ")).isEqualTo(Map.of("kind", "conversation", "chat", "chat", "size", 0.4,
                "areas", List.of(Map.of("page", "a", "label", "A")), Publish.KICKER, "Operations"));
        assertThat(Publish.conversation("chat", 0.4, areas, " ")).isEqualTo(Publish.conversation("chat", 0.4, areas)).doesNotContainKey(Publish.KICKER);
        assertThat(Publish.conversation("chat", 0.4, areas, null)).doesNotContainKey(Publish.KICKER);
        assertThat(Publish.geo("watch", List.of(), "Harbour")).isEqualTo(Map.of("kind", "geo", "map", "watch", "windows", List.of(), Publish.KICKER, "Harbour"));
        assertThat(Publish.geo("watch", List.of(), "")).isEqualTo(Publish.geo("watch", List.of())).doesNotContainKey(Publish.KICKER);
        assertThatThrownBy(() -> Publish.geo("", List.of(), "Harbour")).hasMessageContaining("map's page");
    }

    @Test
    void aBannerRidesInTheConversationLayoutAtTheTopAndTheFoot() {
        // AF-18: one optional line the host draws at the page's top and foot; inside options.layout, like the kicker
        List<Map<String, Object>> areas = List.of(Publish.area("a", "A", ""));
        Map<String, Object> layout = Publish.conversation("chat", 0.36, areas, "Desk", "  Internal use only  ");
        assertThat(layout).isEqualTo(Map.of("kind", "conversation", "chat", "chat", "size", 0.36, "areas", List.of(Map.of("page", "a", "label", "A")),
                Publish.KICKER, "Desk", Publish.BANNER, "Internal use only"));
        assertThat(Publish.conversation("chat", 0.4, areas, "", "Internal use only")).containsEntry(Publish.BANNER, "Internal use only").doesNotContainKey(Publish.KICKER);
        // empty or absent draws nothing, so the layout map is the one without it
        for (String none : java.util.Arrays.asList("", "   ", null)) {
            assertThat(Publish.conversation("chat", 0.4, areas, "Desk", none)).as("banner " + none).isEqualTo(Publish.conversation("chat", 0.4, areas, "Desk"))
                    .doesNotContainKey(Publish.BANNER);
        }
        assertThat(Publish.BANNER).isEqualTo("banner");
        String longest = "x".repeat(Publish.BANNER_MAX);
        assertThat(Publish.conversation("chat", 0.4, areas, "", longest)).containsEntry(Publish.BANNER, longest);
        assertThatThrownBy(() -> Publish.conversation("chat", 0.4, areas, "", longest + "y")).hasMessageContaining("at most 120");
        assertThatThrownBy(() -> Publish.conversation("chat", 0.4, areas, "", "Line one\nLine two")).hasMessageContaining("one line");
        // the other checks still come first
        assertThatThrownBy(() -> Publish.conversation("", 0.4, areas, "", "Internal use only")).hasMessageContaining("chat");
    }

    @Test
    void aPublishCarriesThemInItsOptions() {
        Map<String, Object> options = Map.of(Publish.NAVIGATION, Publish.navigation("tabs", List.of(Publish.navPage("claims", "Claims", ""))));
        Publish p = new Publish(Publish.VERSION, "desk", "", List.of("claims"), "Desk", "", "idp", options, Instant.EPOCH);
        assertThat(p.options()).containsKey("navigation");
        assertThat(Publish.LAYOUT).isEqualTo("layout");
    }
}
