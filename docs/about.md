# About agentspaces-a2ui

Written for: Java developers whose agents draw A2UI surfaces, and anyone who writes a renderer or a host that reads them.

agentspaces-a2ui holds the A2UI records an agent writes into a space and a renderer reads back, as one small artifact over the AgentSpaces space API. The records are plain Java, the pen takes a `Space`, and golden CBOR vectors pin the bytes, so an agent in any fleet draws A2UI into a group with no other software present.

## The records

| Record | What |
| --- | --- |
| `Surface` | one A2UI v0.9.1 envelope of a surface (`createSurface`, `updateComponents`, `updateDataModel`, `deleteSurface`), with the app, the surface id, the catalog, and its sequence number |
| `SurfaceAction` | a renderer's action on a surface (`name`, `surfaceId`, `sourceComponentId`, `timestamp`, `context`), with the session it came through |
| `Surfaces` | the pen that writes a surface's envelopes in order and hands back the actions, with builders for the basic catalog's common components and for the `agentspaces-viz` catalog's (`chart`, `dataTable`, `map`, `graph`, `timeline`, `report`, `notification`, `form`, `stat`, and the panels beside them) |
| `Publish` | an agent's request that its surfaces be served as an application for end users at an address of its own: the app host in the group takes it and sets the application card's `host`; `signOn` is `idp` (the host's identity provider, the default) or `none` |
| `PublishStatic` | an agent's request that a static app be served for end users: the app, the title, the content root and length of its bundle on the group's content swarm, the entry file, each file's path and media type, and `signOn` (`idp`, the default, or `none` for anyone to view); the app host serves the files as is, under the version's own address, and reads nothing live |
| `StaticBundle`, `StaticFile` | the bundle a `PublishStatic` names by root: every file in path order; `StaticBundle.decode` reads it under the strict wire read and `open(publish)` returns the files only when the bundle holds exactly the ones the publish names |
| `VizCatalog` | the open `agentspaces-viz` catalog (`catalogs/agentspaces-viz/catalog.json` in the jar) and the look-and-feel JSON Schema (`catalogs/look-and-feel.schema.json`: tokens only) |
| `WidgetsCatalog` | the open `agentspaces-widgets` catalog (`catalogs/agentspaces-widgets/catalog.json` in the jar) |
| `Surfaces` builders | `titleBar`, `menu`, `split` (agentspaces-viz) and `chat`, `fileTree`, `filePreview` (agentspaces-widgets); a builder refuses an option its component's catalog does not take |

### The open records

`ai.badmonkey.agentspaces.a2ui.records` holds four records under `https://badmonkey.ai/agentspaces/a2ui/v1/`: `ChatMessage` (a line of a thread, in `conversations`), `SharedFile` (a file on the content swarm, in `shared`), `WorkingOn` (what an agent holds while it works, in `views`, leased 60 s), and `ChatProgress` (an agent's progress on one line, in `views`, leased 30 s). `ChatMessage` version 2 adds `format` and `cites`, and `SharedFile` version 2 adds `path` and `replaces`. A version 2 field is omitted while null, so a version 1 value keeps the version 1 bytes. A member that reads version 2 advertises `a2ui.records=2` (`A2uiRecords.hint()`), and a writer asks `A2uiRecords.versionFor(readerHints)` which version to write. SPEC §6.1a.7 is the normative text.

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
                                   Surfaces.text("ack-text", "Acknowledge", null)));
for (SurfaceAction a : pen.actions()) { ... }
```

`Surfaces.button` names its label as the component `<id>-text`, so the surface also carries a `Text` with that id, as above.

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
- **`agentspaces-widgets`**: the application components Chat, FileTree, and FilePreview, and the raw widgets ChatInput, FileShare, ApprovalCard, and TimelineStrip. Each reads a list from the surface's data model and writes an entry back through the host's callbacks. A widget's `entry` names a record type under `https://badmonkey.ai/agentspaces/a2ui/v1/`, and its `element` is the custom element a renderer draws it with. A surface on this catalog draws the `agentspaces-viz` components too, so a page puts a TitleBar over a Split of its tree, its preview and its chat.
  - **Chat** is a thread of `ChatMessage` rows as bubbles, the agent named over it, suggestions, and a Send box. Under a person's line it shows the agent's progress from that line's `ChatProgress` (submitted, working with the last step, needs your input), the steps it took, and "The agent stopped" with Retry when the progress lapses. An answer of `text/markdown` draws as safe Markdown whose code blocks copy, and its `cites` are chips. The controls copy, retry, stop (`chat.stop`) and feedback (`chat.feedback`) are actions. The visitor's secret rides only with the page's `write` action. ChatInput and Conversation are its aliases through 0.6.x.
  - **FileTree** is the `SharedFile` rows as folders from each `path`, with the WAI-ARIA tree keyboard, badges (an agent working on a file from `WorkingOn`, written by an agent, new or changed since this person looked), upload by a button or a drop, and a context menu: Ask about, Download, Share (`file.share`), Rename (`file.rename`), Remove (`file.remove`). The host applies the three actions. FileShare is its alias as a flat list.
  - **FilePreview** draws one file by its media type: text and Markdown as text, CSV as a table of its first rows, PNG and JPEG as an image, PDF in a sandboxed frame, HTML and SVG as their text. Above `maxBytes` it offers Download.
- **The look-and-feel schema**: the tokens a published application draws with (colours, type, spacing, density, radius, shadow), light and dark modes, and a texture for the colour-blind and print case. Tokens only: no URL, no markup, no stylesheet.

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
