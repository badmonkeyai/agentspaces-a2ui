# Contributing to AgentSpaces A2UI

Thank you for your interest in contributing. AgentSpaces A2UI is an Apache 2.0
project from Bad Monkey, Inc., and everything in this repository stays
Apache 2.0.

This repository holds the A2UI records an agent writes into a space and a
renderer reads back (`Surface`, `SurfaceAction`, `Publish`, `PublishStatic`,
`StaticBundle`), the `Surfaces` pen that writes them, and the two open
catalogs, `agentspaces-viz` and `agentspaces-widgets`. The space API it writes
through, and the coordination fabric itself, live in the
[AgentSpaces core repository](https://github.com/badmonkeyai/agentspaces).

## Before you start

- **Questions and ideas** are welcome as [GitHub issues](https://github.com/badmonkeyai/agentspaces-a2ui/issues).
- **Security issues** must not be filed as public issues. See
  [SECURITY.md](SECURITY.md) for private reporting.
- **Code of conduct:** by participating you agree to the
  [code of conduct](CODE_OF_CONDUCT.md).

## The rules a change must keep

- **The bytes are the contract.** Every record travels between peers in
  canonical CBOR, and the golden vectors under `src/test/resources/vectors`
  pin those bytes. Peers in other languages and other hosts read the same
  bytes, so a test that fails on a vector means the change broke them. The
  strict wire read refuses a field a record does not name, so a new field
  breaks every reader that has not upgraded. Add a new record type, as
  `PublishStatic` sits beside `Publish`, rather than a field on an existing
  one. Regenerate a vector (delete its file and run the tests) only when the
  pull request says why the bytes change.
- **The catalogs are pinned too.** `src/test/resources/golden/widgets_catalog.json`
  pins the widgets catalog, and `golden/surfaces.json` holds one surface per
  component for a renderer's conformance test. A catalog change updates its
  golden file in the same pull request.
- **The open side stays open.** The records, the pen, and the catalogs name no
  product built on top of them. IRIs live under
  `https://badmonkey.ai/agentspaces/`, component elements start with
  `agentspaces-`, and a host adds its own catalogs beside these rather than
  inside them.
- **The null policy holds.** A record refuses a null app and surface id where
  it is built and defaults the rest. `RecordContractsTest` scans every record
  in the package, so a new record needs its row there.
- **A static app stays inside its directory.** `PublishStatic.isPath` and
  `StaticBundle.open` are what let a host serve a bundle without fetching
  anything outside it. A change that loosens either needs a test that shows
  the path it now accepts is still safe to serve.
- **Small and framework-free.** The library depends on `agentspaces-api`,
  `agentspaces-common`, and Jackson. Spring, Micronaut, and any UI toolkit stay
  out of it; a host brings them.

## Building and testing

```bash
mvn -B -ntp verify                                       # the records, the catalogs, the vectors, and the two-peer example
mvn -B -ntp verify -Pcentral-publish -Dgpg.skip=true     # plus the sources and javadoc jars, as CI runs it
node tools/docs-lint.mjs .                               # the README's shape and the docs index
```

Java 21 and Maven. The AgentSpaces 0.6.0 artifacts resolve from Maven Central.
The example test starts two peers over TCP on `127.0.0.1` and needs no other
network.

## Pull requests

Open an issue first for anything beyond a fix. Keep the change small, add the
test that fails without it, and sign off your commits (DCO). CI runs the build,
the docs lint, and a dependency scan on every push and pull request.
