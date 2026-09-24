# Architecture

## Bounded context

One context, **ExpenseCapture**: turning captured documents into expense transactions. No other domain
(invoicing, inventory, travel) is modelled.

## Aggregates


| Aggregate   | Root          | Members                       | Why                                                                                                                     |
| ----------- | ------------- | ----------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Receipt     | `Receipt`     | —                             | A receipt exists before any transaction is derived from it, so it is a root in its own right and referenced by id only. |
| Transaction | `Transaction` | `Tax` (1:N), `LineItem` (1:N) | Taxes and items have no lifecycle or meaning outside their transaction and are never queried independently.             |


Consequence: **two repositories, not four.** `Tax` and `LineItem` are persisted only through the
`Transaction` root; there is deliberately no `TaxRepository`. Taxes are child records rather than a single
`taxAmount` column, because a receipt can legitimately carry several rates.

`Money` is an immutable value object (`BigDecimal` scale 2, `HALF_UP`, currency-checked arithmetic).
`double` is never used: `4.00 + 6.00 + 1.90 == 11.90` is not reliably true in binary floating point, and
`BigDecimal.equals` would call `2.85` and `2.850` different, so comparison is by `compareTo`.

```
Receipt ──id──> Transaction (root)
                 ├── grandTotal, printedSubtotal, merchant, date, currency
                 ├── lineItemBasis, itemizeStatus, reconciliationGap
                 ├── rawOcrText            <- source of truth for re-itemize
                 ├── taxes:     [Tax, ...]
                 └── lineItems: [LineItem, ...]
```



## The central design decision: one invariant, two strengths

Reconciliation (`sum(items) + sum(taxes) == grand_total`) is enforced differently depending on **who** caused
the inconsistency:

```
machine extraction that does not reconcile  ->  200, persisted, NEEDS_REVIEW   (soft)
human override that does not reconcile      ->  409, nothing written           (hard)
```

A machine guess is *evidence*: keeping it — flagged — is what lets a person correct it, and destroying it
would destroy the only record of what the receipt actually said. A human edit is an *assertion*: if it is
arithmetically false, the right answer is to refuse the write and report the gap.

The corollary, and the rule the fixtures are really testing: **nothing fabricates data to make the maths
work.** No code path constructs a `LineItem` from a residual difference, and `grandTotal` is never recomputed
from the items. `ReconciliationService` reports the gap; it never closes it. A dedicated negative test
(`should_process_withMismatchedReceipt_notAddBalancingLineOrAlterTotal`) fails if that ever changes.

## Pipeline

```
POST /receipts            -> validate, sanitise filename, write to disk, persist Receipt
POST /receipts/{id}/process
     OcrProvider.extractText()          stub: fixture text or the uploaded text
  -> ReceiptTextParser.parse()          header, taxes (printed amount first), items, basis
  -> ReconciliationService.evaluate()   COMPLETE | NEEDS_REVIEW | FAILED (+ gap)
  -> TransactionRepository.save()       raw OCR text stored here
POST /transactions/{id}/itemize
  -> re-parse items from stored rawOcrText, replace items only, re-evaluate
PATCH /transactions/{id}/items
  -> evaluate first; reconciles ? save : throw 409 (zero writes)
```

`process` is idempotent: it looks the transaction up by `receiptId` and updates in place, so the id stays
stable and a second call cannot create a duplicate.

`itemize` reads `rawOcrText` from the aggregate — not the file, not the OCR provider — which is why the raw
text must be persisted.

### Why `itemize` exists when the OCR text never changes

The two write paths differ only in whether OCR runs, and that is the point:


|                                   | Re-runs OCR     | Cost          | Purpose                                 |
| --------------------------------- | --------------- | ------------- | --------------------------------------- |
| `POST /receipts/{id}/process`     | Yes, every call | A vendor call | Refresh everything from the stored file |
| `POST /transactions/{id}/itemize` | **Never**       | Free          | Re-parse the text already stored        |


It is not a no-op. Because it replaces line items with the parser's current output, it acts as *undo* for a
user's edit: after a `PATCH` merges three items into one, `itemize` restores the machine's three. In
production its real value is re-applying an **improved parser** to existing receipts without paying for OCR
again — vendor OCR is the expensive, rate-limited, sometimes non-deterministic half, while parsing is pure and
cheap. That is the same seam that keeps `OcrProvider` out of `ReceiptTextParser`; this endpoint is what it buys.

