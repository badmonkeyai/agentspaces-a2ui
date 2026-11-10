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

import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A surface agent's pen: writes A2UI v0.9.1 envelopes as {@code Surface} entries into the group's
 * {@code views} space, one per message in {@code seq} order per surface, and reads back the {@code SurfaceAction}
 * entries renderers send. Components are plain maps in the basic catalog's shape; a few builders cover the
 * common ones so an agent's surface reads as what it says.
 */
public final class Surfaces {

    public static final String VERSION = Surface.A2UI_VERSION;
    private final Space views;
    private final String app;
    private final InstantSource clock;
    /**
     * How long an update of one path of the data model stays in the space when the pen was given no other term: a
     * minute, since the feed keeps the newest per path for a page that joins and the space is replicated by every member.
     */
    public static final Duration DEFAULT_UPDATE_LEASE = Duration.ofMinutes(1);
    private final Duration lease;
    private final Duration updateLease;
    private final Map<String, Long> seq = new ConcurrentHashMap<>();
    private final Set<String> seen = new HashSet<>();

    public Surfaces(Space views, String app, InstantSource clock) {
        this(views, app, clock, Duration.ofDays(1));
    }

    public Surfaces(Space views, String app, InstantSource clock, Duration lease) {
        this(views, app, clock, lease, DEFAULT_UPDATE_LEASE);
    }

    /**
     * @param lease       how long a surface's creation, its components, its whole data model, and its publish stay
     * @param updateLease how long an update of one path of the data model stays: short, since a stream that updates a
     *                    path every second must not leave an entry for every second in a space every member replicates
     *                    (a map whose positions moved every second once drove three members to a full core each); a
     *                    renderer joining late takes the whole model and the updates still alive, the feed keeps the
     *                    newest per path
     */
    public Surfaces(Space views, String app, InstantSource clock, Duration lease, Duration updateLease) {
        this.views = views;
        this.app = app;
        this.clock = clock;
        this.lease = lease;
        this.updateLease = updateLease;
    }

    public String app() {
        return app;
    }

    /** {@code createSurface}: a new surface on a catalog (the basic catalog by default). */
    public Surfaces create(String surfaceId, String catalogId) {
        String catalog = catalogId == null || catalogId.isBlank() ? Surface.BASIC_CATALOG : catalogId;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("surfaceId", surfaceId);
        m.put("catalogId", catalog);
        return write(surfaceId, catalog, "createSurface", m);
    }

