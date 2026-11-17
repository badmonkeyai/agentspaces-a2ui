# About agentspaces-a2ui

Written for: Java developers whose agents draw A2UI surfaces, and anyone who writes a renderer or a host that reads them.

agentspaces-a2ui holds the A2UI records an agent writes into a space and a renderer reads back, as one small artifact over the AgentSpaces space API. The records are plain Java, the pen takes a `Space`, and golden CBOR vectors pin the bytes, so an agent in any fleet draws A2UI into a group with no other software present.

## The records

| Record | What |
| --- | --- |
| `Surface` | one A2UI 1.0 envelope of a surface (`createSurface`, `updateComponents`, `updateDataModel`, `deleteSurface`), with the app, the surface id, the catalog, and its sequence number |
| `SurfaceAction` | a renderer's action on a surface (`name`, `surfaceId`, `sourceComponentId`, `timestamp`, `context`), with the session it came through; a page's call to one of the agent's functions arrives in the same shape with a `functionCallId` (`callsFunction()`, `function()`, `args()`) |
| `Surfaces` | the pen that writes a surface's envelopes in order and hands back the actions, with builders for the basic catalog's common components and for the `agentspaces-viz` catalog's (`chart`, `dataTable`, `map`, `graph`, `timeline`, `report`, `notification`, `form`, `stat`, and the panels beside them) |
| `Publish` | an agent's request that its surfaces be served as an application for end users at an address of its own: the app host in the group takes it and sets the application card's `host`; `signOn` is `idp` (the host's identity provider, the default) or `none` |
| `PublishStatic` | an agent's request that a static app be served for end users: the app, the title, the content root and length of its bundle on the group's content swarm, the entry file, each file's path and media type, and `signOn` (`idp`, the default, or `none` for anyone to view); the app host serves the files as is, under the version's own address, and reads nothing live |
| `StaticBundle`, `StaticFile` | the bundle a `PublishStatic` names by root: every file in path order; `StaticBundle.decode` reads it under the strict wire read and `open(publish)` returns the files only when the bundle holds exactly the ones the publish names |
| `VizCatalog` | the open `agentspaces-viz` catalog (`catalogs/agentspaces-viz/catalog.json` in the jar) and the look-and-feel JSON Schema (`catalogs/look-and-feel.schema.json`: tokens only) |
| `WidgetsCatalog` | the open `agentspaces-widgets` catalog (`catalogs/agentspaces-widgets/catalog.json` in the jar) |
| `Surfaces` builders | `titleBar`, `menu`, `split` (agentspaces-viz) and `chat`, `fileTree`, `filePreview` (agentspaces-widgets); a builder refuses an option its component's catalog does not take, and a v0.9.1 `{"path": ...}` or `{"call": ...}` in any property or option, however deep |

### The open records

`ai.badmonkey.agentspaces.a2ui.records` holds five records under `https://badmonkey.ai/agentspaces/a2ui/v1/`: `ChatMessage` (a line of a thread, in `conversations`), `SharedFile` (a file on the content swarm, in `shared`), `WorkingOn` (what an agent holds while it works, in `views`, leased 60 s), `ChatProgress` (an agent's progress on one line, in `views`, leased 30 s), and `FunctionResponse` (an agent's answer to a page's function call, in `views`, leased a minute). `ChatMessage` version 2 adds `format` and `cites`, and `SharedFile` version 2 adds `path` and `replaces`. A version 2 field is omitted while null, so a version 1 value keeps the version 1 bytes. A member that reads version 2 advertises `a2ui.records=2` (`A2uiRecords.hint()`), and a writer asks `A2uiRecords.versionFor(readerHints)` which version to write. SPEC §6.1a.7 is the normative text.

## How an agent and a renderer use them

- An agent that draws A2UI writes `Surface` entries into its group's `views` space and reads `SurfaceAction` entries back. Each pen call writes one envelope, numbered in order per surface, so a renderer that joins late reads them back in sequence.
- `pen.publish(...)` asks the group's app host to serve the surfaces as an application for end users at its own address, which is where people use what the agent drew. A console that joins the group lists the application and links to it.
- A host that depends on this artifact may name the records under its own namespace, so entries its earlier versions wrote read unchanged.

```java
Surfaces pen = new Surfaces(views, "app-desk", InstantSource.system());
pen.create("claim-77", Surface.BASIC_CATALOG)
   .components("claim-77", List.of(Surfaces.column("root", List.of("t", "ack")),
                                   Surfaces.text("t", "Claim 77 needs a decision", "body"),
                                   Surfaces.button("ack", "Acknowledge", "acknowledge", Map.of("claimId", "c-77"), "primary"),
                                   Surfaces.text("ackText", "Acknowledge", null)));
for (SurfaceAction a : pen.actions()) { ... }
```

`Surfaces.button` names its label as the component `<id>Text`, so the surface also carries a `Text` with that id, as above.

## Agent functions

A component's action may call one of the drawing agent's functions and wait for its answer: A2UI 1.0's `callAgentFunction`.

- **Draw the call.** `Surfaces.functionCall(function, args)` is 1.0's action `{"functionCall": {"@call": function, "args": {...}}}`, and `Surfaces.functionButton(id, label, function, args, variant)` is a button that makes it, its label the `Text` with the id `<id>Text`, as for `Surfaces.button`. Each argument is a literal or a 1.0 binding (`Map.of("@path", "/selected")`), checked as every builder checks a value. The name is an identifier of at most 64 characters, and never one of the basic catalog's own functions (`Surfaces.RENDERER_FUNCTIONS`), which the renderer runs itself.
- **Take the call.** On a press the renderer resolves the arguments against the data model and the host carries the call to the agent as a `SurfaceAction`: its `name` is the function, its `context` the arguments, and its `functionCallId` the call's id. `pen.actions()` hands it out once, like any action, and `a.callsFunction()` tells it apart.
- **Answer it.** `pen.answer(call, value)` writes a `FunctionResponse` with the value, and `pen.fail(call, code, message)` one with an error. The answer copies the call's app, surface, session and id. The value is JSON of at most 32,768 characters as JSON writes it, escapes included, and 16 levels, with no NaN or infinite number. A host delivers the answer, as 1.0's `agentFunctionResponse`, to the page of the calling session alone. It drops an answer to a call it does not hold.

```java
Map<String, Object> lookUp = Surfaces.functionButton("lookUp", "Look up", "berthOf", Map.of("vessel", Map.of("@path", "/selected")), "primary");
for (SurfaceAction a : pen.actions()) {
    if (a.callsFunction() && "berthOf".equals(a.function())) {
        String berth = berths.get(String.valueOf(a.args().get("vessel")));
        if (berth == null) pen.fail(a, "NOT_FOUND", "No such vessel in port."); else pen.answer(a, Map.of("berth", berth));
    }
}
```

The rules a host keeps:

- Take a call only from a session it knows, since a page with no session shares the empty one with every other visitor.
- Take only a function a component of the surface calls, so a page calls what the agent drew and nothing else. An `updateComponents` replaces components by id and leaves the others, so the components are those of every `updateComponents` since the surface's last `createSurface`, each as the newest one that names it left it.
- Bound each session's calls.
- Pass an ordinary action on without a `functionCallId`, so only a call the host took reaches an agent as one.
- Deliver an answer only to that session's open streams of the surface, and only from a peer that drew the surface. A peer drew the surface when it wrote an envelope of it that the host admitted; an envelope the validator refused makes no peer a drawer. Any member that writes an admitted envelope of the surface is a drawer, so the group's members are trusted to answer for what they draw.
- Judge an answer again on its next look at the drawers while its call waits, rather than drop it for good, when the answering peer began drawing since the last look.
- A renderer reports a call that gets no answer in its time.

## Leases

- A surface's creation, its components, its whole data model, and its publish stay for the pen's lease: a day unless the pen is given another.
- An update of one path of the data model stays for the pen's update lease: a minute unless the pen is given another. A stream that updates a path every second must not leave an entry for every second in a space every member replicates. A renderer that joins late takes the whole model and the updates still alive.

## A surface authored elsewhere

`Surfaces.draw(surfaceId, envelopes)` draws a surface authored elsewhere (a template, the A2UI Composer's output) under any surface id, so one authored surface serves every key. Each envelope is written in order under the new id, with the pen's own sequence numbers, and a `createSurface` that names no catalog gets the basic catalog.

## Static apps

A static app (kind `assembled`) works the same way without surfaces:

1. The agent encodes a `StaticBundle` with `CborCodec.toBytes`.
2. It publishes the bytes on the group's content swarm.
3. It writes a `PublishStatic` that names the root.

Paths stay inside the app (no `..`, no hidden file, nothing that needs escaping), so a host refuses a path outside the bundle before it fetches anything. The golden vectors `publish_static_cbor` and `static_bundle_cbor` pin the two records.

## The catalogs

- **`agentspaces-viz`**: the components people look at in a published application: Chart (by the dataviz method), DataTable, Map, Graph, Timeline, Report, Notification, Form, and Stat, with the panels Window, Conversation, Notice, Toolbar, KeyValue, and Prose, and the frame of a page: TitleBar, Menu, and Split. An agent names series and paths, the renderer draws with the host's look and feel, and nothing executes.
  - **A path in a graph.** Graph's and GraphView's node and edge rows may carry `lit: true`. Graph then lights exactly the lit edges (and those a person hovers or focuses), and GraphView draws the lit nodes as selected and the lit edges in the accent. A lit row's accessible text adds ", on the path".
  - **A row button's words.** DataTable's `rowActionLabel` is the words on each row's button, which is named with the row's first column for a screen reader.
  - **A marking line.** A conversation layout may carry a `banner` (`Publish.conversation(chat, size, areas, kicker, banner)`): one line of plain text, at most 120 characters, that the host draws at the very top of the page and at its foot.
- **`agentspaces-widgets`**: the application components Chat, FileTree, and FilePreview, and the raw widgets ChatInput, FileShare, ApprovalCard, and TimelineStrip. Each reads a list from the surface's data model and writes an entry back through the host's callbacks. A widget's `entry` names a record type under `https://badmonkey.ai/agentspaces/a2ui/v1/`, and its `element` is the custom element a renderer draws it with. A surface on this catalog draws the `agentspaces-viz` components too, so a page puts a TitleBar over a Split of its tree, its preview and its chat.
  - **Chat** is a thread of `ChatMessage` rows as bubbles, the agent named over it, suggestions, and a Send box. Under a person's line it shows the agent's progress from that line's `ChatProgress` (submitted, working with the last step, needs your input), the steps it took, and "The agent stopped" with Retry when the progress lapses. An answer of `text/markdown` draws as safe Markdown whose code blocks copy, and its `cites` are chips. The controls copy, retry, stop (`chat.stop`) and feedback (`chat.feedback`, a thumbs up "Helpful" and a thumbs down "Not helpful" with a pressed state) are actions. `suggestions` is a list of questions or a binding to rows the agent writes (`{text, label, line}`), so the chips follow the newest answer: a row with a `line` draws while that line is the newest of the thread, a row with none always draws, at most 8 chips draw, and a tap sends the row's whole `text`. The visitor's secret rides only with the page's `write` action. ChatInput and Conversation are its aliases through 0.6.x.
  - **FileTree** is the `SharedFile` rows as folders from each `path`, with the WAI-ARIA tree keyboard, badges (an agent working on a file from `WorkingOn`, written by an agent, new or changed since this person looked), upload by a button or a drop, and a context menu: Ask about, Download, Share (`file.share`), Rename (`file.rename`), Remove (`file.remove`). The host applies the three actions. FileShare is its alias as a flat list.
  - **FilePreview** draws one file by its media type: text and Markdown as text, CSV as a table of its first rows, PNG and JPEG as an image, PDF in a sandboxed frame, HTML and SVG as their text. Above `maxBytes` it offers Download.
- **The look-and-feel schema**: the tokens a published application draws with (colours, type, spacing, density, radius, shadow), light and dark modes, and a texture for the colour-blind and print case. Tokens only: no URL, no markup, no stylesheet.

Both catalogs are closed A2UI 1.0 documents, in the basic catalog's shape:

- **Closed components.** Every component schema says `additionalProperties: false`. A property the catalog does not declare is refused, by a host's validator and by any 1.0 validator. Each component declares 1.0's envelope properties (`id`, `catalogId`, `accessibility`, `metadata`) beside its own.
- **`$defs`.** `anyComponent` names every component by reference, `anyFunction` says the catalog has no functions of its own, and the shared types (`ComponentId`, `DataBinding`, `TextOrBinding`, `NamedAction`, `HeaderAction`, `Accept`) are referenced from the properties that use them.
- **The builders walk every value.** A builder walks every property and option value it writes. It refuses v0.9.1's `{"path": ...}` binding and `{"call": ...}` call and names where it sits, such as `Map.follow`. A property the catalog declares as `path` (a Map layer's, a Chart series') passes, and so does an action context's entry called `path`.

Both catalogs are open, and a host adds its own catalogs beside them. `VizCatalog.catalog()`, `WidgetsCatalog.catalog()`, and `VizCatalog.lookAndFeelSchema()` read the documents from the jar. The open React renderer [`@agentspaces/a2ui-react`](https://github.com/badmonkeyai/agentspaces-a2ui-react) draws both catalogs and the basic catalog in a browser; its build regenerates its types from these documents and the golden surfaces, and fails when they drift.

## The example

`src/test/java/ai/badmonkey/agentspaces/a2ui/SurfaceDeskExampleTest.java`:

- Two peers over TCP share a `views` space.
- The desk peer draws one card per decision with a `Surfaces` pen.
- The renderer peer reads the envelopes back in order and presses the card's button, which reaches the desk as a `SurfaceAction`.
- The same envelopes are drawn again as a template for the next decision.

Nothing in it needs more than this library and the AgentSpaces runtime; a console that joins the group draws the same envelopes.

## Coordinates

```xml
<dependency>
  <groupId>ai.badmonkey.agentspaces</groupId>
  <artifactId>agentspaces-a2ui</artifactId>
  <version>0.6.0</version>
</dependency>
```

It brings `agentspaces-api`, `agentspaces-common`, and `jackson-databind`, at the versions the `agentspaces-dependencies` 0.6.0 BOM names. Java 21.