Measured on `receipt-clean`: `process` → +1 OCR invocation, `itemize` → +0, transaction id unchanged.

### Known limitation: line items have no provenance

Both `process` and `itemize` replace line items outright, so they silently discard a user's `PATCH` edits. This
follows the brief (*"Replace line items only"*), and is accepted here, but the underlying gap is that a
`LineItem` does not record whether the parser proposed it or a human asserted it. Nothing in the model can
therefore protect one from the other, and a human-confirmed `COMPLETE` is indistinguishable from a
machine-guessed `COMPLETE`.

A production design would add `LineItemSource {AUTO, USER}` and either preserve `USER` items across
re-itemize or reject the call unless explicitly forced. Deliberately out of scope for this exercise.

### Line item identity across an override

The brief names three verbs — *edit / merge / split* — without prescribing a mechanism. All three are served by
one full-replacement payload, with identity opt-in per row:


| Verb      | Client sends                           | Identity                                                 |
| --------- | -------------------------------------- | -------------------------------------------------------- |
| **edit**  | same count, existing `item_id` per row | ids preserved — a real edit, not a recreate              |
| **merge** | fewer rows, no `item_id`               | one new id; the combined row is not any of the originals |
| **split** | more rows; may keep one `item_id`      | that row keeps its id, the rest are new                  |


Two guards make this safe. An `item_id` the transaction does not own is a `400`, not a silent new item —
otherwise patching the wrong transaction would succeed and look correct. The same `item_id` twice is also a
`400`, since two rows claiming one identity would collapse a split into an edit. Both checks run *before*
reconciliation so that an identity error is never reported as a `409` mismatch.

`OverrideKind` is derived from the count change rather than declared in the request: the client already states
its intent by sending the list, and a declared verb could contradict it, forcing the server to choose which to
believe.

Before this, every write minted fresh ids, so a PATCH with byte-identical content regenerated all of them and
the published `item_id` was an identifier no client could rely on or even reference.

## Deliberate simplifications


