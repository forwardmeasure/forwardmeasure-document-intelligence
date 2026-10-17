# DocLang qualification fixture infrastructure

Date: 2026-10-16.

## Why this exists

Nothing has yet established that Docling Serve produces **complete and correct DocLang**
on real documents. The evidence so far is one single-page OCR smoke conversion in
production and HTML-only conversions locally; the validator passes the 101 upstream
`.dclg` examples, which proves the validator, not Docling's output.

Indexical (the extraction agent) reads DocLang as evidence: element identity, page
breaks, tables, captions, form key/value pairs, content layers, and the distinction
between printed text and Docling's model-written `description`/`summary`. If any of those
are wrong or missing, extraction is wrong. This harness converts representative documents
to DocLang, validates every output, and asserts the structure Indexical depends on.

It is qualification, not a unit test of the validator. It belongs in
`forwardmeasure-document-intelligence` because this component owns conversion
(`DocumentIntelligenceService`) and DocLang validation (`DoclangValidator`,
`DoclangArchiveValidator`).

## Two tiers

**Tier 1 — offline golden (default, CI-safe, no network).** For each corpus document we
commit the DocLang (and, where requested, DCLX) that Docling produced, as a *golden*
artifact. The harness loads each golden, runs `DoclangValidator.validate(bytes)` /
`DoclangArchiveValidator.validateDclx(bytes)`, and checks the structural expectations in
the manifest. This runs with no Docling Serve and no models — fast, deterministic, and it
catches regressions in our validator or in the expectations themselves.

**Tier 2 — live qualification (opt-in).** The harness submits each source document to a
real, pinned Docling Serve through `DocumentIntelligenceService.submit/poll/
retrieveAndStore`, captures the produced DocLang/DCLX, validates it, and asserts the same
expectations. With a refresh flag it (re)writes the goldens and updates the manifest
hashes. This is how a golden is born and how we prove Docling itself is faithful.

```
Tier 1 (default):   golden DocLang  ──> DoclangValidator ──> structural assertions
Tier 2 (live):      source ──> Docling Serve ──> DocLang ──> validate ──> assert
                                                      └─(refresh)─> rewrite golden + hashes
```

Tier 2 points at a configurable endpoint (`fdi.qualification.doclingUrl`, default the
locally-run pinned CPU image) rather than spinning Docling up per run — the CPU image and
model load are too heavy for that. It is gated behind `-Dfdi.qualification.live=true`.

## Layout

The corpus is the source of truth and lives at the repository root, independent of which
Maven module ends up running it:

```
qualification/
  corpus/
    manifest.yaml                 # one entry per document (schema below)
    sources/                      # committed INPUT documents (synthetic / public-domain only)
      <id>.pdf
    golden/                       # committed OUTPUT artifacts produced by Tier 2
      <id>.doclang.xml
      <id>.dclx                   # only when DCLX was requested
    README.md                     # how to add a document
  external/                       # git-ignored: real, authorised evidence (runs Tier 2, never committed)
```

**Sensitivity rule.** Everything under `corpus/sources/` and `corpus/golden/` is committed,
so it must be synthetic or public-domain. Real evidence a user provides goes under
`external/`, which is git-ignored; it can drive Tier 2 live but is never committed and
never produces a committed golden. This follows the platform rule that fixtures are
synthetic and authorised real evidence is used only in development environments.

## Manifest schema

`manifest.yaml` is a list under `documents:`. Each entry:

| Field | Meaning |
|---|---|
| `id` | Stable slug; names the source and golden files. |
| `title` | Human description. |
| `class` | One of the provenance classes below. Drives coverage reporting. |
| `source.file` | Path under `corpus/sources/` (or `external/` for uncommitted). |
| `source.mediaType` | e.g. `application/pdf`. |
| `source.sha256`, `source.sizeBytes` | Integrity pin of the input. |
| `options` | The `ConversionOptions` used: `pipeline`, `formats`, `doOcr`, `forceOcr`, `ocrLanguages`, `vlmPreset`, `imageMode`, `firstPage`/`lastPage`, `imageScale`, `enrichments`, `stagePresets`. |
| `expect.validation` | `valid`, or a list of expected `ValidationReport` finding codes. |
| `expect.structure` | Structural assertions over the DocLang (below). |
| `golden.doclang`, `golden.dclx` | Golden paths + their sha256; written/verified by Tier 2. |

