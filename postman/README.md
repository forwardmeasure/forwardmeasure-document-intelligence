# ForwardMeasure Docling Deployment Postman collection

Import [the collection](ForwardMeasure%20Docling%20Deployment.postman_collection.json) and [the optional environment](ForwardMeasure%20Docling%20Deployment.postman_environment.json) into Postman. Copies are also available directly in /home/pn, beside the existing ForwardMeasure Geocoding collection.

The collection contains **27 requests in nine folders** for the current Docling deployment and its planned Serve 1.37.0 upgrade. It follows the Geocoding collection's folder, variable and response-test structure. Docling uses its own X-Api-Key authentication.

## Connect

The observed deployment on October 14, 2026 is CPU Serve 1.31.0, with ClusterIP Service docling-docling-serve:5001 in namespace docling-serve and no public ingress.

Start this in a terminal:

```bash
kubectl --context gke_genai-llm-393115_us-central1_openworkflow-prod \
  -n docling-serve port-forward service/docling-docling-serve \
  5001:5001 --address 127.0.0.1
```

Use the Postman desktop app or Desktop Agent to reach localhost. The default docling_base_url is http://127.0.0.1:5001; change it if using another local port or an approved endpoint.

Set docling_api_key as a local, secret environment value when API-key protection is enabled. The exported credential is blank. Health, version, OpenAPI and capability requests explicitly use no authentication because these upstream endpoints are public. Protected conversion/task requests inherit API-key authentication.

expected_docling_serve_version is blank by default, so current-version checks can run before rollout. Set it to 1.37.0 when verifying the upgraded deployment.

## Requests

| Folder | Purpose |
|---|---|
| Deployment Checks | Health, liveness, readiness, installed versions and OpenAPI |
| Standard Conversion | Built-in fixture, HTTP source, local upload, forced OCR and referenced-image ZIP |
| Asynchronous Conversion - Built-in Fixture | Submit, bounded polling and result retrieval |
| Asynchronous Conversion - File Upload | Same lifecycle with a selected local document |
| DocLang and DCLX | DocLang XML export/import, DCLX ZIP export and DCLX file import |
| Chunking | Hierarchical fixture chunking and optional hybrid chunking |
| Prepared VLM Profiles - Optional | Explicit Granite Docling 258M and Granite Vision 3.2 requests |
| Capability Discovery - Optional | Formats, presets, limits and features after the upgrade |
| Authentication Checks - Optional | Missing/wrong API keys against a nonexistent task ID |

Start with Deployment Checks and Convert Built-in Fixture - JSON Markdown Text. The fixture is a short HTML document containing a heading, table and reference. The response tests independently check retained expected text, structured JSON and output status.

The current OpenAPI advertises DocLang/DCLX already. These are representation examples, not a claim of validated conversion quality. The collection's XML/content and round-trip checks do not perform XSD, Schematron, archive bounds or complete geometry/asset-fidelity validation; those belong in forwardmeasure-document-intelligence.

For ZIP requests, use Postman's Send and Download. DCLX export returns an outer ZIP containing a .dclx member and companion output files. Extract the .dclx member before using Import Uploaded DCLX; selecting the outer ZIP is a different input format.

## Variables and optional requests

Collection defaults make the fixture workflows usable without an environment. The companion environment contains connection/options variables only. Workflow state stays in collection variables, preventing blank exported environment values from shadowing captured task IDs.

| Variable | Default | Use |
|---|---|---|
| enable_http_source_requests | false | Set source_url, then enable URL conversion |
| enable_file_upload_requests | false | Select local files in each request's Body/form-data, then enable |
| enable_doclang_requests | true | Synthetic XML round trip and DCLX export; file import also needs uploads enabled |
| enable_vlm_requests | false | Use only on the appropriate prepared VLM deployment |
| enable_hybrid_chunking_request | false | Enable after qualifying tokenizer/preset assets |
| enable_capabilities_request | false | Enable after Serve 1.37.0 rollout with capability discovery enabled |
| enable_authentication_checks | false | Enable when protected routes must reject missing/wrong keys with HTTP 401 |
| poll_wait_seconds | 5 | Server-side long-poll wait, between 0 and 30 seconds |
| poll_max_attempts | 60 | Maximum polling attempts, between 1 and 1,000 |
| docling_tenant_id | empty | Optional Ray X-Tenant-Id context; does not establish FEI tenant authorization |

Optional requests use pm.execution.skipRequest in their pre-request scripts. This applies when using Send as well as the Collection Runner. Enable the relevant switches before sending them. Use a current Postman runtime supporting this method; no Newman compatibility is asserted.

Files are selected per request through Postman's file picker. File src is initially null. For a full Runner run, configure all enabled upload requests first; otherwise run the specific configured folder/request.

Multipart option fields are flattened. List-valued fields such as to_formats are repeated rows, and JSON source bodies use arrays. Let Postman set multipart Content-Type and its boundary.

VLM requests explicitly set pipeline=vlm and the named preset. Each request requires the corresponding prepared profile; enabling a switch does not prepare a model. The regular CPU/standard deployment lacks these whole-page VLM models. Hybrid chunking can require additional tokenizer assets.

The capability endpoint is optional and was absent from the observed 1.31.0 OpenAPI. Preset advertisement alone does not establish model readiness or eliminate default-alias behavior.

## Asynchronous workflow

Run the built-in fixture async folder in its existing order:

1. Submit captures docling_task_id and resets the polling counter.
2. Poll Until Terminal uses the server's wait parameter. The Runner repeats this request until terminal status or the configured limit.
3. Fetch Result verifies document output. Save the response promptly because fetched results can expire.

For manual Send, automatic Runner navigation does not run. Send the poll request again until the reported task is terminal, then fetch the result.

Unknown status, exhausted polling or a mismatched task ID stops Runner navigation and records a failing assertion. Failed/partial/skipped conversions fail the success assertion; a terminal failure can still be fetched for diagnostics. Result retrieval rejects a missing task ID or a nonterminal task before sending.

The local engine has ephemeral task state. A Postman task ID is an operational handle and is not a durable artifact or business operation identity.

## Validation performed when creating the collection (historical baseline)

- Official Postman Collection v2.1 schema validation.
- JavaScript syntax checks and simulated async/gating/round-trip script behavior.
- Request bodies and form options checked against the live Serve 1.31.0 OpenAPI schema; the optional capability route checked against pinned Serve 1.37.0 source.
- Read-only live health, readiness, liveness, version and OpenAPI requests.

No conversion or administrative clearing request was submitted, and no deployment setting was changed. Script simulation does not substitute for a real Postman conversion run or document-quality qualification.

Primary sources: [Serve 1.37.0 API](https://github.com/docling-project/docling-serve/blob/v1.37.0/docling_serve/app.py), [API-key authentication](https://github.com/docling-project/docling-serve/blob/v1.37.0/docling_serve/auth.py), [Postman execution methods](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-execution/), [Postman Collection schema](https://schema.getpostman.com/json/collection/v2.1.0/collection.json).

The collection is now owned by `forwardmeasure-document-intelligence/postman`. The home-directory filenames are symlinks to these canonical files. The generated Java client was separately qualified against the pinned Serve 1.37.0 image; see [implementation evidence](../docs/implementation-evidence-2026-10-14.md).
