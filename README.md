# ForwardMeasure Document Intelligence

A standalone Java component for document conversion through Docling Serve, immutable artifact references, conversion provenance and DocLang/DCLX validation. It belongs alongside the other ForwardMeasure components. Owning applications retain document admission, tenant authorization and business workflow state.

## Build

Requires Java 25 and Maven 3.9.9 or newer, plus the ForwardMeasure platform parent and shared object-storage/API-contract artifacts at version 1.1.0.

```bash
mvn -B verify
python3 tools/prepare-openapi.py --check
```

`verify` generates and compiles the client, runs tests, packages the runnable CLI, and checks Java formatting, licensing and platform dependency policy. Dependency and plugin versions come from the platform parent and framework BOMs. The compatibility reactor builds this component after object storage.

## Modules

| Module path (root prefix omitted) | Responsibility |
|---|---|
| api-specifications | Pinned upstream OpenAPI snapshots, generator input and provenance |
| docling-client-java | OpenAPI Generator Java/Apache HttpClient 5 client, generated at build time |
| contracts | ForwardMeasure inputs, durable task handles, artifact references, submissions, receipts and validation reports |
| core | Bounded transport, prepared-model gates, async primitives, artifact workflow, canonical fingerprints and local artifact repository |
| validation | Pinned DocLang 0.7.3 XSD **and Schematron**, namespace normalization evidence, bounded ZIP/DCLX/OPC validation |
| object-storage | Adapter to the shared `forwardmeasure-object-storage-api`, with pinned versions and hash/length verification |
| framework-bindings | Maven parent aggregating the reusable framework integrations |
| framework-bindings/spring | Spring Boot auto-configuration |
| framework-bindings/quarkus | CDI producers and disposal |
| framework-bindings/micronaut | Micronaut factory and lifecycle |
| cli | Runnable command-line client |
| bom | Component dependency management |

The artifact prefix is `forwardmeasure-document-intelligence-`; the group is `com.forwardmeasure.documentintelligence` and the component revision is `1.1.0`.

The bindings live under `forwardmeasure-document-intelligence-framework-bindings/` and inherit from its parent POM. Their artifact IDs remain `forwardmeasure-document-intelligence-spring`, `forwardmeasure-document-intelligence-quarkus` and `forwardmeasure-document-intelligence-micronaut`. Each binding imports its own framework BOM; the CLI remains a separate executable module.

## Generated client contract

The generator uses **the full Serve 1.37.0 schema extracted from the exact CPU image pinned by the Helm chart**, including all 23 upstream REST paths and 166 schemas. The upstream snapshot is retained unchanged in the API specifications JAR, alongside the captured 1.31.0 snapshot for compatibility evidence. OpenAPI Generator 7.25.0 creates Java sources under the client module's `target/generated-sources/openapi`; generated Java is never edited or committed.

[Contract provenance](forwardmeasure-document-intelligence-api-specifications/src/main/resources/META-INF/openapi/contract-provenance.json) records the image, package inventory, file hashes and generation adjustments. [prepare-openapi.py](tools/prepare-openapi.py) reproducibly produces the generator input and checks drift. There is no longer a mixed-release, reduced "contract projection" as the generator input.

The generator input removes Python defaults/`const`/discriminator annotations to avoid Java union/default-generation defects; union models therefore do not enforce variant constraints locally. Tuples become arrays of their original homogeneous type, or Object arrays for mixed tuples, preserving length bounds. The server remains authoritative for variant validation. Conversion/chunk/result responses are deliberately generated as opaque files: the wrapper preserves the original JSON or ZIP bytes, including future Docling fields. All original endpoint definitions and document schemas remain in the source snapshot.

The generated layer exposes the complete upstream API, including multipart and administrative routes. The higher-level gateway exposes conversion, task polling/result retrieval, health/version/capability discovery and typed hierarchical/hybrid chunk requests. It uses generated request models and API operations; it does not hand-code HTTP paths.

