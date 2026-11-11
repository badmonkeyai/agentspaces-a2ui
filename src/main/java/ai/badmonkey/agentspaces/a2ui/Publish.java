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
 * in the panes of a split ({@link #layout}). A Publish without them draws its surfaces stacked. Version 1.
 */
public record Publish(int schemaVersion, String app, String from, List<String> surfaces, String title, String host, String signOn,
                      Map<String, Object> options, Instant at) {

    public static final int VERSION = 1;
    public static final List<String> SIGN_ONS = List.of("idp", "none");

    public static final String NAVIGATION = "navigation";
    public static final String LAYOUT = "layout";

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
}