    /** {@code updateComponents}: the flat list; relationships are ids. */
    public Surfaces components(String surfaceId, List<Map<String, Object>> components) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("surfaceId", surfaceId);
        m.put("components", List.copyOf(components));
        return write(surfaceId, "", "updateComponents", m);
    }

    /** {@code updateDataModel}: a value at a JSON pointer ({@code /} replaces the model and stays as long as the surface; a path's update stays {@code updateLease}). */
    public Surfaces data(String surfaceId, String path, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        String p = path == null || path.isBlank() ? "/" : path;
        m.put("surfaceId", surfaceId);
        m.put("path", p);
        m.put("value", value);
        return write(surfaceId, "", "updateDataModel", m, "/".equals(p) ? lease : updateLease);
    }

    /** {@code deleteSurface}. */
    public Surfaces delete(String surfaceId) {
        return write(surfaceId, "", "deleteSurface", Map.of("surfaceId", surfaceId));
    }

    /**
     * Draws a surface from envelopes authored elsewhere (a template, the A2UI Composer's output): each envelope is
     * written in order under {@code surfaceId}, with the surface id inside it replaced by that one and this helper's
     * sequence numbers, so one authored surface serves any number of keys. An envelope with no catalog on its
     * {@code createSurface} gets the basic catalog.
     */
    @SuppressWarnings("unchecked")
    public Surfaces draw(String surfaceId, List<Map<String, Object>> envelopes) {
        for (Map<String, Object> envelope : envelopes) {
            for (String kind : List.of("createSurface", "updateComponents", "updateDataModel", "deleteSurface")) {
                if (envelope.get(kind) instanceof Map<?, ?> m) {
                    Map<String, Object> body = new LinkedHashMap<>((Map<String, Object>) m);
                    body.put("surfaceId", surfaceId);
                    String catalog = "";
                    if (kind.equals("createSurface")) {
                        Object c = body.get("catalogId");
                        catalog = c == null || String.valueOf(c).isBlank() ? Surface.BASIC_CATALOG : String.valueOf(c);
                        body.put("catalogId", catalog);
                    }
                    write(surfaceId, catalog, kind, body);
                    break;
                }
            }
        }
        return this;
    }

    /**
     * Asks the group's app host to serve this app's surfaces as an application for end users: every
     * surface when {@code surfaces} is empty, at the host named or the group's default, behind the identity provider of
     * whoever runs the host ({@code idp}, the default) unless {@code signOn} says {@code none}.
     */
    public Publish publish(List<String> surfaces, String title, String host, String signOn) {
        Publish p = new Publish(Publish.VERSION, app, "", surfaces, title, host, signOn, Map.of(), clock.instant());
        views.write(p, Lease.of(lease), Map.of("app", app, "kind", "publish"));
        return p;
    }

    private Surfaces write(String surfaceId, String catalogId, String kind, Map<String, Object> body) {
        return write(surfaceId, catalogId, kind, body, lease);
    }

    private Surfaces write(String surfaceId, String catalogId, String kind, Map<String, Object> body, Duration term) {
        long n = seq.merge(surfaceId, 1L, Long::sum);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("version", VERSION);
        message.put(kind, body);
        views.write(new Surface(Surface.VERSION, app, surfaceId, catalogId, n, message, VERSION, clock.instant()), Lease.of(term),
                Map.of("app", app, "surfaceId", surfaceId, "kind", "surface", "seq", Long.toString(n)));
        return this;
    }

    /** The actions renderers sent this app's surfaces that this pen has not handed out yet. */
    public List<SurfaceAction> actions() {
        List<SurfaceAction> out = new ArrayList<>();
        for (SurfaceAction a : views.readAll(Template.of(SurfaceAction.class), 500)) {
            if (app.equals(a.app()) && seen.add(a.surfaceId() + "|" + a.at() + "|" + a.action().getOrDefault("name", ""))) {
                out.add(a);
            }
        }
        return out;
    }

    // ---- component builders (the basic catalog)

    public static Map<String, Object> text(String id, String text, String variant) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("component", "Text");
        m.put("text", text);
        if (variant != null && !variant.isBlank()) {
            m.put("variant", variant);
        }
        return m;
    }

    /** A text bound to the data model at a JSON pointer. */
    public static Map<String, Object> bound(String id, String path, String variant) {
        Map<String, Object> m = text(id, "", variant);
        m.put("text", Map.of("path", path));
        return m;
    }

    public static Map<String, Object> column(String id, List<String> children) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("component", "Column");
        m.put("children", List.copyOf(children));
        return m;
    }

    public static Map<String, Object> row(String id, List<String> children) {
        Map<String, Object> m = column(id, children);
        m.put("component", "Row");
        return m;
    }

    public static Map<String, Object> card(String id, String child) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("component", "Card");
        m.put("child", child);
        return m;
    }

    /** A button whose press reaches the agent as an action with the context. */
    public static Map<String, Object> button(String id, String childText, String eventName, Map<String, Object> context, String variant) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("component", "Button");
        m.put("child", id + "-text");
        m.put("action", Map.of("event", Map.of("name", eventName, "context", context == null ? Map.of() : context)));
        if (variant != null && !variant.isBlank()) {
            m.put("variant", variant);
        }
        return m;
    }

    public static Map<String, Object> divider(String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("component", "Divider");
        return m;
    }

    // ---- component builders (the agentspaces-viz catalog)

    private static Map<String, Object> viz(String id, String component) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("component", component);
        return m;
    }

    /** A chart of one kind over series: {@code series} is a list of {@code {name, path}} maps, the paths into the data model. */
    public static Map<String, Object> chart(String id, String kind, String title, List<Map<String, Object>> series, String x, String unit) {
        Map<String, Object> m = viz(id, "Chart");
        m.put("kind", kind);
        if (title != null && !title.isBlank()) {
            m.put("title", title);
        }
        m.put("series", List.copyOf(series));
        if (x != null && !x.isBlank()) {
            m.put("x", x);
        }
        if (unit != null && !unit.isBlank()) {
            m.put("unit", unit);
        }
        return m;
    }

    public static Map<String, Object> dataTable(String id, List<Map<String, Object>> columns, String rows, String rowAction) {
        Map<String, Object> m = viz(id, "DataTable");
        m.put("columns", List.copyOf(columns));
        m.put("rows", rows);
        if (rowAction != null && !rowAction.isBlank()) {
            m.put("rowAction", rowAction);
        }
        return m;
    }

    public static Map<String, Object> map(String id, List<Map<String, Object>> layers, String markerAction) {
        return map(id, layers, markerAction, Map.of());
    }

    /**
     * A map of layers: each layer from {@link #layer}; {@code options} carries the rest of the
     * Map's properties ({@code center}, {@code zoom}, {@code fit}, {@code transition}, {@code follow}, {@code highlight},
     * {@code legend}, {@code table}, {@code alertAction}); a blank or null option is left out.
     */
    public static Map<String, Object> map(String id, List<Map<String, Object>> layers, String markerAction, Map<String, Object> options) {
        Map<String, Object> m = viz(id, "Map");
        m.put("layers", List.copyOf(layers));
        if (markerAction != null && !markerAction.isBlank()) {
            m.put("markerAction", markerAction);
        }
        options.forEach((k, v) -> {
            if (v != null && !(v instanceof String s && s.isBlank())) {
                m.put(k, v);
            }
        });
        return m;
    }

    /**
     * One layer of a Map: the kind ({@code icons}, {@code markers}, {@code tracks}, {@code areas}, {@code heat},
     * {@code geojson}, {@code alerts}), the path of its rows, the key field, and the label field; {@code more} adds
     * {@code icon}, {@code size}, {@code heading}, {@code timeField}, {@code trail}, {@code color}.
     */
    public static Map<String, Object> layer(String kind, String path, String key, String label, Map<String, Object> more) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("kind", kind);
        l.put("path", path);
        if (key != null && !key.isBlank()) {
            l.put("key", key);
        }
        if (label != null && !label.isBlank()) {
            l.put("label", label);
        }
        more.forEach((k, v) -> {
            if (v != null && !(v instanceof String s && s.isBlank())) {
                l.put(k, v);
            }
        });
        return l;
    }

    /** A panel with a header over one child: dragged by the header, minimized to its bar. */
    public static Map<String, Object> window(String id, String title, String child, String placement) {
        Map<String, Object> m = viz(id, "Window");
        m.put("title", title);
        m.put("child", child);
        if (placement != null && !placement.isBlank()) {
            m.put("placement", placement);
        }
        return m;
    }

    /** A status box: the status word with its icon, a title, the text or a path to it. */
    public static Map<String, Object> notice(String id, String status, String title, String text, String dismissAction) {
        Map<String, Object> m = viz(id, "Notice");
        if (status != null && !status.isBlank()) {
            m.put("status", status);
        }
        m.put("title", title);
        if (text != null && !text.isBlank()) {
            m.put("text", text);
        }
        if (dismissAction != null && !dismissAction.isBlank()) {
            m.put("dismissAction", dismissAction);
        }
        return m;
    }

    /** A row of actions: each {@code {label, action, primary?, context?}}. */
    public static Map<String, Object> toolbar(String id, String title, List<Map<String, Object>> actions) {
        Map<String, Object> m = viz(id, "Toolbar");
        if (title != null && !title.isBlank()) {
            m.put("title", title);
        }
        m.put("actions", List.copyOf(actions));
        return m;
    }

    /** Facts as pairs: the items ({@code {label, value}}, the value a literal or a path), or every field of the object at {@code path}. */
    public static Map<String, Object> keyValue(String id, String title, List<Map<String, Object>> items, String path) {
        Map<String, Object> m = viz(id, "KeyValue");
        if (title != null && !title.isBlank()) {
            m.put("title", title);
        }
        if (items != null && !items.isEmpty()) {
            m.put("items", List.copyOf(items));
        }
        if (path != null && !path.isBlank()) {
            m.put("path", path);
        }
        return m;
    }

    /** Prose: Markdown text as safe HTML, the text or a path to it (named Prose: the A2UI React binding keeps the Markdown element for itself). */
    public static Map<String, Object> prose(String id, String text) {
        Map<String, Object> m = viz(id, "Prose");
        m.put("text", text);
        return m;
    }

    /** A chat with an agent: the ChatMessage rows at {@code lines}, the thread {@code key}, the agent named. */
    public static Map<String, Object> conversation(String id, String lines, String key, String agent) {
        Map<String, Object> m = viz(id, "Conversation");
        m.put("lines", lines);
        m.put("key", key);
        if (agent != null && !agent.isBlank()) {
            m.put("agent", agent);
        }
        return m;
    }

    public static Map<String, Object> graph(String id, String nodes, String edges, String layout, String nodeAction) {
        Map<String, Object> m = viz(id, "Graph");
        m.put("nodes", nodes);
        m.put("edges", edges);
        m.put("layout", layout == null || layout.isBlank() ? "columns" : layout);
        if (nodeAction != null && !nodeAction.isBlank()) {
            m.put("nodeAction", nodeAction);
        }
        return m;
    }

    public static Map<String, Object> timeline(String id, String events, String lanes) {
        Map<String, Object> m = viz(id, "Timeline");
        m.put("events", events);
        if (lanes != null && !lanes.isBlank()) {
            m.put("lanes", lanes);
        }
        return m;
    }

    /** A report of sections: each {@code {heading, kind, text | component}}. */
    public static Map<String, Object> report(String id, String title, List<Map<String, Object>> sections) {
        Map<String, Object> m = viz(id, "Report");
        m.put("title", title);
        m.put("sections", List.copyOf(sections));
        return m;
    }

    public static Map<String, Object> notification(String id, String items, String ackAction, String routeAction) {
        Map<String, Object> m = viz(id, "Notification");
        m.put("items", items);
        m.put("ackAction", ackAction);
        if (routeAction != null && !routeAction.isBlank()) {
            m.put("routeAction", routeAction);
        }
        return m;
    }

    public static Map<String, Object> form(String id, String fragment, List<String> fields, String submitAction) {
        Map<String, Object> m = viz(id, "Form");
        m.put("fragment", fragment);
        if (fields != null && !fields.isEmpty()) {
            m.put("fields", List.copyOf(fields));
        }
        m.put("submitAction", submitAction);
        return m;
    }

    public static Map<String, Object> stat(String id, String label, String value, String unit, String delta, String status) {
        Map<String, Object> m = viz(id, "Stat");
        m.put("label", label);
        m.put("value", value);
        if (unit != null && !unit.isBlank()) {
            m.put("unit", unit);
        }
        if (delta != null && !delta.isBlank()) {
            m.put("delta", delta);
        }
        if (status != null && !status.isBlank()) {
            m.put("status", status);
        }
        return m;
    }
}
