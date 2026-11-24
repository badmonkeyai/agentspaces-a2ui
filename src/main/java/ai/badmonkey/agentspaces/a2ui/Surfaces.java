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
import com.fasterxml.jackson.databind.JsonNode;

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
 * A surface agent's pen: writes A2UI 1.0 envelopes as {@code Surface} entries into the group's
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
     * minute, since a host keeps the newest per path for a page that joins and the space is replicated by every member.
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
     *                    renderer joining late takes the whole model and the updates still alive, and a host keeps the
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
        for (Map<String, Object> c : components) {
            componentId(c.get("id"));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("surfaceId", surfaceId);
        m.put("components", List.copyOf(components));
        return write(surfaceId, "", "updateComponents", m);
    }

    /** A2UI 1.0's component id: a Unicode identifier (UAX #31), so {@code barMenu} and {@code bar_menu}, never {@code bar-menu}. */
    private static final java.util.regex.Pattern COMPONENT_ID = java.util.regex.Pattern.compile("[\\p{IsAlphabetic}_][\\p{IsAlphabetic}\\p{IsDigit}_]*");

    /** Refuses a component id A2UI 1.0 refuses, naming it and a form it takes (2026-11-12); returns the id as text. */
    public static String componentId(Object id) {
        String s = id == null ? "" : String.valueOf(id);
        if (!COMPONENT_ID.matcher(s).matches()) {
            throw new IllegalArgumentException("component id '" + s + "' is not an A2UI 1.0 identifier (UAX #31): use letters, digits and _ only, such as "
                    + java.util.Arrays.stream(s.split("[^\\p{IsAlphabetic}\\p{IsDigit}_]+")).filter(w -> !w.isEmpty())
                    .reduce((a, b) -> a + Character.toUpperCase(b.charAt(0)) + b.substring(1)).orElse("root"));
        }
        return s;
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
            // a call's id tells two calls of one function apart even when the host stamped both with the same instant
            if (app.equals(a.app()) && seen.add(a.surfaceId() + "|" + a.at() + "|" + a.action().getOrDefault("name", "") + "|" + a.functionCallId())) {
                out.add(a);
            }
        }
        return out;
    }

    /**
     * Answers a page's call to one of this agent's functions with the function's value (JSON, or null for a function
     * that returns nothing): a {@link FunctionResponse} in {@code views}, which the host delivers to the calling session's
     * page alone. Refuses an action that calls no function.
     */
    public FunctionResponse answer(SurfaceAction call, Object value) {
        return respond(FunctionResponse.answering(call, value, clock.instant()));
    }

    /** Answers a page's call with a failure: a code the page can branch on and a sentence a person reads. */
    public FunctionResponse fail(SurfaceAction call, String code, String message) {
        return respond(FunctionResponse.failing(call, code, message, clock.instant()));
    }

    private FunctionResponse respond(FunctionResponse r) {
        views.write(r, Lease.of(FunctionResponse.LEASE), Map.of("app", r.app(), "surfaceId", r.surfaceId(), "kind", "function-response"));
        return r;
    }

    // ---- component builders (the basic catalog)

    public static Map<String, Object> text(String id, String text, String variant) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("component", "Text");
        // A2UI 1.0's Text takes the variants caption and body alone; a heading (h1 to h5) is the Markdown its text draws
        int level = headingLevel(variant);
        m.put("text", level > 0 && text != null && !text.isBlank() ? "#".repeat(level) + " " + text : text);
        if (variant != null && !variant.isBlank() && level == 0) {
            m.put("variant", variant);
        }
        return m;
    }

    /** 1 to 5 for the v0.9 heading variants h1 to h5, which A2UI 1.0's Text draws as Markdown; 0 for any other. */
    static int headingLevel(String variant) {
        return variant != null && variant.matches("h[1-5]") ? variant.charAt(1) - '0' : 0;
    }

    /** A text bound to the data model at a JSON pointer. */
    public static Map<String, Object> bound(String id, String path, String variant) {
        Map<String, Object> m = text(id, "", variant);
        m.put("text", Map.of("@path", path));   // A2UI 1.0's data binding
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
        m.put("child", id + "Text");
        // the context's keys are names; each value is a literal or a 1.0 binding, never v0.9.1's (GEO-V-05)
        if (context != null) {
            context.forEach((k, v) -> binding("Button.action.event.context." + k, v));
        }
        m.put("action", Map.of("event", Map.of("name", eventName, "context", context == null ? Map.of() : context)));
        if (variant != null && !variant.isBlank()) {
            m.put("variant", variant);
        }
        return m;
    }

    /**
     * The functions the basic catalog runs in the renderer itself (A2UI 1.0's {@code anyFunction}). A call to one of them
     * never reaches the agent, so an agent function may not take one of these names.
     */
    public static final Set<String> RENDERER_FUNCTIONS = Set.of("required", "regex", "length", "numeric", "email", "formatString", "formatNumber",
            "formatCurrency", "formatDate", "pluralize", "openUrl", "and", "or", "not");

    /** An agent function's name: an identifier, as 1.0 names a function, of at most 64 characters. */
    private static final java.util.regex.Pattern FUNCTION_NAME = java.util.regex.Pattern.compile("[\\p{IsAlphabetic}_][\\p{IsAlphabetic}\\p{IsDigit}_]{0,63}");

    /**
     * An action that calls one of the drawing agent's functions (A2UI 1.0's {@code functionCall}, GEO-V-07): on a press
     * the renderer sends {@code callAgentFunction} with the arguments resolved against the data model, the host carries
     * it to the agent as a {@link SurfaceAction} with a {@code functionCallId}, and the agent's
     * {@link #answer} comes back to the page that pressed. Each argument is a literal or a 1.0 binding
     * ({@code Map.of("@path", "/x")}), never v0.9.1's; the name is an identifier and not one of
     * {@link #RENDERER_FUNCTIONS}, which the renderer runs itself.
     */
    public static Map<String, Object> functionCall(String function, Map<String, Object> args) {
        String name = function == null ? "" : function;
        if (!FUNCTION_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("an agent function's name is an identifier of at most 64 letters, digits and _: '" + name + "'");
        }
        if (RENDERER_FUNCTIONS.contains(name)) {
            throw new IllegalArgumentException("'" + name + "' is a function the renderer runs itself (the basic catalog's); name the agent's function otherwise");
        }
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("@call", name);
        if (args != null && !args.isEmpty()) {
            // the arguments' keys are names; each value is a literal or a 1.0 binding, never v0.9.1's (GEO-V-05)
            args.forEach((k, v) -> binding("functionCall.args." + k, v));
            call.put("args", new LinkedHashMap<>(args));
        }
        return Map.of("functionCall", call);
    }

    /** A button whose press calls one of the drawing agent's functions ({@link #functionCall}) and waits for its answer. */
    public static Map<String, Object> functionButton(String id, String childText, String function, Map<String, Object> args, String variant) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", componentId(id));
        m.put("component", "Button");
        m.put("child", id + "Text");
        m.put("action", functionCall(function, args));
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
        return checked(m);
    }

    public static Map<String, Object> dataTable(String id, List<Map<String, Object>> columns, String rows, String rowAction) {
        return dataTable(id, columns, rows, rowAction, "");
    }

    /**
     * A {@link #dataTable} whose row button reads {@code rowActionLabel} (blank for the action's name, as before), since
     * a person reads "Open" or "Add the page" where the action's name ({@code claims.open}) means nothing to them. The
     * renderer adds the row's first column to the button's accessible name. A label names the button of a
     * {@code rowAction}, so a label with no action is refused here rather than drawn as nothing.
     */
    public static Map<String, Object> dataTable(String id, List<Map<String, Object>> columns, String rows, String rowAction, String rowActionLabel) {
        Map<String, Object> m = viz(id, "DataTable");
        m.put("columns", List.copyOf(columns));
        m.put("rows", rows);
        boolean action = rowAction != null && !rowAction.isBlank();
        if (action) {
            m.put("rowAction", rowAction);
        }
        if (rowActionLabel != null && !rowActionLabel.isBlank()) {
            if (!action) {
                throw new IllegalArgumentException("a DataTable's rowActionLabel names the button of a rowAction, and " + id + " has none");
            }
            m.put("rowActionLabel", rowActionLabel.strip());
        }
        return checked(m);
    }

    /**
     * A {@link #dataTable} with the rest of the table's properties as options: {@code sort}, {@code filter},
     * {@code pageSize}, {@code rowAction}, {@code rowActionLabel}, {@code rowActions} (from {@link #tableAction}
     * or {@link #tablePickAction}), {@code select} (from {@link #tableSelect}), {@code rowFilter} (from {@link #rowFilter})
     * and {@code emptyText}; one the catalog does not declare is refused, and a
     * blank or null option is left out.
     */
    public static Map<String, Object> dataTable(String id, List<Map<String, Object>> columns, String rows, Map<String, Object> options) {
        Map<String, Object> m = viz(id, "DataTable");
        m.put("columns", List.copyOf(columns));
        m.put("rows", rows);
        JsonNode declared = VizCatalog.CATALOG.path("components").path("DataTable").path("properties");
        if (options != null) {
            options.forEach((k, v) -> {
                if (!declared.has(k) || "id".equals(k) || "component".equals(k) || "columns".equals(k) || "rows".equals(k)) {
                    throw new IllegalArgumentException("DataTable has no option " + k + " in " + VizCatalog.ID);
                }
                if (v != null && !(v instanceof String str && str.isBlank())) {
                    m.put(k, v);
                }
            });
        }
        if (m.containsKey("rowActionLabel") && !m.containsKey("rowAction")) {
            throw new IllegalArgumentException("a DataTable's rowActionLabel names the button of a rowAction, and " + id + " has none");
        }
        if (m.containsKey("select")) {
            checkSelect(m.get("select"));
        }
        if (m.get("rowActions") instanceof List<?> list) {
            list.forEach(Surfaces::checkTableAction);
        } else if (m.containsKey("rowActions")) {
            throw new IllegalArgumentException("a DataTable's rowActions is a list of {action, label}");
        }
        if (m.containsKey("rowFilter")) {
            checkRowFilter(m.get("rowFilter"), "a DataTable's rowFilter");
        }
        return checked(m);
    }

    /** The most keys a table's select picks at once: the look's categorical hues fix eight (agentspaces-geo's pick set). */
    public static final int MAX_PICKED = 8;

    /**
     * A DataTable's select (srad/agentspaces-geo.md GEO-D-06): a checkbox per row that adds the row's {@code key} column to the
     * pick set of the GeoMap {@code map} (blank for the GeoLayout's map), at most {@code max} (null for 8, else 1 to 8) at
     * once; {@code label} heads the checkbox column (blank for "Show on the map"); a change of the {@code clearOn}
     * column's value empties the set; {@code focus} names a column holding a map feature's id, whose first-column text
     * then flies the map to it. Blank arguments are left out.
     */
    public static Map<String, Object> tableSelect(String key, String map, Integer max, String label, String clearOn, String focus) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("key", key);
        if (map != null && !map.isBlank()) {
            s.put("map", map);
        }
        if (max != null) {
            s.put("max", max);
        }
        for (String[] kv : new String[][] {{"label", label}, {"clearOn", clearOn}, {"focus", focus}}) {
            if (kv[1] != null && !kv[1].isBlank()) {
                s.put(kv[0], kv[1]);
            }
        }
        checkSelect(s);
        return Map.copyOf(s);
    }

    private static void checkSelect(Object value) {
        if (!(value instanceof Map<?, ?> s)) {
            throw new IllegalArgumentException("a DataTable's select is an object with a key: " + value);
        }
        for (Object k : s.keySet()) {
            if (!List.of("key", "map", "max", "label", "clearOn", "focus").contains(k)) {
                throw new IllegalArgumentException("a DataTable's select has no property " + k);
            }
        }
        if (!(s.get("key") instanceof String key) || key.isBlank()) {
            throw new IllegalArgumentException("a DataTable's select names the column whose value is the row's pick key");
        }
        if (s.containsKey("max")) {
            Object v = s.get("max");
            boolean whole = v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte;
            if (!whole || ((Number) v).longValue() < 1 || ((Number) v).longValue() > MAX_PICKED) {
                throw new IllegalArgumentException("a DataTable's select.max is a whole number from 1 to " + MAX_PICKED + ": " + v);
            }
        }
        for (String k : List.of("map", "label", "clearOn", "focus")) {
            if (s.containsKey(k) && !(s.get(k) instanceof String)) {
                throw new IllegalArgumentException("a DataTable's select." + k + " is a string: " + s.get(k));
            }
        }
    }

    /** One of a DataTable's rowActions: the action a row's button dispatches with {@code {row}}, and the button's words. */
    public static Map<String, Object> tableAction(String action, String label) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("action", action);
        a.put("label", label);
        checkTableAction(a);
        return Map.copyOf(a);
    }

    /**
     * One of a DataTable's rowActions that sets the map's pick set in place of a dispatch (GEO-D-10, as a Watchbox's pick
     * action does): the button's {@code action} name and {@code label}, the row's column {@code from} that lists the keys
     * (the first eight are taken), and the GeoMap {@code map} whose set it is (blank for the GeoLayout's map, else the
     * table's select.map).
     */
    public static Map<String, Object> tablePickAction(String action, String label, String from, String map) {
        return tablePickAction(action, label, from, map, false);
    }

    /**
     * {@link #tablePickAction(String, String, String, String)}, and with {@code dispatch} the button also dispatches its
     * action with {@code {row}}: for an action that brings the keys' rows (a pattern the page does not hold yet), so the
     * first eight keys are picked even where no table lists them yet, each shown as its rows arrive.
     */
    public static Map<String, Object> tablePickAction(String action, String label, String from, String map, boolean dispatch) {
        Map<String, Object> pick = new LinkedHashMap<>();
        pick.put("from", from);
        if (map != null && !map.isBlank()) {
            pick.put("map", map);
        }
        if (dispatch) {
            pick.put("dispatch", true);
        }
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("action", action);
        a.put("label", label);
        a.put("pick", Map.copyOf(pick));
        checkTableAction(a);
        return Map.copyOf(a);
    }

    private static void checkTableAction(Object value) {
        if (!(value instanceof Map<?, ?> a) || !(a.get("action") instanceof String action) || action.isBlank()
                || !(a.get("label") instanceof String label) || label.isBlank() || !List.of("action", "label", "pick").containsAll(a.keySet())) {
            throw new IllegalArgumentException("a DataTable's row action is {action, label}, both words, and an optional pick: " + value);
        }
        if (a.containsKey("pick") && !(a.get("pick") instanceof Map<?, ?> p && List.of("from", "map", "dispatch").containsAll(p.keySet())
                && p.get("from") instanceof String from && !from.isBlank() && (!p.containsKey("map") || p.get("map") instanceof String)
                && (!p.containsKey("dispatch") || p.get("dispatch") instanceof Boolean))) {
            throw new IllegalArgumentException("a DataTable row action's pick is {from: <the row's column that lists the keys>, map?, dispatch?: <boolean>}: " + a.get("pick"));
        }
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
        return checked(m);
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
        return piece("Map", l, "layers", "*");
    }

    /** A panel with a header over one child: dragged by the header, minimized to its bar. */
    public static Map<String, Object> window(String id, String title, String child, String placement) {
        Map<String, Object> m = viz(id, "Window");
        m.put("title", title);
        m.put("child", child);
        if (placement != null && !placement.isBlank()) {
            m.put("placement", placement);
        }
        return checked(m);
    }

    /** A window with buttons in its header: each from {@link #titleAction}. */
    public static Map<String, Object> window(String id, String title, String child, String placement, List<Map<String, Object>> actions) {
        Map<String, Object> m = window(id, title, child, placement);
        if (actions != null && !actions.isEmpty()) {
            m.put("actions", List.copyOf(actions));
        }
        return checked(m);
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
        return checked(m);
    }

    /** A row of actions: each {@code {label, action, primary?, context?, confirm?}} ({@link #toolbarAction} for a confirm). */
    public static Map<String, Object> toolbar(String id, String title, List<Map<String, Object>> actions) {
        for (Map<String, Object> a : actions) {
            if (a.containsKey("confirm")) {
                checkConfirm(a.get("confirm"));
            }
        }
        Map<String, Object> m = viz(id, "Toolbar");
        if (title != null && !title.isBlank()) {
            m.put("title", title);
        }
        m.put("actions", List.copyOf(actions));
        return checked(m);
    }

    /**
     * A toolbar action that asks first: its label, the action, and a {@link #confirm}
     * step; the action is dispatched only on the dialog's yes.
     */
    public static Map<String, Object> toolbarAction(String label, String action, Map<String, Object> confirm) {
        if (label == null || label.isBlank() || action == null || action.isBlank()) {
            throw new IllegalArgumentException("a toolbar action has a label and an action");
        }
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("label", label);
        a.put("action", action);
        if (confirm != null) {
            checkConfirm(confirm);
            a.put("confirm", confirm);
        }
        return Map.copyOf(a);
    }

    /**
     * A toolbar action's confirm step: the dialog's title, its question ({@code text}: literal words, or a data model
     * path such as {@code /status/0/prompt}, as Notice's text takes), and the words of its yes and no buttons
     * (blank for Yes and Cancel). The no button takes the focus; Escape and no dispatch nothing.
     */
    public static Map<String, Object> confirm(String title, String text, String yes, String no) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("title", title);
        for (String[] kv : new String[][] {{"text", text}, {"yes", yes}, {"no", no}}) {
            if (kv[1] != null && !kv[1].isBlank()) {
                c.put(kv[0], kv[1]);
            }
        }
        checkConfirm(c);
        return Map.copyOf(c);
    }

    private static void checkConfirm(Object value) {
        if (!(value instanceof Map<?, ?> c)) {
            throw new IllegalArgumentException("a toolbar action's confirm is an object with a title: " + value);
        }
        for (Object k : c.keySet()) {
            if (!List.of("title", "text", "yes", "no").contains(k)) {
                throw new IllegalArgumentException("a toolbar action's confirm has no property " + k);
            }
        }
        if (!(c.get("title") instanceof String t) || t.isBlank()) {
            throw new IllegalArgumentException("a toolbar action's confirm has a title");
        }
        for (String k : List.of("text", "yes", "no")) {
            if (c.containsKey(k) && !(c.get(k) instanceof String)) {
                throw new IllegalArgumentException("a toolbar action's confirm." + k + " is words: " + c.get(k));
            }
        }
    }

    /** A toolbar whose overflow is a {@link #menu} by id. */
    public static Map<String, Object> toolbar(String id, String title, List<Map<String, Object>> actions, String menu) {
        Map<String, Object> m = toolbar(id, title, actions);
        if (menu != null && !menu.isBlank()) {
            m.put("menu", menu);
        }
        return checked(m);
    }

    // ---- the frame of a page: its title bar, its menus and its panel splits

    /**
     * The page's one bar: a title (a literal, or {@code Map.of("@path", ...)}), a subtitle, an icon, a status
     * from {@link #titleStatus}, buttons from {@link #titleAction}, and the id of a {@link #menu} drawn as its overflow;
     * a blank or empty argument is left out.
     */
    public static Map<String, Object> titleBar(String id, Object title, String subtitle, String icon, Map<String, Object> status,
                                               List<Map<String, Object>> actions, String menu) {
        Map<String, Object> m = viz(id, "TitleBar");
        m.put("title", binding("title", title));
        putText(m, "subtitle", subtitle);
        putText(m, "icon", icon);
        if (status != null && !status.isEmpty()) {
            m.put("status", status);
        }
        if (actions != null && !actions.isEmpty()) {
            m.put("actions", List.copyOf(actions));
        }
        putText(m, "menu", menu);
        return checked(m);
    }

    /** A title bar's status: the path of the WorkingOn rows, and the words for working and for idle. */
    public static Map<String, Object> titleStatus(String from, String working, String idle) {
        Map<String, Object> labels = new LinkedHashMap<>();
        putText(labels, "working", working);
        putText(labels, "idle", idle);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        if (!labels.isEmpty()) {
            m.put("labels", labels);
        }
        return m;
    }

    /** A button in a title bar's or a window's header: its id, its words, an icon name (blank for none), and the action it dispatches. */
    public static Map<String, Object> titleAction(String id, String label, String icon, String actionName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        putText(m, "icon", icon);
        m.put("action", Map.of("name", actionName));
        return m;
    }

    /** A menu behind a {@link #trigger}, over items from {@link #menuItem} and {@link #submenu}. */
    public static Map<String, Object> menu(String id, Map<String, Object> trigger, List<Map<String, Object>> items) {
        Map<String, Object> m = viz(id, "Menu");
        m.put("trigger", trigger);
        m.put("items", List.copyOf(items));
        return checked(m);
    }

    /** A menu whose items the data model holds at {@code itemsPath}, such as a context menu an agent fills per row. */
    public static Map<String, Object> menu(String id, Map<String, Object> trigger, String itemsPath) {
        Map<String, Object> m = viz(id, "Menu");
        m.put("trigger", trigger);
        m.put("items", itemsPath);
        return checked(m);
    }

    /** What opens a menu: {@code button}, {@code overflow}, or {@code context}, and its words. */
    public static Map<String, Object> trigger(String kind, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        putText(m, "label", label);
        return m;
    }

    /** A menu item that dispatches {@code actionName}. */
    public static Map<String, Object> menuItem(String id, String label, String actionName) {
        return menuItem(id, label, actionName, Map.of());
    }

    /**
     * A literal or an A2UI 1.0 data binding ({@code Map.of("@path", "/x")}), as a path-or-literal property takes it; the
     * v0.9.1 forms {@code Map.of("path", "/x")} and {@code Map.of("call", ...)} are refused, naming the property, since
     * the wire carries 1.0 alone (2026-11-12, no v0.9.1 reading kept; GEO-V-05). A map or a list is walked to its
     * leaves, so an old form nested in a list or under another key is refused as well.
     */
    static Object binding(String property, Object value) {
        return binding(null, property, value, null);
    }

    /**
     * The same walk by a schema of {@code catalog}: a map whose schema declares {@code path} (a Map layer's, a Chart
     * series') or {@code call} as a property keeps it, a free-form record (an action's context) keeps its keys as names
     * and has its values walked, an embedded component (a Report section's) is read by its own schema, and any other map
     * with {@code path} or {@code call} and no {@code @path} or {@code @call} is a v0.9.1 form and refused, naming where
     * it sits. With no schema every such map is refused. Returns the value.
     */
    public static <T> T binding(JsonNode catalog, String where, T value, JsonNode schema) {
        JsonNode s = Catalogs.resolve(catalog, schema);
        if (value instanceof Map<?, ?> m) {
            if (catalog != null && m.get("component") instanceof String name && catalog.path("components").has(name)
                    && !Catalogs.declares(catalog, s, "component")) {
                s = catalog.path("components").path(name);
            }
            boolean record = Catalogs.record(catalog, s);
            if (!record && m.containsKey("path") && !m.containsKey("@path") && !Catalogs.declares(catalog, s, "path")) {
                throw new IllegalArgumentException(where + " binds with A2UI 1.0's {\"@path\": ...}, not v0.9.1's {\"path\": ...}");
            }
            if (!record && m.containsKey("call") && !m.containsKey("@call") && !Catalogs.declares(catalog, s, "call")) {
                throw new IllegalArgumentException(where + " calls a function with A2UI 1.0's {\"@call\": ...}, not v0.9.1's {\"call\": ...}");
            }
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                // 1.0's single-@ rule: below a component's root, a key matching ^@([^@]|$) other than @path or @call is
                // reserved (INVALID_RESERVED_KEY); a literal key that starts with @ is written doubled, @@type for @type
                if (reserved(key) && !"@path".equals(key) && !"@call".equals(key)) {
                    throw new IllegalArgumentException(where + "." + key + " is a reserved A2UI 1.0 key; a literal key that starts with @ is written @" + key);
                }
                binding(catalog, where + "." + key, e.getValue(), Catalogs.property(catalog, s, key));
            }
        } else if (value instanceof List<?> l) {
            JsonNode items = Catalogs.items(catalog, s);
            for (int i = 0; i < l.size(); i++) {
                binding(catalog, where + "[" + i + "]", l.get(i), items);
            }
        }
        return value;
    }

    /**
     * Routes every property of a built component through {@link #binding(JsonNode, String, Object, JsonNode)} by the
     * component's schema in {@code catalog}, so no builder writes a v0.9.1 binding or call in any property or option,
     * however deep (GEO-V-05). Returns the component.
     */
    public static Map<String, Object> bindings(JsonNode catalog, Map<String, Object> component) {
        String name = String.valueOf(component.get("component"));
        JsonNode schema = catalog.path("components").path(name);
        for (Map.Entry<String, Object> e : component.entrySet()) {
            String key = e.getKey();
            // 1.0 final (2026-11-10, spec §2.9): a component's envelope keys are @catalogId, @accessibility, @metadata
            // and @checks; their bare names are ordinary properties, so one the component does not declare is refused
            if (ENVELOPE_KEYS.contains("@" + key) && !schema.path("properties").has(key)) {
                throw new IllegalArgumentException(name + "." + key + " is A2UI 1.0's @" + key + " envelope key, not " + key);
            }
            if (reserved(key) && !ENVELOPE_KEYS.contains(key)) {
                throw new IllegalArgumentException(name + "." + key + " is a reserved A2UI 1.0 key; a component's root takes only " + ENVELOPE_KEYS);
            }
            if (!"id".equals(key) && !"component".equals(key)) {
                binding(catalog, name + "." + key, e.getValue(), Catalogs.property(catalog, schema, key));
            }
        }
        return component;
    }

    /**
     * A2UI 1.0's component envelope keys (spec §2.9): the only {@code @}-prefixed keys at a component's root. {@code id}
     * and {@code component} stay bare, as do {@code createSurface.catalogId} and a function call's {@code catalogId}.
     */
    public static final Set<String> ENVELOPE_KEYS = Set.of("@catalogId", "@accessibility", "@metadata", "@checks");

    /** A key in 1.0's reserved single-{@code @} namespace, {@code ^@([^@]|$)}; a doubled {@code @@} is a literal. */
    static boolean reserved(String key) {
        return key.startsWith("@") && !key.startsWith("@@");
    }

    /** A viz or widgets component, its properties walked by its own catalog before a builder hands it out. */
    private static Map<String, Object> checked(Map<String, Object> m) {
        return bindings(WidgetsCatalog.COMPONENTS.contains(String.valueOf(m.get("component"))) ? WidgetsCatalog.CATALOG : VizCatalog.CATALOG, m);
    }

    /** A piece of a component (a Map layer, a menu item) walked by its schema at {@code path} under the component, so the call that wrote it fails. */
    private static Map<String, Object> piece(String component, Map<String, Object> value, String... path) {
        JsonNode schema = VizCatalog.CATALOG.path("components").path(component);
        StringBuilder where = new StringBuilder(component);
        for (String step : path) {
            schema = "*".equals(step) ? Catalogs.items(VizCatalog.CATALOG, schema) : Catalogs.property(VizCatalog.CATALOG, schema, step);
            where.append("*".equals(step) ? "[]" : "." + step);
        }
        return binding(VizCatalog.CATALOG, where.toString(), value, schema);
    }

    /** A menu item with {@code more} of {@code shortcut}, {@code group}, {@code disabled}, {@code checked}, or an action {@code context}. */
    public static Map<String, Object> menuItem(String id, String label, String actionName, Map<String, Object> more) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("name", actionName);
        more.forEach((k, v) -> {
            if ("context".equals(k)) {
                action.put("context", v);
            } else if (v != null && !(v instanceof String str && str.isBlank())) {
                m.put(k, v);
            }
        });
        m.put("action", action);
        return piece("Menu", m, "items", "*");
    }

    /** A submenu: an item whose items open beside it, one level deep. */
    public static Map<String, Object> submenu(String id, String label, List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            if (item.containsKey("items")) {
                throw new IllegalArgumentException("a menu has one level of submenu: " + item.get("id") + " holds items of its own");
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("items", List.copyOf(items));
        return piece("Menu", m, "items", "*");
    }

    /**
     * Two or three panes from {@link #pane}, side by side ({@code horizontal}) or one over another; the
     * browser keeps the sizes under {@code persist} (blank keeps none), and the panes stack below {@code stackBelow}
     * pixels (0 never).
     */
    public static Map<String, Object> split(String id, String orientation, String persist, int stackBelow, List<Map<String, Object>> panes) {
        if (panes.size() < 2 || panes.size() > 3) {
            throw new IllegalArgumentException("a split has two or three panes: " + panes.size());
        }
        String o = orientation == null || orientation.isBlank() ? "horizontal" : orientation;
        if (!o.equals("horizontal") && !o.equals("vertical")) {
            throw new IllegalArgumentException("a split's orientation is horizontal or vertical: " + orientation);
        }
        Map<String, Object> m = viz(id, "Split");
        m.put("orientation", o);
        putText(m, "persist", persist);
        if (stackBelow > 0) {
            m.put("stackBelow", stackBelow);
        }
        m.put("panes", List.copyOf(panes));
        return checked(m);
    }

    /** One pane of a split: its id, its child's id (blank for an empty pane), its share, its minimum in pixels, whether it collapses, and its name. */
    public static Map<String, Object> pane(String id, String child, double size, int min, boolean collapsible, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        putText(m, "child", child);
        m.put("size", size);
        if (min > 0) {
            m.put("min", min);
        }
        if (collapsible) {
            m.put("collapsible", true);
        }
        putText(m, "label", label);
        return m;
    }

    private static void putText(Map<String, Object> m, String key, String value) {
        if (value != null && !value.isBlank()) {
            m.put(key, value);
        }
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
        return checked(m);
    }

    /** Prose: Markdown text as safe HTML, the text or a path to it (named Prose: the A2UI React binding keeps the Markdown element for itself). */
    public static Map<String, Object> prose(String id, String text) {
        Map<String, Object> m = viz(id, "Prose");
        m.put("text", text);
        return checked(m);
    }

    /**
     * The one agentic chat of the {@code agentspaces-widgets} catalog: the agent named over the
     * thread (blank for none), the thread {@code key}, and {@code options} of the component's other properties
     * ({@code reads}, {@code progress}, {@code files}, {@code suggestions}, {@code placeholder}, {@code attach},
     * {@code format}, {@code controls}, {@code max}, {@code preview}, {@code writeAction}), each checked against the
     * catalog, so a word it does not take (a control of {@code delete}) fails here and not at the feed.
     *
     * <p>{@code suggestions} is a list of strings or a data binding ({@code Map.of("@path", "/followups")}) to rows
     * the agent writes, each a string or {@code {text, label, line}}, so the chips follow the latest answer. It is one
     * or the other, never both, since the catalog's property is one of the two shapes; a binding is checked as every
     * binding is, so v0.9.1's {@code {"path": ...}} is refused here.
     */
    public static Map<String, Object> chat(String id, String agent, String key, Map<String, Object> options) {
        WidgetsCatalog.check("Chat", options);
        suggestions(options.get("suggestions"));
        Map<String, Object> m = viz(id, "Chat");
        putText(m, "agent", agent);
        m.put("key", key == null ? "" : key);
        m.putAll(new java.util.TreeMap<>(options));
        return checked(m);
    }

    /**
     * Refuses a Chat {@code suggestions} the catalog's {@code oneOf} would refuse at the feed: anything but a list of
     * strings or 1.0's {@code {"@path": "/..."}} binding alone. v0.9.1's {@code {"path": ...}} is named by the binding
     * check, as everywhere else.
     */
    private static void suggestions(Object value) {
        if (value == null) {
            return;
        }
        binding("Chat.suggestions", value);   // an old form named where it sits, as every builder names it
        if (value instanceof List<?> list && list.stream().allMatch(v -> v instanceof String)) {
            return;
        }
        if (value instanceof Map<?, ?> m && m.size() == 1 && m.get("@path") instanceof String path && path.startsWith("/")) {
            return;
        }
        throw new IllegalArgumentException("Chat.suggestions is a list of strings or one binding {\"@path\": \"/...\"} to the agent's rows, not " + value);
    }

    /**
     * A tree of files over the SharedFile rows at {@code reads}, with {@code options} of
     * {@code working}, {@code label}, {@code upload}, {@code preview}, {@code chat}, {@code actions}, {@code badges},
     * {@code agents}, {@code flat}, {@code accept} and {@code maxBytes}, each checked against the catalog.
     */
    public static Map<String, Object> fileTree(String id, String reads, Map<String, Object> options) {
        WidgetsCatalog.check("FileTree", options);
        Map<String, Object> m = viz(id, "FileTree");
        m.put("reads", reads == null || reads.isBlank() ? "/files" : reads);
        m.putAll(new java.util.TreeMap<>(options));
        return checked(m);
    }

    /**
     * One file by its media type: {@code file} a SharedFile id, or {@code Map.of("@path", ...)}
     * where a tree puts the person's choice, and {@code options} of {@code files}, {@code maxBytes} (at most 16 MiB),
     * {@code csvRows} and {@code kinds}, each checked against the catalog.
     */
    public static Map<String, Object> filePreview(String id, Object file, Map<String, Object> options) {
        WidgetsCatalog.check("FilePreview", options);
        Map<String, Object> m = viz(id, "FilePreview");
        m.put("file", binding("file", file));
        m.putAll(new java.util.TreeMap<>(options));
        return checked(m);
    }

    /** A chat with an agent: the ChatMessage rows at {@code lines}, the thread {@code key}, the agent named. */
    public static Map<String, Object> conversation(String id, String lines, String key, String agent) {
        Map<String, Object> m = viz(id, "Conversation");
        m.put("lines", lines);
        m.put("key", key);
        if (agent != null && !agent.isBlank()) {
            m.put("agent", agent);
        }
        return checked(m);
    }

    public static Map<String, Object> graph(String id, String nodes, String edges, String layout, String nodeAction) {
        Map<String, Object> m = viz(id, "Graph");
        m.put("nodes", nodes);
        m.put("edges", edges);
        m.put("layout", layout == null || layout.isBlank() ? "columns" : layout);
        if (nodeAction != null && !nodeAction.isBlank()) {
            m.put("nodeAction", nodeAction);
        }
        return checked(m);
    }

    /**
     * {@code Visualize} (srad/agentspaces-geo.md §3.4, GEO-D-01): a chart of the table at {@code rows} by a spec: the
     * {@code mark} (bar, column, line, area, scatter or table), the {@code x} column, and {@code y}, one column or a list
     * of them; {@code options} carries {@code color}, {@code aggregate}, {@code sort}, {@code title}, {@code unit} and
     * {@code table}, each checked against the catalog. A {@code heatmap} (srad/agentspaces-geo.md GEO-D-08) also
     * names its {@code row} column and may carry {@code xOrder}, {@code rowOrder}, {@code facet}, {@code pick} (from
     * {@link #heatmapPick}), {@code map}, {@code focus}, {@code cellAction} and {@code selected}; those are refused on any
     * other mark, and a heatmap without its row column is refused, as {@link #heatmap} checks.
     */
    public static Map<String, Object> visualize(String id, String rows, String mark, String x, Object y, Map<String, Object> options) {
        if (rows == null || !rows.startsWith("/")) {
            throw new IllegalArgumentException("a Visualize reads a table at a data model path such as /spend: " + rows);
        }
        if (x == null || x.isBlank()) {
            throw new IllegalArgumentException("a Visualize names its x column");
        }
        if (y != null && !(y instanceof String) && !(y instanceof List<?> l && l.stream().allMatch(v -> v instanceof String))) {
            throw new IllegalArgumentException("a Visualize's y is a column or a list of columns: " + y);
        }
        Map<String, Object> m = viz(id, "Visualize");
        m.put("rows", rows);
        m.put("mark", mark == null || mark.isBlank() ? "column" : mark);
        m.put("x", x);
        if (y != null && !(y instanceof String s && s.isBlank())) {
            m.put("y", y instanceof List<?> l ? List.copyOf(l) : y);
        }
        Map<String, Object> checked = new LinkedHashMap<>(Map.of("mark", m.get("mark")));
        if (options != null) {
            options.forEach((k, v) -> { if (v != null && !(v instanceof String str && str.isBlank())) { checked.put(k, v); m.put(k, v); } });
        }
        Catalogs.checkOptions(VizCatalog.catalog(), "Visualize", checked);
        checkHeatmap(m);
        return checked(m);
    }

    /** The properties only a heatmap reads (GEO-D-08). */
    public static final List<String> HEATMAP_OPTIONS = List.of("row", "xOrder", "rowOrder", "facet", "pick", "map", "focus", "cellAction", "selected");
    /** The most small multiples a heatmap draws. */
    public static final int MAX_FACETS = 8;
    /** The most categories a heatmap's xOrder or rowOrder lists (the catalog's maxItems). */
    public static final int MAX_HEAT_CATEGORIES = 400;
    /** The 24 hours of a day as two-digit words, "00" to "23": a heatmap's xOrder for a day by the hour. */
    public static final List<String> HOURS = java.util.stream.IntStream.range(0, 24).mapToObj(h -> (h < 10 ? "0" : "") + h).toList();
    /** The seven days of a week from Sunday, "Sun" to "Sat": a heatmap's rowOrder for a week (ISO 8601's weeks start on Monday; turn it if yours does). */
    public static final List<String> WEEKDAYS = List.of("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat");

    /**
     * {@code Visualize mark: heatmap} (srad/agentspaces-geo.md GEO-D-08): a grid of {@code x} across by {@code row}
     * down, each cell's value the column {@code value} (null or blank for a count of the rows) aggregated by the option
     * {@code aggregate}; {@code options} carries the rest ({@code xOrder}, {@code rowOrder}, {@code facet}, {@code pick},
     * {@code map}, {@code focus}, {@code cellAction}, {@code selected}, {@code title}, {@code unit}, {@code table}).
     *
     * <pre>{@code
     * Surfaces.heatmap("week", "/cells", "x", "y", "value", Map.of("xOrder", Surfaces.HOURS, "rowOrder", Surfaces.WEEKDAYS,
     *         "facet", "alias", "pick", Surfaces.heatmapPick("alias"), "focus", "place", "cellAction", "cell.open", "selected", "/slot"));
     * }</pre>
     */
    public static Map<String, Object> heatmap(String id, String rows, String x, String row, String value, Map<String, Object> options) {
        Map<String, Object> o = new LinkedHashMap<>();
        if (options != null) {
            o.putAll(options);
        }
        if (o.containsKey("row")) {
            throw new IllegalArgumentException("a heatmap names its row column as an argument, not an option");
        }
        o.put("row", row);
        return visualize(id, rows, "heatmap", x, value, o);
    }

    /** A heatmap's pick (GEO-D-08): while a key is picked, only the rows whose column {@code by} holds a picked key are drawn. */
    public static Map<String, Object> heatmapPick(String by) {
        if (by == null || by.isBlank()) {
            throw new IllegalArgumentException("a heatmap's pick.by names the column whose value is the row's pick key");
        }
        return Map.of("by", by);
    }

    private static void checkHeatmap(Map<String, Object> m) {
        boolean heat = "heatmap".equals(m.get("mark"));
        for (String k : HEATMAP_OPTIONS) {
            if (!heat && m.containsKey(k)) {
                throw new IllegalArgumentException("Visualize." + k + " is a heatmap's; mark " + m.get("mark") + " does not read it");
            }
        }
        if (!heat) {
            return;
        }
        if (!(m.get("row") instanceof String row) || row.isBlank()) {
            throw new IllegalArgumentException("a heatmap names its row column, the categories down its side");
        }
        if (m.get("y") instanceof List<?> ys && ys.size() > 1) {
            throw new IllegalArgumentException("a heatmap colours each cell by one column, not " + ys);
        }
        for (String k : List.of("xOrder", "rowOrder")) {
            if (m.containsKey(k) && !(m.get(k) instanceof List<?> l && !l.isEmpty() && l.stream().allMatch(v -> v instanceof String)
                    && l.stream().distinct().count() == l.size())) {
                throw new IllegalArgumentException("a heatmap's " + k + " is a list of categories, each once: " + m.get(k));
            }
            if (m.get(k) instanceof List<?> l && l.size() > MAX_HEAT_CATEGORIES) {
                throw new IllegalArgumentException("a heatmap's " + k + " lists at most " + MAX_HEAT_CATEGORIES + " categories, not " + l.size());
            }
        }
        for (String k : List.of("facet", "map", "focus", "cellAction")) {
            if (m.containsKey(k) && !(m.get(k) instanceof String v && !v.isBlank())) {
                throw new IllegalArgumentException("a heatmap's " + k + " is a column or a name: " + m.get(k));
            }
        }
        if (m.containsKey("selected") && !(m.get("selected") instanceof String sel && sel.startsWith("/"))) {
            throw new IllegalArgumentException("a heatmap's selected is a data model path such as /slot: " + m.get("selected"));
        }
        if (m.containsKey("pick") && !(m.get("pick") instanceof Map<?, ?> p && p.size() == 1 && p.get("by") instanceof String by && !by.isBlank())) {
            throw new IllegalArgumentException("a heatmap's pick is {by: <the column that holds the pick key>}: " + m.get("pick"));
        }
    }

    /**
     * The filter a DataTable's {@code rowFilter} and a GeoMap layer's {@code filter} share (GEO-M-12): keep the rows whose
     * {@code match} columns (one, or several) equal the value at the data model {@code path}; a missing or empty value
     * keeps every row.
     */
    public static Map<String, Object> rowFilter(String path, List<String> match) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("path", path);
        f.put("match", match != null && match.size() == 1 ? match.get(0) : match == null ? null : List.copyOf(match));
        checkRowFilter(f, "a filter");
        return Map.copyOf(f);
    }

    /** {@link #rowFilter(String, List)} on one column. */
    public static Map<String, Object> rowFilter(String path, String match) {
        return rowFilter(path, match == null ? null : List.of(match));
    }

    /** Checks a filter's shape: a path that starts with /, and a column or a list of columns, each once. */
    public static void checkRowFilter(Object value, String what) {
        if (!(value instanceof Map<?, ?> f) || !List.of("path", "match").containsAll(f.keySet())) {
            throw new IllegalArgumentException(what + " is {path, match}: " + value);
        }
        if (!(f.get("path") instanceof String p) || !p.startsWith("/")) {
            throw new IllegalArgumentException(what + "'s path is a data model path such as /slot: " + f.get("path"));
        }
        Object match = f.get("match");
        boolean one = match instanceof String c && !c.isBlank();
        boolean many = match instanceof List<?> l && !l.isEmpty() && l.stream().allMatch(c -> c instanceof String s && !s.isBlank()) && l.stream().distinct().count() == l.size();
        if (!one && !many) {
            throw new IllegalArgumentException(what + "'s match is a column or a list of columns, each once: " + match);
        }
    }

    /**
     * {@code GraphView} (GEO-G-01): the graph of a nodes table and an edges table, laid out {@code force}, {@code tree} or
     * {@code layered}; {@code options} carries {@code features} (GeoFeatures whose links are the edges, instead of the two
     * tables), {@code map}, {@code selectAction} and {@code title}.
     */
    public static Map<String, Object> graphView(String id, String nodes, String edges, String layout, Map<String, Object> options) {
        Map<String, Object> m = viz(id, "GraphView");
        if (nodes != null && !nodes.isBlank()) {
            m.put("nodes", nodes);
        }
        if (edges != null && !edges.isBlank()) {
            m.put("edges", edges);
        }
        m.put("layout", layout == null || layout.isBlank() ? "force" : layout);
        Map<String, Object> checked = new LinkedHashMap<>(Map.of("layout", m.get("layout")));
        if (options != null) {
            options.forEach((k, v) -> { if (v != null && !(v instanceof String str && str.isBlank())) { checked.put(k, v); m.put(k, v); } });
        }
        Catalogs.checkOptions(VizCatalog.catalog(), "GraphView", checked);
        if (!m.containsKey("features") && (!m.containsKey("nodes") || !m.containsKey("edges"))) {
            throw new IllegalArgumentException("a GraphView binds a nodes table and an edges table, or GeoFeatures with their links");
        }
        return checked(m);
    }

    public static Map<String, Object> timeline(String id, String events, String lanes) {
        Map<String, Object> m = viz(id, "Timeline");
        m.put("events", events);
        if (lanes != null && !lanes.isBlank()) {
            m.put("lanes", lanes);
        }
        return checked(m);
    }

    /** A report of sections: each {@code {heading, kind, text | component}}. */
    public static Map<String, Object> report(String id, String title, List<Map<String, Object>> sections) {
        Map<String, Object> m = viz(id, "Report");
        m.put("title", title);
        m.put("sections", List.copyOf(sections));
        return checked(m);
    }

    public static Map<String, Object> notification(String id, String items, String ackAction, String routeAction) {
        Map<String, Object> m = viz(id, "Notification");
        m.put("items", items);
        m.put("ackAction", ackAction);
        if (routeAction != null && !routeAction.isBlank()) {
            m.put("routeAction", routeAction);
        }
        return checked(m);
    }

    public static Map<String, Object> form(String id, String fragment, List<String> fields, String submitAction) {
        Map<String, Object> m = viz(id, "Form");
        m.put("fragment", fragment);
        if (fields != null && !fields.isEmpty()) {
            m.put("fields", List.copyOf(fields));
        }
        m.put("submitAction", submitAction);
        return checked(m);
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
        return checked(m);
    }
}
