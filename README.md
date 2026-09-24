# Expense Capture

A small HTTP API that turns an uploaded receipt into one expense transaction with its own tax records and
auto-itemized line items — and is honest when the numbers do not add up.

**OCR is stubbed.** No vendor is called and no API key is required. The fixture `.txt` files in
`fixtures/task-a/` are treated as already-known OCR output. See [OCR](#ocr-stubbed) below.

- Java 17 · Spring Boot 3.3 · Maven
- In-memory storage; uploaded files go to local disk
- 150 tests (107 unit + 43 integration), all green against `fixtures/task-a/gold.json`
- [Postman collection](postman/) — 33 requests, ~340 assertions, verified against a running server

---

## Run

```bash
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. To use another port (8080 is a common conflict):

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8099
```

Or build and run the jar:

```bash
mvn clean package
java -jar target/expense-capture-1.0.0.jar --server.port=8099
```

### Tests

```bash
mvn verify
```

> **Note on this machine:** Maven is installed as `mvn3`, and the shared `~/.m2` is used by other projects.
> All commands above were verified with an isolated local repository so nothing shared is touched:
> ```bash
> mvn3 -Dmaven.repo.local=./.m2repo verify
> ```

---

## Endpoints

| Method | Path | Behaviour |
|---|---|---|
| `POST` | `/api/v1/receipts` | Multipart upload. Stores the file, returns `receipt_id`. |
| `POST` | `/api/v1/receipts/{id}/process` | Stubbed OCR + extraction. Creates/updates the transaction, tax rows and line items. **Idempotent.** |
| `GET` | `/api/v1/transactions/{id}` | Transaction with taxes, line items and `itemize_status`. |
| `POST` | `/api/v1/transactions/{id}/itemize` | Re-runs auto-itemize from **stored** OCR. Replaces line items only. |
| `PATCH` | `/api/v1/transactions/{id}/items` | User edit / merge / split. **409** if amounts no longer reconcile. |
| `GET` | `/health/live`, `/health/ready` | Liveness and readiness. |

All examples below use port `8099`.

### Postman

`postman/expense-capture.postman_collection.json` — 33 requests, ~340 assertions, verified green against a
running server. Import it, set `baseUrl` if you are not on `8099`, then **use Collection Runner on the whole
collection**.

No local files are needed: receipt content is embedded in each upload as a `multipart/form-data` body, so the
collection is portable. Run the folders in order — later requests reuse `receiptId`, `transactionId` and
`itemId1..3` captured by earlier ones, so a PATCH run in isolation fails with `EXP-VAL-003`.

| Folder | What it proves |
|---|---|
| 00 Health | Liveness has no dependencies; readiness reports storage and OCR |
| 01 Happy path | `receipt-clean` matches gold; process is idempotent; EDIT preserves `item_id`, MERGE mints a new one; re-itemize undoes the merge from stored OCR |
| 02 Tax only | Tax **3.83 read from the receipt**, not 24.00 × 0.19 = 4.56 derived |
| 03 Mismatch | Total stays **18.50** (not 11.90 or 10.00); no line equals the 6.60 residual |
| 04 Override conflict | 409 with the mismatch **and a follow-up GET proving nothing was written** |
| 05 Validation guards | Empty file, disallowed type, spoofed magic bytes, unknown/duplicate ids, malformed JSON |
| 06 Protocol errors | 405 with `Allow`, 415, 404, security headers, correlation-id echo |

Note: `POST /receipts/{id}/process` re-parses from scratch, so it mints new `item_id`s while keeping the same
`transaction_id`. The collection re-captures ids after each process and GET for that reason.

### 1. Health

Two endpoints. They are separate because a failing liveness probe and a failing readiness probe mean opposite
things to an orchestrator: liveness failure ⇒ **restart the container**, readiness failure ⇒ **withdraw
traffic, keep it running**. If liveness checked dependencies, a brief dependency outage would restart every
instance at once and leave no warm capacity when it recovered. Liveness is therefore deliberately dumb.

Readiness checks only **infrastructure prerequisites** — here storage and OCR; in a larger service also
caches, datastores and brokers. Ordinary application components are not probed: they are wired at startup and
cannot fail independently at runtime, so checking them adds noise without signal.

```bash
curl -s http://localhost:8099/health/live   # cheap; no dependency checks
curl -s http://localhost:8099/health/ready  # prerequisites; 200 UP/DEGRADED, 503 DOWN
```

`/health/live` → `{"status":"UP"}`

`/health/ready`:

```json
{
  "status": "UP",
  "service": "expense-capture",
  "version": "1.0.0",
  "profile": "default",
  "uptime_seconds": 11,
  "runtime": { "java_version": "17.0.12", "heap_used_mb": 29, "heap_max_mb": 6144 },
  "dependencies": {
    "storage": { "status": "UP", "response_time": 1, "reason": null,
                 "detail": { "upload_dir": "/app/data/uploads", "exists": true,
                             "writable": true, "free_space_mb": 304481 } },
    "ocr": { "status": "UP", "response_time": 0, "reason": null,
             "detail": { "provider": "stub", "fixture_dir": "./fixtures/task-a", "readable": true } }
  }
}
```

Break a prerequisite and the split becomes visible:

```bash
java -jar target/expense-capture-1.0.0.jar --server.port=8099 --app.ocr.fixture-dir=./nope

curl -s -w " %{http_code}\n" http://localhost:8099/health/live   # {"status":"UP"} 200  <- no restart
curl -s -w " %{http_code}\n" http://localhost:8099/health/ready  # DOWN 503             <- traffic withdrawn
#   ocr     -> DOWN  "fixture directory is not readable: .../nope"
#   storage -> UP                                                <- failures are isolated
```

Any prerequisite `DOWN` ⇒ `DOWN` + 503. `DEGRADED` (e.g. free disk below 100 MB) ⇒ 200, because an impaired
instance can still serve and removing it would only cost capacity.

Adding a cache or datastore later means adding one `HealthIndicator` implementation — no controller change.
Status-to-HTTP mapping lives in `HealthStatusMapper`, so the controller contains no branching on status.

### 2. Upload a receipt

```bash
curl -s -F "file=@fixtures/task-a/receipt-clean.txt;type=text/plain" \
  http://localhost:8099/api/v1/receipts
```

```json
{
  "receipt_id": "3a8122a1-5e61-4de7-9044-e1cf4b8a1de4",
  "original_filename": "receipt-clean.txt",
  "content_type": "text/plain",
  "size_bytes": 252,
  "uploaded_at": "2026-03-12T10:00:00Z"
}
```

### 3. Process it

```bash
curl -s -X POST http://localhost:8099/api/v1/receipts/<RECEIPT_ID>/process
```

```json
{
  "transaction_id": "fea53359-339f-4446-8fad-cff8326c580c",
  "receipt_id": "3a8122a1-5e61-4de7-9044-e1cf4b8a1de4",
  "merchant": "Cafe Mitte",
  "date": "2026-03-12",
  "currency": "EUR",
  "grand_total": 17.85,
  "printed_subtotal": 15.00,
  "line_item_basis": "NET",
  "taxes": [
    { "tax_id": "5e823b62", "name": "VAT", "rate": 0.19, "amount": 2.85, "jurisdiction": null }
  ],
  "line_items": [
    { "item_id": "61df32f7", "description": "Espresso",      "amount": 3.50 },
    { "item_id": "475dd3d7", "description": "Sandwich",      "amount": 8.90 },
    { "item_id": "6f712f93", "description": "Mineral water", "amount": 2.60 }
  ],
  "itemize_status": "COMPLETE",
  "reconciliation": { "expected": 17.85, "actual": 17.85, "difference": 0.00 },
  "ocr": { "provider": "stub", "text_length": 252 }
}
```

Calling `process` again returns the **same** `transaction_id` — one receipt, one transaction.

### 4. Read the transaction

```bash
curl -s http://localhost:8099/api/v1/transactions/<TRANSACTION_ID>
```

Raw OCR text is stored but deliberately not returned — it can be large and may contain personal data. The
response reports `ocr.text_length` as evidence it was persisted, and step 5 proves it is usable.

### 5. Re-run auto-itemize from stored OCR

```bash
curl -s -X POST http://localhost:8099/api/v1/transactions/<TRANSACTION_ID>/itemize
```

Line items are re-derived from the stored OCR text. The transaction id, header and taxes are unchanged, and
no second transaction is created.

### 6. User override — merge, split, edit

The body is the **complete replacement list**. A merge sends fewer items, a split sends more, an edit sends
changed amounts — one endpoint covers all three.

Identity is opt-in per row: supply an existing `item_id` to **edit** that item and keep its id, or omit
`item_id` for a genuinely new row (the product of a merge or split). An `item_id` that does not belong to the
transaction is rejected with `400 EXP-VAL-003` rather than silently treated as new. The server derives the verb
from the count change and logs it as `kind=EDIT|MERGE|SPLIT`.

Edit (keeps identity, amounts still reconcile):

```bash
curl -s -X PATCH http://localhost:8099/api/v1/transactions/<TRANSACTION_ID>/items \
  -H 'Content-Type: application/json' \
  -d '{"items":[
        {"item_id":"<ESPRESSO_ITEM_ID>","description":"Espresso","amount":3.00},
        {"item_id":"<SANDWICH_ITEM_ID>","description":"Sandwich","amount":9.40},
        {"item_id":"<WATER_ITEM_ID>","description":"Mineral water","amount":2.60}
      ]}'
```

Valid merge (3 items into 1 that still reconciles: 15.00 + 2.85 VAT = 17.85). No `item_id`, because the
combined row is not any of the originals:

```bash
curl -s -X PATCH http://localhost:8099/api/v1/transactions/<TRANSACTION_ID>/items \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"description":"Team lunch","amount":15.00}]}'
```

```json
{ "line_items": [ { "description": "Team lunch", "amount": 15.00 } ], "itemize_status": "COMPLETE" }
```

Invalid edit — amounts no longer reconcile, so **409** and nothing is written:

```bash
curl -s -X PATCH http://localhost:8099/api/v1/transactions/<TRANSACTION_ID>/items \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"description":"Lunch","amount":9.00}]}'
```

```json
{
  "timestamp": "2026-03-12T10:04:22.117Z",
  "status": 409,
  "error": "Conflict",
  "code": "EXP-CON-001",
  "message": "Line items do not reconcile with the transaction total",
  "path": "/api/v1/transactions/0c8df15d/items",
  "mismatch": {
    "expected": 17.85, "actual": 11.85, "difference": 6.00,
    "line_item_basis": "NET", "currency": "EUR"
  }
}
```

A follow-up `GET` still shows the original three items: the rejected request wrote nothing.

---

## Fixture results

All three fixtures reproduce `gold.json` exactly.

| Fixture | merchant | grand_total | taxes | line_items | itemize_status |
|---|---|---|---|---|---|
| `receipt-clean` | Cafe Mitte | 17.85 | VAT 0.19 / **2.85** | 3 items summing 15.00 | `COMPLETE` |
| `receipt-tax-only` | Berlin Taxi GmbH | 24.00 | VAT 0.19 / **3.83** | **none** | `NEEDS_REVIEW` |
| `receipt-mismatch` | Hotel Shop | **18.50** | VAT 0.19 / **1.90** | 2 items summing 10.00 | `NEEDS_REVIEW` |

`receipt-mismatch` reports `reconciliation.difference: 6.60`. It does **not** gain a balancing line and its
`grand_total` is **not** rewritten to 11.90 or 10.00.

```bash
# reproduce, for any fixture name
RID=$(curl -s -F "file=@fixtures/task-a/receipt-mismatch.txt;type=text/plain" \
  http://localhost:8099/api/v1/receipts | python3 -c 'import sys,json;print(json.load(sys.stdin)["receipt_id"])')
curl -s -X POST http://localhost:8099/api/v1/receipts/$RID/process | python3 -m json.tool
```

---

## Design decisions

### Tax amounts are read, never computed

All three fixtures print the tax amount, and `gold.json` expects those printed values. So the parser
**reads** them. Derivation is a fallback only, used when the receipt omits the amount:

1. printed amount (primary)
2. `printed_subtotal × rate`
3. `grand_total × rate / (1 + rate)`

This matters. Deriving from the gross total gives the right answer on two fixtures and the **wrong** answer
on the third:

| Fixture | `total × 0.19/1.19` | gold | |
|---|---|---|---|
| `receipt-clean` | 2.85 | 2.85 | ok |
| `receipt-tax-only` | 3.83 | 3.83 | ok |
| `receipt-mismatch` | **2.95** | **1.90** | wrong |

`receipt-mismatch` prints VAT 1.90, which belongs to its printed `Subtotal 10.00` while `TOTAL` says 18.50.
That contradiction is the fixture's whole purpose, so any derive-first design fails it.

### `line_item_basis`, not "inclusive vs exclusive receipt"

The grand total is the gross amount paid — tax included — on every fixture (`17.85 / 1.19 = 15.00` exactly).
So "inclusive vs exclusive receipt" is not a real distinction. What changes the arithmetic is the basis of the
**line items**, detected by comparing their sum against the printed subtotal and total:

```
NET     sum(items) + sum(taxes) == grand_total    # receipt-clean, receipt-mismatch
GROSS   sum(items)              == grand_total
UNKNOWN no items parsed; reconciliation does not apply   # receipt-tax-only
```

Line items are stored **net**, matching `gold.json` (items sum to the printed subtotal, not the total).
Comparison tolerance is `app.reconciliation.tolerance` (default `0.01`), not a hardcoded literal.

### Machine mismatch is kept; human mismatch is rejected

Same inconsistency, two deliberate responses:

```
machine extraction that does not reconcile  ->  200, persisted, NEEDS_REVIEW
human override that does not reconcile      ->  409, nothing written
```

A machine guess is *evidence* — keep it so a person can fix it. A human edit is an *assertion* — if it is
arithmetically false, refuse it. Nothing anywhere fabricates a balancing line or adjusts a total.

### `receipt-tax-only` yields no line items

`gold.json` expects `line_items: []`, and permits a fallback item equal to the total only if the status stays
`NEEDS_REVIEW`. The fixture prints `Trip fare` with no amount beside it, so a parser that requires both a
description and an amount naturally produces zero items. The fallback is deliberately **not** used:
inventing an amount that was never printed would contradict the brief's central rule.

---

## OCR (stubbed)

No OCR or LLM vendor is called, and no API key is needed. `StubOcrProvider` resolves text by:

1. the uploaded filename's stem, e.g. `receipt-clean.png` → `fixtures/task-a/receipt-clean.txt`;
2. otherwise the uploaded file itself, when it is decodable UTF-8 text;
3. otherwise `422 EXP-EXT-001` — never a silent empty result.

Both paths sit behind the `OcrProvider` interface, so a real provider (Vision, Textract, a VLM) could be
added without touching any service.

---

## Configuration

Every value is overridable by environment variable and validated at startup — the service refuses to boot on
bad config rather than silently defaulting. No secrets are required by this service.

| Key | Env var | Default |
|---|---|---|
| `app.storage.upload-dir` | `APP_STORAGE_UPLOAD_DIR` | `./data/uploads` |
| `app.storage.max-file-size` | `APP_STORAGE_MAX_FILE_SIZE` | `10MB` |
| `app.ocr.provider` | `APP_OCR_PROVIDER` | `stub` — matched against each provider's `providerName()`; an unknown value fails startup and lists the valid ones |
| `app.ocr.fixture-dir` | `APP_OCR_FIXTURE_DIR` | `./fixtures/task-a` |
| `app.reconciliation.tolerance` | `APP_RECONCILIATION_TOLERANCE` | `0.01` |
| `server.port` | `SERVER_PORT` | `8080` |

## Error codes

| Code | HTTP | Meaning |
|---|---|---|
| `EXP-VAL-001` | 400 | Uploaded file missing or empty |
| `EXP-VAL-002` | 400 | Unsupported content type or size exceeded |
| `EXP-VAL-003` | 400 | Invalid item override payload |
| `EXP-VAL-004` | 422 | Receipt text could not be parsed |
| `EXP-VAL-005` | 422 | No stored OCR text for re-itemize |
| `EXP-VAL-006` | 400 | Request body missing or not valid JSON |
| `EXP-VAL-007` | 405 | HTTP method not supported (`Allow` header lists valid ones) |
| `EXP-VAL-008` | 415 | `Content-Type` not supported |
| `EXP-VAL-009` | 400 | File content contradicts its declared type (magic-byte check) |
| `EXP-NOT-001` | 404 | Receipt not found |
| `EXP-NOT-002` | 404 | Transaction not found |
| `EXP-NOT-003` | 404 | No such endpoint |
| `EXP-CON-001` | 409 | Line items do not reconcile with the total |
| `EXP-EXT-001` | 422 | OCR could not produce text |
| `EXP-INT-001` | 500 | Unhandled internal error |

## Out of scope

Auth, UI, PDF generation, real OCR vendor, invoices, inventory, travel, production file storage — all
excluded per the brief.

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the aggregate design and the reasoning behind the
soft/hard invariant split.
