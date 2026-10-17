# DocLang qualification corpus

Source of truth for the DocLang qualification harness. Design:
[`doclang-qualification-fixtures.md`](../../docs/doclang-qualification-fixtures.md).

- `sources/` — committed **input** documents. Synthetic or public-domain only.
- `golden/` — committed **output** DocLang/DCLX produced by a live (Tier 2) run.
- `manifest.yaml` — one entry per document; see the design doc for the schema.

Real, authorised evidence goes in `../external/` (git-ignored). It can drive a live
run but is never committed and never produces a committed golden.

## Add a document

1. Put the file in `sources/` (synthetic) or `../external/` (real).
2. Add a `manifest.yaml` entry. The `expect.structure.containsText` list — specific
   content you know is in the document — is what makes the check meaningful.
3. Produce the golden and fill hashes:
   `mvn -pl forwardmeasure-document-intelligence-qualification test \
      -Dfdi.qualification.live=true -Dfdi.qualification.refresh=true`
4. Review the golden once. Tier 1 (default `mvn test`) then guards it with no network.