Conversion calls take generated `ConvertDocumentsOptions`, including generated `ProcessingPipeline`, `OutputFormat` and `ImageRefMode` values. Polling and waiting return generated `TaskStatusResponse` with `ConversionStatus`. `DoclingOptions.standard()` supplies the application's JSON/Markdown defaults; callers can also construct generated options directly. Optional generated collections default to null so omitted options use server defaults. The `contracts` module contains ForwardMeasure-specific workflow/storage types and depends on the generated client; the generated client depends only on the API specifications and its transport/serialization libraries.

## Java usage

```java
var settings = DoclingSettings.defaults(URI.create("http://localhost:5001"), System.getenv("DOCLING_API_KEY"));
try (var gateway = new GeneratedDoclingGateway(settings)) {
    var source = new BinaryDocument("report.pdf", Files.readAllBytes(Path.of("report.pdf")));
    var options = DoclingOptions.standard()
        .toFormats(List.of(OutputFormat.JSON, OutputFormat.MD));
    var task = gateway.submit(source, options);
    // Persist task.taskId() immediately in the owning durable workflow.
    var progress = gateway.poll(task, Duration.ofSeconds(5));
    if (progress.getTaskStatus() == ConversionStatus.SUCCESS) {
        var result = gateway.fetch(task);
        Files.write(Path.of("response.json"), result.content());
    }
}
```

For durable artifact processing, construct `DocumentIntelligenceService` with the gateway, an `ArtifactRepository`, a `DoclangArchiveValidator` and a finite input bound. `submit(sourceReference, filename, options, context)` verifies source bytes and returns a serializable `ConversionSubmission`. Persist it before any polling. `poll(submission, wait)` exposes one poll. `retrieveAndStore(submission)` accepts only a successful task, validates requested outputs, stores the original response plus individual artifacts and writes a provenance manifest. Persist the resulting `ConversionReceipt` in the owning workflow. Artifact publication is not a transaction across all files; an unsuccessful write can leave harmless content-addressed objects, but no successful receipt is returned until all writes finish.

Artifact extraction supports JSON, Markdown, text, HTML, DocLang and DCLX. Other generated output formats are available through the gateway/raw generated client; the artifact service rejects them before submission. Durable submissions defensively copy mutable generated options. Fingerprints cover every generated option, including nested custom configurations, with stable object-key and output-format ordering.

This replaces the former handwritten conversion options and task-status types. Java callers must use generated models; persisted submission options now use OpenAPI property names and enum values, and their fingerprint representation has changed. Submissions written with the former options structure must be reconciled before reuse; this change does not automatically migrate or resubmit server tasks.

Operation fingerprints include scope, source URI/version/hash and filename (used for format inference), canonical options and operator-supplied immutable image/model-bundle identities. They are provenance identities, **not server idempotency keys**. Docling does not guarantee idempotent submissions. A network failure after POST produces `SUBMISSION_OUTCOME_UNKNOWN`; reconcile the owning workflow before resubmitting. There are no automatic HTTP retries or redirects. Errors use the platform's shared RFC 9457 `Problem` and omit upstream private bodies.

Inline source and response sizes and transport timeouts are finite. Default inline input is 16 MiB and response is 64 MiB. The high-level source operation base64-encodes one bounded document; use the generated multipart API for larger/specialized uploads with appropriate caller limits. Chunk requests use generated models and require the caller to bound supplied sources and select prepared tokenizer/model options; they are not part of the artifact-oriented conversion workflow.

`await` is a bounded convenience for operators, with zero-wait server polls and a short local polling delay. The local deadline must leave enough budget for the configured connection-acquisition, connect and read timeouts before another request begins. Interruption is preserved; cancellation stops local polling and does not cancel the server task. For workflow workers, use one `poll` per scheduled activity instead of holding a worker in `await`. Expired task results require recovery through the owning workflow's policies.

## Runtime identity and model preparation

`RuntimeIdentity` requires an image reference containing `@sha256:` and the SHA-256 of the actual prepared model-bundle manifest. Obtain these from deployment evidence; do not infer the bundle from `/version`. Provenance labels the identity authority as the deployment operator. A scope key isolates fingerprint identities, but does not authorize tenant access.

