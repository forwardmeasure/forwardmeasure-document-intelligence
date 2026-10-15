# Document Intelligence implementation evidence — 2026-10-14

## Current scope

Standalone local repository and Maven component implemented with contracts, generated client, core, validation, shared object-storage adapter, Spring/Quarkus/Micronaut bindings, CLI and BOM. Postman collection/environment are canonical under `postman/`; home paths are symlinks. No GitHub remote, publication, live chart upgrade or model download was performed by the client implementation. The image was pulled locally for schema capture and HTML qualification; model loading at boot was disabled for that isolated fixture.

## Contract evidence

The exact chart-pinned CPU image `quay.io/docling-project/docling-serve-cpu@sha256:6eda9c524992147c1b0b06a1a9be4894bd68b1f43c86d92c32f996c4c60d18a3` produced its full `create_app().openapi()` with networking disabled and lifespan not started. Serve 1.37.0, Docling Slim 2.138.0, Core 2.101.1, Jobkit 3.8.2, FastAPI 0.141.1 and Pydantic 2.14.1 were observed. The full schema has 23 paths and 166 schemas. Provenance stores snapshot and generator-input hashes. Generator normalizations retain every REST path/schema; union constraints are validated by the server, and conversion/chunk/result bytes are opaque to preserve native output.

## Executed checks

| Check | Result | Evidence |
|---|---|---|
| Full Java reactor compile | Passed | `/tmp/forwardmeasure-fdi-research/bindings-build.log` |
| Reactor tests | 133 tests passed, no failures/errors/skips | `/tmp/forwardmeasure-fdi-research/reactor-tests.log` |
| Clean Maven verify | Passed; generation, compilation, tests, JAR/shaded CLI packaging, Spotless, RAT and platform policy | `/tmp/forwardmeasure-fdi-research/clean-verify.log` |
| Contract regeneration drift | Passed | `python3 tools/prepare-openapi.py --check` |
| Exact image qualification | Passed: readiness, inline JSON/Markdown/DocLang, UTF-8, async submit/poll/result, ZIP and nested DCLX validation | `/tmp/forwardmeasure-fdi-research/qualification-137.log` |

Tests include 101 upstream valid/invalid DocLang examples plus namespace, XXE, semantic-only Schematron, XML size/depth, OPC/assets, archive traversal/symlinks/truncation/expansion; generated request bodies and API-key headers; raw response preservation; task mismatch/expiry/cancellation/deadline; uncertain POST outcome without retry; source hash/version checks; local artifacts/provenance; real Spring, Quarkus and Micronaut container wiring against HTTP fixtures; CLI validation/help. Storage adapter tests are contract/unit tests with a provider fixture, not live cloud-provider acceptance. Local image output artifacts are under `/tmp/forwardmeasure-fdi-research/qualification-137`.

The isolated image's HTML conversion does not qualify PDF/OCR model loading, GPU/VLM performance, real cloud storage, workload scaling, full geometry fidelity, framework-native packaging or a live cluster rollout. Native JSON is retained as the original response; extracted JSON is serialized without mapping into business schemas. The production cluster was previously observed on Serve 1.31.0; the full target client was qualified locally on 1.37.0.

A failed filename-field test compilation is retained as `/tmp/forwardmeasure-fdi-research/verify-submission-filename-failed.log`; the test fixture was updated before the final successful verification. Wildcard imports were corrected before successful formatting verification. JaCoCo module reports are generated during normal verification. A normal `verify` is not a passing 85% line/85% branch coverage gate; the platform's `coverage` profile owns those thresholds.

The moved Postman assets were revalidated against both the historical 1.31.0 schema and the full extracted 1.37.0 schema: 15 request bodies passed, the official collection schema passed, and 47 JavaScript scripts/30 control scenarios passed. The packaged CLI `--help` and `health` commands also ran successfully against the local image. Conversion fingerprints include the input filename to distinguish format inference; connection acquisition has a finite timeout and polling budgets account for acquisition/connect/read timeouts.

Final clean verification completed successfully on 2026-10-14 at 12:56 America/New_York: 133 tests, zero failures, errors or skipped tests. The local Maven install also succeeded. Platform BOM validation passed after component registration. The temporary local qualification container was removed after the generated client tests; the pinned image remains cached for repeat qualification. The live namespace was not changed.
