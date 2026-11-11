# Building

Written for: contributors who build this repository.

```bash
mvn -B -ntp verify                                       # the records, the catalogs, the vectors, and the two-peer example: 31 tests
mvn -B -ntp verify -Pcentral-publish -Dgpg.skip=true     # plus the sources and javadoc jars, unsigned, as CI runs it
mvn -q clean install                                     # into the local repository, for a host that builds on it
node tools/docs-lint.mjs .                               # the README's shape and this folder's index
```

Java 21 and Maven. The AgentSpaces 0.6.0 artifacts (the `agentspaces-dependencies` BOM, `agentspaces-api`, and `agentspaces-common`, plus `agentspaces-peering`, `agentspaces-space`, and `agentspaces-identity` for the example test) resolve from Maven Central.

## What the tests pin

- `A2uiGoldenTest`, `VizCatalogTest`, and `StaticAppTest` encode each record and compare the bytes with its vector under `src/test/resources/vectors`. A missing vector file is written on the next run, so delete a vector only when its bytes are meant to change, and say why in the pull request.
- `WidgetsCatalogTest` pins the widgets catalog as `src/test/resources/golden/widgets_catalog.json`, and `VizCatalogTest` writes one surface per component to `golden/surfaces.json` for a renderer's conformance test.
- `RecordContractsTest` scans the package's compiled records for their null policy, so a new record without a row there fails.
- `SurfaceDeskExampleTest` runs two peers over TCP on `127.0.0.1`.

## Source files

Every Java source, the POM, and the workflows carry the Apache 2.0 header. The JSON documents (the catalogs, the look-and-feel schema, and the golden files) and the hex vectors are data that JSON and hex cannot annotate; `LICENSE` covers them.
