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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An agent's request that its surfaces be served as an application for end users: written into the group's
 * {@code views} space beside the surfaces; an app host in the group takes it, serves the page at an address, and sets
 * the application card's host. {@code surfaces} names the surface ids to serve, or is empty for every surface of the
 * app; {@code host} names a host by name or url, or is empty for the group's default; {@code signOn} is {@code idp}
 * (the identity provider of whoever runs the host; the default) or {@code none} (SPEC §6.1a.4), and a host serves an
 * app without a session only for {@code none}. The newest Publish per app is the one in force. {@code options} carries
 * what a host may read beyond the record: {@code unpublish: true} takes the page down (the host drops the route and
 * forgets the sign-on); {@code panels} and {@code arrangement} (an application drafted from declared panels: the
 * panels in the order their author chose, for a host to synthesize the surface); {@code assembled: true} marks such a
 * Publish. Two more keys lay out a page: {@link #NAVIGATION} draws the surfaces as pages a person moves between,
 * as tabs or a rail, the current page in the URL fragment ({@link #navigation}); {@link #LAYOUT} places whole surfaces
 * in the panes of a split ({@link #layout}), or lays the page out as a conversation ({@link #conversation}): a top bar
 * with the app's areas, a chat on the left that grows down the page, the current area's surface on the right, and an
 * optional {@link #BANNER} line at the page's top and foot. A Publish without them draws its surfaces stacked. Version 1.
 */
public record Publish(int schemaVersion, String app, String from, List<String> surfaces, String title, String host, String signOn,
                      Map<String, Object> options, Instant at) {

    public static final int VERSION = 1;
    public static final List<String> SIGN_ONS = List.of("idp", "none");

    public static final String NAVIGATION = "navigation";
    public static final String LAYOUT = "layout";
    /**
     * In a {@link #conversation} or {@link #geo} layout map: a short line a host draws above the app's title in the top
     * bar (a section, a place, a mode). Optional; it rides inside {@code options.layout}, so no entry carries a new field.
     */
    public static final String KICKER = "kicker";
    /**
     * In a {@link #conversation} layout map: one line of plain text a host draws centred at the very top of the page,
     * above the bar, and again at its very foot (a marking a page must carry, a notice every reader must see). Optional;
     * like the {@link #KICKER} it rides inside {@code options.layout}, and the field names no marking scheme.
     */
    public static final String BANNER = "banner";
    /** The longest {@link #BANNER}, in characters: one line on a narrow page. */
    public static final int BANNER_MAX = 120;

    public Publish {
        Objects.requireNonNull(app, "app");
        if (app.isBlank()) {
            throw new IllegalArgumentException("a publish names its app");
        }
        from = from == null ? "" : from;
        surfaces = surfaces == null ? List.of() : List.copyOf(surfaces);
        title = title == null || title.isBlank() ? app : title;
        host = host == null ? "" : host;
        signOn = signOn == null || signOn.isBlank() ? "idp" : signOn;
        if (!SIGN_ONS.contains(signOn)) {
            throw new IllegalArgumentException("signOn must be one of " + SIGN_ONS + ": " + signOn);
        }
        options = options == null ? Map.of() : Map.copyOf(options);
        Objects.requireNonNull(at, "at");
    }

    /** {@code options.navigation}: {@code tabs} or {@code rail} over pages from {@link #navPage}. */
    public static Map<String, Object> navigation(String kind, List<Map<String, Object>> pages) {
        if (!"tabs".equals(kind) && !"rail".equals(kind)) {
            throw new IllegalArgumentException("navigation is tabs or rail: " + kind);
        }
        return Map.of("kind", kind, "pages", List.copyOf(pages));
    }

    /** One page of a navigation: the surface id, its title, and an icon name (blank for none). */
    public static Map<String, Object> navPage(String page, String title, String icon) {
        if (page == null || page.isBlank()) {
            throw new IllegalArgumentException("a navigation page names its surface");
        }
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("page", page);
        m.put("title", title == null || title.isBlank() ? page : title);
        if (icon != null && !icon.isBlank()) {
            m.put("icon", icon);
        }
        return Map.copyOf(m);
    }

    /** {@code options.layout}: a split of two or three panes from {@link #layoutPane}, each holding a whole surface. */
    public static Map<String, Object> layout(List<Map<String, Object>> panes) {
        if (panes.size() < 2 || panes.size() > 3) {
            throw new IllegalArgumentException("a layout has two or three panes: " + panes.size());
        }
        return Map.of("kind", "split", "panes", List.copyOf(panes));
    }

    /** One pane of a layout: the surface id, its share, its minimum in pixels (0 for none), and whether it collapses. */
    public static Map<String, Object> layoutPane(String page, double size, int min, boolean collapsible) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("page", page);
        m.put("size", size);
        if (min > 0) {
            m.put("min", min);
        }
        if (collapsible) {
            m.put("collapsible", true);
        }
        return Map.copyOf(m);
    }

    /**
     * {@code options.layout} as a conversation (the standard page of the advanced apps, 2026-11-11): the surface
     * {@code chat} in the left pane, {@code size} of the width (0.2 to 0.6; 0 for 0.4), growing down the page as a person
     * chats; the areas from {@link #area} as menu items in the top bar, each a whole surface drawn in the right pane, which
     * stays in view. The chat stays the same across areas.
     */
    public static Map<String, Object> conversation(String chat, double size, List<Map<String, Object>> areas) {
        return conversation(chat, size, areas, "");
    }

    /** A {@link #conversation} with a {@link #KICKER} above the title in its bar (blank for none). */
    public static Map<String, Object> conversation(String chat, double size, List<Map<String, Object>> areas, String kicker) {
        return conversation(chat, size, areas, kicker, "");
    }

    /**
     * A {@link #conversation} with a {@link #KICKER} and a {@link #BANNER} (each blank for none): the banner's trimmed
     * words, one line of at most {@link #BANNER_MAX} characters, which the host draws at the top and the foot of the
     * page. A line break or a longer line is refused here, since a host would draw it as one cut line.
     */
    public static Map<String, Object> conversation(String chat, double size, List<Map<String, Object>> areas, String kicker, String banner) {
        if (chat == null || chat.isBlank()) {
            throw new IllegalArgumentException("a conversation names the chat's page");
        }
        if (areas.isEmpty()) {
            throw new IllegalArgumentException("a conversation has at least one area");
        }
        if (areas.stream().anyMatch(a -> chat.equals(a.get("page")))) {
            throw new IllegalArgumentException("an area is not the chat's page: " + chat);
        }
        double s = size == 0 ? 0.4 : size;
        if (s < 0.2 || s > 0.6) {
            throw new IllegalArgumentException("a conversation's chat takes 0.2 to 0.6 of the width: " + size);
        }
        return withBanner(withKicker(Map.of("kind", "conversation", "chat", chat, "size", s, "areas", List.copyOf(areas)), kicker), banner);
    }

    /** A layout map as it is without a banner, else with the banner's trimmed words under {@link #BANNER}, checked. */
    private static Map<String, Object> withBanner(Map<String, Object> layout, String banner) {
        if (banner == null || banner.isBlank()) {
            return layout;
        }
        String words = banner.strip();
        if (words.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("a banner is one line of text, with no line break or control character: " + words.replaceAll("\\p{Cntrl}", " "));
        }
        if (words.codePointCount(0, words.length()) > BANNER_MAX) {
            throw new IllegalArgumentException("a banner is at most " + BANNER_MAX + " characters: " + words.codePointCount(0, words.length()));
        }
        Map<String, Object> m = new LinkedHashMap<>(layout);
        m.put(BANNER, words);
        return Map.copyOf(m);
    }

    /** Where a GeoLayout window opens: against a side (two on one side stack), or free over the map. */
    public static final List<String> DOCKS = List.of("left", "right", "bottom", "free");

    /**
     * {@code options.layout} as a GeoLayout (srad/agentspaces-geo.md §3.3, GEO-L-01): the surface {@code map}, which holds
     * the app's GeoMap, full screen under a top bar, and each window from {@link #geoWindow} a card over it, opened and
     * closed from the bar's menu. The map's search draws in the bar, and the map opens its selection's details as a window
     * of its own, so neither is named here. Every window names a surface other than the map's, once.
     */
    public static Map<String, Object> geo(String map, List<Map<String, Object>> windows) {
        return geo(map, windows, "");
    }

    /** A {@link #geo} layout with a {@link #KICKER} above the title in its bar (blank for none). */
    public static Map<String, Object> geo(String map, List<Map<String, Object>> windows, String kicker) {
        if (map == null || map.isBlank()) {
            throw new IllegalArgumentException("a GeoLayout names the map's page");
        }
        java.util.Set<Object> seen = new java.util.HashSet<>();
        for (Map<String, Object> w : windows) {
            if (map.equals(w.get("page"))) {
                throw new IllegalArgumentException("a window is not the map's page: " + map);
            }
            if (!seen.add(w.get("page"))) {
                throw new IllegalArgumentException("a page opens in one window: " + w.get("page"));
            }
        }
        return withKicker(Map.of("kind", "geo", "map", map, "windows", List.copyOf(windows)), kicker);
    }

    /** A layout map as it is without a kicker, else with the kicker's trimmed words under {@link #KICKER}. */
    private static Map<String, Object> withKicker(Map<String, Object> layout, String kicker) {
        if (kicker == null || kicker.isBlank()) {
            return layout;
        }
        Map<String, Object> m = new LinkedHashMap<>(layout);
        m.put(KICKER, kicker.strip());
        return Map.copyOf(m);
    }

    /**
     * One window of a {@link #geo} layout: its surface, the words of its menu item, an icon name (empty for none), whether
     * it is open when a person first comes, and where it opens ({@link #DOCKS}; empty for right).
     */
    public static Map<String, Object> geoWindow(String page, String label, String icon, boolean open, String dock) {
        if (page == null || page.isBlank()) {
            throw new IllegalArgumentException("a window names its page");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("a window has a label: " + page);
        }
        String d = dock == null || dock.isBlank() ? "right" : dock;
        if (!DOCKS.contains(d)) {
            throw new IllegalArgumentException("a window docks left, right, bottom or free: " + dock);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page", page);
        m.put("label", label);
        if (icon != null && !icon.isBlank()) {
            m.put("icon", icon);
        }
        m.put("open", open);
        m.put("dock", d);
        return Map.copyOf(m);
    }

    /** One area of a {@link #conversation}: its surface, the words of its menu item, and an icon name (empty for none). */
    public static Map<String, Object> area(String page, String label, String icon) {
        if (page == null || page.isBlank()) {
            throw new IllegalArgumentException("an area names its page");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("an area has a label: " + page);
        }
        return icon == null || icon.isBlank() ? Map.of("page", page, "label", label) : Map.of("page", page, "label", label, "icon", icon);
    }
}