### Structural assertions (`expect.structure`)

Assertions are deliberately coarse and provenance-focused — enough to catch "Docling
dropped the table" without pinning brittle exact output:

- `minHeadings`, `minTables`, `minPictures`, `minFieldItems`, `pageBreaks` — minimum
  counts of the corresponding DocLang elements.
- `containsText` — a list of substrings that MUST appear verbatim in element text
  (e.g. an amount, a name, a passport number). This is the core "did the content
  survive" check.
- `absentText` — substrings that must NOT appear (e.g. struck-out text that should carry
  `strikethrough`, or a redaction that must not have been OCR'd through).
- `layers` — layer values that must be present (`body`, `furniture`, `background`).
- `derivedFields` — whether `description`/`summary` are expected present or absent, so we
  know whether Docling is emitting model-written prose for this profile.
- `table[].cells` — for a named table, expected (rowHeader, colHeader, value) triples, to
  prove the cell grid is preserved.

### Provenance classes (coverage target)

The corpus is "done enough to trust" when each class has at least one passing document:
`born_digital_pdf`, `scanned_ocr`, `table_heavy`, `form_keyvalue`, `multi_page`,
`multilingual`, `handwriting`, `figures_captions`, `mixed_struck_redacted`.

### Corpus direction

- **First test case: the Epstein public court files.** Redacted, scanned, multi-page
  public-domain court records — they exercise `scanned_ocr`, `multi_page` and
  `mixed_struck_redacted` at once, and they match the platform's evidence-extraction
  showcase. Being public-domain, they may be committed under `corpus/sources/`.
- **Target document types** (the hard cases we must not drop): 401(k) and brokerage
  statements, letters/certificates of incorporation, and similarly **complex, heavily
  table-driven** financial and legal documents. These drive the `table_heavy`,
  `form_keyvalue` and `figures_captions` classes, with `table[].cells` assertions proving
  the cell grid survives.

### Redaction handling (first-class for the Epstein set)

Redacted documents get two extra, non-negotiable checks:

- `absentText` must list content behind redaction boxes — proving OCR did **not** read
  through a redaction.
- The DocLang must not emit a `description`/`summary` that re-identifies a redacted
  subject. This enforces the platform evidence principle "never re-identify redacted
  victims": the pipeline categorises, it does not reconstruct a redacted identity.

## Harness behaviour

A data-driven test iterates `documents:`:

- **Tier 1 (default):** for each entry, load `golden.doclang`, assert its sha256 matches
  the manifest, validate it, assert `expect.structure`. Entries with no golden yet are
  reported as *pending* (not failures), so the suite is green on an empty corpus and the
  coverage report shows what's missing.
- **Tier 2 (`-Dfdi.qualification.live=true`):** submit `source` with `options`, poll to
  terminal, `retrieveAndStore`, validate, assert `expect.structure`. With
  `-Dfdi.qualification.refresh=true`, write the DocLang/DCLX under `golden/` and update
  the manifest hashes. Without refresh, diff the live output against the committed golden
  and fail on drift (this is the Docling-version-drift alarm).

The harness is created as a new module, `forwardmeasure-document-intelligence-
qualification`, depending on `-core` and `-validation`. It is NOT built until the first
real document lands, so its dependencies are real and it compiles green from day one.

## Adding a document (the workflow when you hand me one)

1. Drop the file in `corpus/sources/` (synthetic/public-domain) or `external/` (real).
2. Add a manifest entry: `id`, `class`, `source`, `options`, and `expect` — the
   `containsText` list is what makes the test meaningful, so it must name specific content
   you know is in the document.
3. Run Tier 2 with `--refresh` to produce the golden and fill in hashes.
4. Eyeball the golden once; thereafter Tier 1 guards it for free.

## Open decisions

- **Module placement:** confirm `forwardmeasure-document-intelligence-qualification` as a
  new reactor module (vs. folding Tier 1 into `-validation` test resources and Tier 2 into
  `-core` tests). Recommendation: the dedicated module, so the corpus stays in one place.
- **Live endpoint:** local pinned CPU image vs. the cluster's Docling Serve for Tier 2.
