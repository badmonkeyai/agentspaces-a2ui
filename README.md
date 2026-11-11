# agentspaces-a2ui

**A2UI for AgentSpaces agents**, from Bad Monkey AI. An agent draws an [A2UI](https://a2ui.org) surface by writing its envelopes into a space it shares with its group, any renderer in the group draws them for people and writes their button presses back, and two open catalogs, `agentspaces-viz` and `agentspaces-widgets`, add charts, tables, maps, and widgets beside A2UI's basic catalog. It is for Java developers whose agents need to show people something and hear back, through the group they already share.

## Quick start: draw your first surface

An agent draws with a `Surfaces` pen: each call writes one A2UI envelope as a `Surface` entry, in order. A renderer answers with a `SurfaceAction`, which the pen hands back. In this sample one Spring Boot app plays both parts. The first instance is a refund desk that draws one card and answers its button. The second stands in for a renderer: it prints the card, presses the button, and prints the desk's answer.

**1. Add the dependencies.** The project is `refunds:refund-desk:1.0.0` on the Spring Boot 4.1 parent, with the Spring Boot Maven plugin and the `ai.badmonkey.agentspaces:agentspaces-dependencies:0.6.0` BOM imported. It needs this library, the AgentSpaces starter, and Spring Boot's own starter:

```xml
<dependency><groupId>ai.badmonkey.agentspaces</groupId><artifactId>agentspaces-a2ui</artifactId><version>0.6.0</version></dependency>
<dependency><groupId>ai.badmonkey.agentspaces</groupId><artifactId>agentspaces-spring-boot-starter</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency>
```

**2. Configure the peer** in `src/main/resources/application.yml`. Surfaces and actions travel in a space named `views`. The default `mtls` security profile secures every link, so the file has no security block.

```yaml
spring.main.web-application-type: none   # the group is the interface
spring.main.banner-mode: off
logging.level.root: warn                 # only what the desk and the renderer print
agentspaces:
  bind: 127.0.0.1:${desk.port:7701}
  groups:
    - name: refunds
      founding: refunds-v1
      seeds: ${desk.seeds:}              # empty on the first instance
      spaces:
        - name: views                    # where surfaces and actions travel
```

**3. Write the desk.** The card is a flat list of basic-catalog components that refer to each other by id. The status text is bound to `/status` in the surface's data model, so the desk answers a press by updating that path. Both classes sit in package `refunds`, and the listings leave out the package and import lines.

```java
@SpringBootApplication
public class DeskApp {

    public static void main(String[] args) {
        SpringApplication.run(DeskApp.class, args);
    }

    @Bean
    ApplicationRunner run(AgentSpaces spaces, @Value("${desk.press:false}") boolean press) {
        Space views = spaces.group("refunds").space("views");
        return args -> {
            if (press) { Renderer.pressOnce(views); System.exit(0); }   // the second instance: a stand-in renderer
            desk(views);                                                 // the first instance: the agent
        };
    }

    static void desk(Space views) throws InterruptedException {
        Surfaces pen = new Surfaces(views, "refunds", InstantSource.system());
        pen.create("refund-42", Surface.BASIC_CATALOG)
           .data("refund-42", "/", Map.of("status", "waiting for a decision"))
           .components("refund-42", List.of(
                   Surfaces.card("root", "body"),
                   Surfaces.column("body", List.of("title", "status", "approve")),
                   Surfaces.text("title", "Refund 42: a kettle that arrived broken, 30 EUR", "h3"),
                   Surfaces.bound("status", "/status", "body"),
                   Surfaces.button("approve", "Approve", "approve", Map.of("refundId", "r-42"), "primary"),
                   Surfaces.text("approve-text", "Approve", null)));   // the button's label
        System.out.println("desk: drew refund-42");
        while (true) {
            for (SurfaceAction a : pen.actions()) {
                System.out.println("desk: " + a.action().get("name") + " " + a.action().get("context") + " from " + a.session());
                pen.data(a.surfaceId(), "/status", "approved by " + a.session());
            }
            Thread.sleep(250);
        }
    }
}
```

**4. Write the stand-in renderer.** It reads the envelopes back in sequence order, as every A2UI renderer does, and presses the button with the event name and context the button carries.

```java
class Renderer {

    static void pressOnce(Space views) throws InterruptedException {
        Surface drawn = envelopes(views, 3).stream().filter(s -> s.kind().equals("updateComponents")).findFirst().orElseThrow();
        Map<String, Object> button = Map.of();
        for (Object o : (List<?>) map(drawn.message().get("updateComponents")).get("components")) {
            Map<String, Object> c = map(o);
            if (c.get("text") instanceof String text) System.out.println("renderer: " + text);
            if ("Button".equals(c.get("component"))) button = c;
        }
        Map<String, Object> event = map(map(button.get("action")).get("event"));
        views.write(new SurfaceAction(SurfaceAction.VERSION, "refunds", "refund-42", "reviewer-1",
                Map.of("name", event.get("name"), "surfaceId", "refund-42", "sourceComponentId", button.get("id"),
                       "timestamp", Instant.now().toString(), "context", event.get("context")), Instant.now()),
                Lease.of(Duration.ofMinutes(5)));
        System.out.println("renderer: pressed " + event.get("name"));
        Map<String, Object> answer = map(envelopes(views, 4).get(3).message().get("updateDataModel"));
        System.out.println("renderer: " + answer.get("path") + " is now \"" + answer.get("value") + "\"");
    }

    static List<Surface> envelopes(Space views, int atLeast) throws InterruptedException {
        while (true) {
            List<Surface> all = views.readAll(Template.of(Surface.class), 100).stream()
                    .filter(s -> s.surfaceId().equals("refund-42"))
                    .sorted(Comparator.comparingLong(Surface::seq)).toList();
            if (all.size() >= atLeast) return all;
            Thread.sleep(250);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) { return (Map<String, Object>) o; }
}
```

**5. Run two instances.** Build the jar, start the desk, then start the stand-in renderer, which joins through it:

```
mvn -q package -DskipTests
java -jar target/refund-desk-1.0.0.jar &
java -jar target/refund-desk-1.0.0.jar --desk.port=7702 --desk.seeds=127.0.0.1:7701 --desk.press=true
```

The renderer prints the card's two texts, `renderer: pressed approve`, and `renderer: /status is now "approved by reviewer-1"`. The desk prints `desk: approve {refundId=r-42} from reviewer-1`. Any A2UI renderer that reads the group's `views` space draws the same envelopes as a card with a button.

## What it is

- `Surface`, `SurfaceAction`, `Publish`, `PublishStatic`, and `StaticBundle`: the records an agent and a renderer exchange in a group's `views` space, with golden CBOR vectors that pin their bytes.
- `Surfaces`: the pen that writes a surface's envelopes in order, builders for the basic catalog and for `agentspaces-viz`, and `draw` for a surface authored elsewhere.
- `VizCatalog` and `WidgetsCatalog`: the open `agentspaces-viz` and `agentspaces-widgets` catalogs (the frame of a page: `TitleBar`, `Menu`, `Split`; the application components `Chat`, `FileTree`, `FilePreview`) and a look-and-feel schema, as JSON in the jar; their open React renderer is the package `@agentspaces/a2ui-react`. A page of the six, as `Surfaces` builds it: `titleBar("bar", "Workbench", ...)` over `split("main", "horizontal", "workbench", 720, List.of(pane("l", "tree", 0.25, 180, true, "Files"), pane("m", "preview", 0.45, 320, false, "Preview"), pane("r", "chat", 0.30, 300, true, "Chat")))`, with `fileTree("tree", "/files", ...)`, `filePreview("preview", Map.of("path", "/selected/file"), ...)` and `chat("chat", "workbench", "main", ...)` in its panes.
- `ChatMessage`, `SharedFile`, `WorkingOn` and `ChatProgress` in `ai.badmonkey.agentspaces.a2ui.records`: the open records a chat, a file tree and a progress line read, with the member hint `a2ui.records=2` that gates their version 2 fields.
- Java 21; it depends on `agentspaces-api`, `agentspaces-common`, and Jackson, and on no framework or platform.

## Add it to your build

The quick start's first step is the whole build for a Spring Boot host: `ai.badmonkey.agentspaces:agentspaces-a2ui:0.6.0` from Maven Central beside the AgentSpaces and Spring Boot starters. Any host that gives you a `Space` works the same way. [About agentspaces-a2ui](docs/about.md) describes every record and both catalogs, and [Building](docs/building.md) builds from a clone.

## Where to read next

1. **[The AgentSpaces developer guide](https://www.badmonkey.ai/agentspaces/developer-guide.html)**: groups, spaces, leases, and the hosts that give an agent its `Space`.
2. **[The A2UI specification](https://a2ui.org)**: the envelopes and the basic catalog this library writes.
3. **[The example](src/test/java/ai/badmonkey/agentspaces/a2ui/SurfaceDeskExampleTest.java)**: two peers over TCP, a card drawn and answered, and the same envelopes drawn again as a template.
4. **[@agentspaces/a2ui-react](https://github.com/badmonkeyai/agentspaces-a2ui-react)**: the open React renderer of both catalogs, on npm.
5. **[The documents](docs/README.md)**: everything this README leaves out, one topic per document, each with the reader it is written for.

## License

Apache License 2.0, Copyright 2026 Bad Monkey, Inc. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Contributions are welcome under the Developer Certificate of Origin; see [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md). Report a vulnerability privately, as [SECURITY.md](SECURITY.md) describes. [Releasing](docs/releasing.md) has the release steps, and [CHANGELOG.md](CHANGELOG.md) what each release changed.