| Choice                                                        | Rationale                                                                                                                                                                                                                                                                                                                       | Trade-off accepted                                                                                                                                                     |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PATCH` takes the full replacement list                       | Merge (fewer items), split (more items) and edit (changed amounts) are all the same operation, so one code path serves all three. YAGNI over an operation DSL. An optional `item_id` per row makes an edit preserve identity rather than destroy-and-recreate, so the brief's three verbs are honoured without three endpoints. | No partial patch: the client always sends every row. The override verb is derived from the count change rather than declared, so a same-count reorder reads as `EDIT`. |
| In-memory repositories behind interfaces                      | Permitted by the brief; keeps the focus on the data model.                                                                                                                                                                                                                                                                      | Not durable. Swapping in JDBC requires no change above the repository interfaces.                                                                                      |
| `OcrProvider` interface with one implementation               | Two real implementations exist in principle (stub now, vendor later) and the brief explicitly anticipates the swap, so the seam is justified rather than speculative.                                                                                                                                                           | One extra indirection.                                                                                                                                                 |
| Line-oriented regex parser                                    | Three fixtures with a consistent layout; a general-purpose OCR parser would be over-engineering.                                                                                                                                                                                                                                | Brittle against unseen layouts. Non-item labels (`Subtotal`, `TOTAL`, `VAT`, tips, change) are explicitly excluded.                                                    |
| Hand-rolled health indicators instead of Spring Boot Actuator | Actuator's real value is its ecosystem of ready-made indicators (DataSource, Redis, disk). With three trivial checks and no datastore, it would add a dependency and endpoint surface to replace ~80 lines.                                                                                                                     | A close call. With a datastore, Actuator would be the right choice.                                                                                                    |
| Java 17, not 21                                               | Only JDK 17 was installed on the build machine. Every feature used (records, text blocks, sealed-free design) exists in 17.                                                                                                                                                                                                     | None functionally.                                                                                                                                                     |




## Cross-cutting

- **Health** — two endpoints, `/health/live` and `/health/ready`. Liveness is dumb and cheap; readiness checks
infrastructure prerequisites only (storage and OCR here; caches, datastores and brokers in a larger
service). The split exists because an orchestrator *restarts* on liveness failure but only *withdraws
traffic* on readiness failure — checking dependencies in liveness converts a 5-second dependency blip into a
fleet-wide restart storm with no warm capacity left. Ordinary application components are deliberately not
probed: they are wired at startup and cannot fail independently at runtime, so every registered
`HealthIndicator` is a genuine prerequisite and no optional flag is needed. Each indicator times itself and
catches its own failures, so one broken dependency can neither 500 the probe nor mask the others' state.
Status-to-HTTP mapping is a separate `HealthStatusMapper`, keeping status branching out of the controller.
- **Errors** — `ReceiptException` base carrying `code` / `message` / `httpStatus`; four subclasses; a single
`@RestControllerAdvice` renders the standard body and never leaks a stack trace.
- **Logging** — 3-part format `Expense Capture | <message> | <identifier>`, with dotted identifiers for
nesting (`R-…​.T-…​`). L1 milestones are prefixed `Event :`; reconciliation gaps log at `WARN`.
- **Correlation** — `X-Correlation-ID` accepted or generated per request, placed in the MDC and removed in a
`finally` block so nothing leaks across pooled threads.
- **Config** — typed `AppProperties` with `@Validated`; the upload directory is created and write-checked at
startup, and an unsupported OCR provider is fatal. Fail fast, never silently default.
- **Security** — input validation at the boundary; filenames sanitised into a `StoredFilename` value object so
a traversal name like `../../etc/passwd` cannot reach `Path.resolve`, and the type makes that guarantee
unbypassable by a later caller; uploaded bytes checked against their declared content type by magic number,
because `Content-Type` is client-supplied and trivially spoofed; size limits; baseline security headers on
every response; no secrets anywhere in the repo. Auth, CORS and rate limiting are out of scope per the brief
(the perimeter-gateway model assumes a gateway that does not exist here).



## Concurrency

Every HTTP request is served on its own thread, so the one-transaction-per-receipt rule cannot be enforced by
the caller. `findByReceiptId(...).orElseGet(create)` is a check-then-act race: two concurrent `process` calls
both find nothing and both create. `ConcurrentHashMap` makes each *operation* atomic but not a *sequence* of
them, so the invariant is enforced inside the repository via `findOrCreateByReceiptId`, whose
`computeIfAbsent` mapping function runs at most once per key. `ConcurrentProcessingIT` fires 16 simultaneous
`process` calls at one receipt and asserts exactly one transaction results; it fails against the naive version.

Known limitation, accepted for this exercise: two concurrent `process` calls for the same receipt then mutate
the *same* `Transaction` instance. Both write identical extracted values, so the stored result is correct, but
a strictly safe version needs per-receipt serialisation or an immutable aggregate swapped atomically.

### Concurrent overrides on one transaction

The same aliasing affects `PATCH /transactions/{id}/items`, and there it is not benign. `findById` returns the
stored instance itself, not a copy, so two overrides interleave inside one mutable aggregate. Measured over 40
rounds of a simultaneous merge (3 items → 1) and split (3 → 2):

```
rounds=40  corrupted=1  rejected=0
corrupted shape: size=3 sum=30.00
```

One run in forty produced **three items summing to 30.00** — both writers' rows, double the true total of 15.00,
a state neither client asked for. It persists as `COMPLETE` because each writer reconciled against the list it
built before the other interleaved. So the hard invariant — *nothing false is ever stored* — is guaranteed only
for a single writer.

`overrideLineItems` narrows the window by making replace-plus-status one call rather than two, but does not
close it: read, reconcile and write are still three separate steps.

Fixing it properly is a version check, not a lock:

```java
// aggregate carries a version; save() rejects a stale write
if (stored.getVersion() != expectedVersion) throw new ConcurrentModificationException();
```

That returns `409` to the loser — which is exactly the semantics the brief already demands for a non-reconciling
override, so it fits the existing contract. Out of scope here: the brief specifies no auth, no sessions and a
single user, so two simultaneous editors of one receipt cannot arise in the reviewed scenario. Recorded rather
than left as an unstated surprise.

## Upload validation

Rules live behind `UploadValidator` and are run by `UploadValidationChain`, which Spring injects already
sorted by `@Order`. Adding a rule means adding a class — no edit to the chain or to `ReceiptService` (OCP),
which `NewValidatorExtensibilityTest` demonstrates with a rule that did not exist when the chain was written.

Two details carry the design:

- **Cheap-first ordering.** Metadata rules (`ORDER_METADATA`) run before content inspection
(`ORDER_CONTENT`), so an oversized payload is rejected without anything reading its bytes. A unit test
proves the chain honours the order it is given; `UploadValidationWiringIT` proves the order Spring *gives*
it is correct, which is the half a unit test cannot cover.
- **One read of the payload.** `ReceiptUpload` reads the leading bytes once behind `mark`/`reset` and caches
them, so validators are pure functions over that object and do no I/O. Letting each validator call
`getInputStream()` would mean N passes and would rely on `MultipartFile` being re-readable, which it does
not guarantee. The stream stays positioned at zero for the file store.

Security ordering note: the declared `Content-Type` check is a cheap filter, not a control — it is what
`FileContentValidator` verifies against the magic number that makes the claim trustworthy.

## Why parsing and OCR stay separate

`OcrProvider` is an interface; `ReceiptTextParser` is not. The split follows what varies:

- **OCR varies by file type and vendor** — PDF, PNG and JPEG differ in how text is obtained, but all yield a
`String`. A new format or vendor is a new `OcrProvider`, selected by `app.ocr.provider`; the parser is
untouched. Verified by `OcrProviderSelectionIT`, which registers a second provider and asserts it is the one
injected. (Before selection existed, a second implementation broke startup outright with `expected single matching bean but found 2` — the extension point was documented but non-functional.)
- **Parsing varies by nothing** — it is a pure function `String -> ExtractionResult`: no I/O, no clock, no
network. There is no seam worth stubbing and no second implementation in prospect, so an interface over it
would add a file and buy nothing. Its 11 tests use no mocks.

Folding `OcrProvider` into the parser would also break a brief requirement:
`POST /transactions/{id}/itemize` must re-itemize from **stored** OCR text and must not call OCR again.
Because retrieval lives outside the parser, `ItemizeService` parses `transaction.getRawOcrText()` directly —
confirmed live, a re-itemize performs zero OCR invocations.

## Why reconciliation has no interface, and no facade

`ReconciliationService` is a pure function `ReconciliationRequest -> ReconciliationOutcome`. Its only
collaborator is the configured tolerance — policy, not a replaceable service — so there is no seam worth
abstracting and its tests use no mocks. An interface would add a file and buy nothing.

A facade was considered and rejected: the complexity was not *hidden* behind too few types, it was *exposed* by
a bad signature. `evaluate` took five positional parameters, four of which callers pulled off the same
`Transaction`, and the fifth (`headerUsable`) was hardcoded `true` by two of three callers — so a transaction
with no merchant and no date was reported `COMPLETE` by re-itemize and by override. A facade would have wrapped
that bug, not removed it.

The fix moves the decision to the only object that can answer it: `Transaction.hasUsableHeader()`, with the
rule defined once in `ExtractedFields.isUsableHeader` so the extraction and the stored aggregate cannot drift.
`ReconciliationRequest.forExtraction` / `forTransaction` then make the flag unsuppliable by callers — the
mistake is now unavailable rather than merely documented.

## Raw OCR is stored, not published

The brief requires raw OCR text to be *stored* and *used as the source for re-itemize* — not returned. It was
briefly exposed via `?include=raw_ocr`, which was removed: receipt text can run to kilobytes and may carry
names, addresses or partial card numbers, so data minimisation argues for keeping it internal.

Removing it also deleted a boolean-parameter smell. `TransactionResponse.from(transaction, includeRawOcr)` was
called from four places and **three passed a literal** `false` — the same shape as the `headerUsable=true`
defect above, where a flag the caller should not have owned produced a wrong answer. `from(transaction)` now
takes one argument, and with `default-property-inclusion: always` every response stops carrying a permanently
null `raw_ocr_text`.

Persistence is still asserted, just not through HTTP: `GoldFixtureIT` reads the stored aggregate directly and  
checks the text, the provider, and that `ocr.text_length` matches what was actually stored. Re-itemize returning  
correct items with zero OCR invocations is the end-to-end proof that the stored text is real and usable.

## Tests

150 tests: 107 unit (Surefire, `*Test`) + 43 integration (Failsafe, `*IT`, `@SpringBootTest` + `MockMvc`).
The integration suite asserts against `fixtures/task-a/gold.json` directly, so the acceptance criteria are
executable. Tests that would fail if the logic were gutted include: the printed-vs-derived tax assertions
(1.90 not 2.95; 3.83 not 4.56), the no-balancing-line assertion, the idempotency assertion, the
409-writes-nothing assertion, the concurrent one-transaction-per-receipt assertion, the spoofed-content-type
rejection, and the readiness assertions that a broken prerequisite yields 503 while liveness stays 200.