VLM requires an explicit named preset; `default` is rejected. VLM and enrichment options must also appear in `DoclingSettings`' explicitly prepared feature sets. Capability discovery is optional and can return 404/403 on older/restricted deployments; advertisement does not prove model readiness. Stage presets and advanced generated request configurations must match the prepared deployment. Standard CPU conversion uses the chart's prepared standard models; GPU/VLM qualification remains a separate deployment activity.

## Framework configuration

All three bindings create the same gateway and close its owned transport on shutdown. Spring uses auto-configuration; Quarkus uses indexed CDI producers; Micronaut uses generated bean definitions. Applications provide artifact repositories and create their owning artifact workflow/service; bindings do not add a new REST service or business database.

```properties
document-intelligence.docling.endpoint=http://docling-docling-serve.docling-serve.svc.cluster.local:5001
document-intelligence.docling.api-key=${DOCLING_API_KEY}
document-intelligence.docling.connect-timeout=PT10S
document-intelligence.docling.read-timeout=PT2M
document-intelligence.docling.max-input-bytes=16777216
document-intelligence.docling.max-response-bytes=67108864
document-intelligence.docling.prepared-vlm-presets=
document-intelligence.docling.prepared-enrichments=
```

Durations use ISO-8601. Prepared presets/enrichments are comma-separated; enrichment names are the `Enrichment` enum names. Configure a secret at runtime; never export API keys into collections or source control. The client uses Docling's `X-Api-Key`. Application OIDC/AuthZEN admission remains with the owning product. The high-level gateway uses a single service connection and does not forward `X-Tenant-Id`; deployments requiring that Docling header must use the generated API's tenant parameter under their application authorization policy.

## CLI

```bash
java -jar forwardmeasure-document-intelligence-cli/target/forwardmeasure-document-intelligence-cli-1.1.0-all.jar --help
```

Commands: `health`, `convert <file> <artifact-root> [json,md,doclang,dclx]`, `submit <file> <artifact-root> [formats]`, `poll <submission.json>`, `fetch <submission.json> <artifact-root>`, `validate <file.xml|file.dclx>`.

CLI connection settings map the property names above to uppercase environment variables, replacing dots/hyphens with underscores: `DOCUMENT_INTELLIGENCE_DOCLING_ENDPOINT`, `DOCUMENT_INTELLIGENCE_DOCLING_API_KEY`, and so forth. `submit`/`convert` also require `DOCUMENT_INTELLIGENCE_RUNTIME_IMAGE` and `DOCUMENT_INTELLIGENCE_MODEL_BUNDLE_SHA256`; optional `DOCUMENT_INTELLIGENCE_SCOPE` defaults to `cli`. `DOCUMENT_INTELLIGENCE_DEADLINE` defaults to `PT15M` for `convert`. `health` and `validate` do not require a runtime identity.

Source bytes are imported into the local artifact repository before submission. A submission JSON file enables `poll`/`fetch` in later invocations. Receipt and provenance files refer to content-addressed local artifacts. Preserve the artifact root while resuming; do not place it on an untrusted shared filesystem.

## DocLang and DCLX

Validation uses the upstream 0.7.3 XSD, Schematron and reference Schematron transpiler, with Saxon-HE supplied by the platform. Empty-namespace Docling XML can be normalized for validation; the raw original bytes remain unchanged, and the report records both hashes. XML forbids DTD/entities/external resources and bounds bytes, depth and element count. ZIP validation reads the central directory, checks CRC and declared size, rejects unsafe/duplicate/symlink/encrypted members, and bounds compressed bytes, expanded bytes, member sizes/count and compression ratio. Nested DCLX shares the outer expansion budget. OPC relationships, document content type and picture asset references must resolve within the package. This proves structural/semantic/package validity; it does not prove OCR accuracy or full document-layout fidelity.

## Postman and evidence

Import [the collection](postman/ForwardMeasure%20Docling%20Deployment.postman_collection.json) and [environment](postman/ForwardMeasure%20Docling%20Deployment.postman_environment.json). See [Postman instructions](postman/README.md) and [implementation evidence](docs/implementation-evidence-2026-10-14.md).

The chart changes and historical complete remediation plan remain in the Helm repository. The chart version was not edited. This implementation creates local code and artifacts; it does not publish a remote repository, release artifacts or upgrade the live cluster.
