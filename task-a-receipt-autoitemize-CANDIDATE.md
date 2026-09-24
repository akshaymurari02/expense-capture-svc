# Take-home A � Receipt upload, taxes, auto-itemize

**Deadline:** 24 hours from when you receive this assignment. You do not need to start the moment the email arrives.

**Expected effort:** around 60�120 minutes of work. A working slice on the fixtures beats an unfinished platform.

**You complete only this task.** Do not build invoicing, inventory, or travel APIs.

Language and framework are your choice. We care that the API runs and the data model is right.

---

## Using an LLM (allowed)

You **may** use ChatGPT, Cursor, or any other LLM **to help you write the code**. Paste this brief and the fixtures into your local tools.

You **do not** need to call an OCR or LLM API from the running service. Reading the fixture text and stubbing OCR is enough. Matching `gold.json` on the fixtures is the bar � not a production OCR vendor.

If you *do* call a vendor (OpenAI, Gemini, Vision, etc.):

- Put the key in an **environment variable** (or a `.env` file that is **gitignored**).
- README: how to set the variable, then run.
- **Do not commit API keys**, tokens, or credential files. A placeholder such as `YOUR_KEY` is fine. A real key in the repo is an automatic fail.

We will not ask you for your key. Scoring uses your README curls and the fixtures.

---

## Product context

A user uploads a **receipt**. The system:

1. Creates **one transaction** from that receipt.
2. Parses and **stores taxes** as their own records (not a single tax number on the header).
3. **Auto-itemizes**: splits the receipt into several **line items**, the way expense auto-itemize works today (the model proposes items from the receipt; the user can still edit them).

If line items do not add up to the receipt total, keep the transaction and mark itemization as needing review. Do **not** invent a fake line to force the math to work.

---

## What to build

A small HTTP API. In-memory storage or SQLite is enough. No login, no UI, no PDF.

### Endpoints

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/receipts` | Multipart upload of an image or PDF. Store the file (local disk is fine). Return a `receipt_id`. |
| `POST` | `/receipts/{id}/process` | Run OCR + extraction. Create/update the **transaction**, **tax lines**, and **auto-itemize line items**. Persist **raw OCR text** as well as structured fields. |
| `GET` | `/transactions/{id}` | Return the transaction with its taxes and line items, plus `itemize_status`. |
| `POST` | `/transactions/{id}/itemize` | Re-run auto-itemize from **stored OCR**. Replace line items only. Do not create a second transaction. |
| `PATCH` | `/transactions/{id}/items` | User override: edit / merge / split items. If amounts no longer reconcile with the transaction total (and stored taxes), return **409** with the mismatch. Do not silently �fix� totals. |

`GET /health` is welcome.  - Done reviewed completely

You may use a real OCR/VLM API, or treat fixture files as already-known images and extract from provided OCR text in `/fixtures`. If you stub OCR, say so in the README. Stubbing is a complete solution for this exercise.

### Data we expect to exist after `process`

**Transaction (header)**  
merchant/supplier, date, currency, grand total, link to `receipt_id`.

**Taxes (list)**  
Each row: `name` (e.g. VAT, GST), `rate`, `amount`. Optional jurisdiction if you have time.

**Line items (auto-itemize)**  
Each row: `description`, `amount`, optional `tax_amount` / quantity.

**Itemize status**  
`COMPLETE` | `NEEDS_REVIEW` | `FAILED` (or equivalent names).

**Raw OCR**  
Stored and used as the source for re-itemize.

---

## Fixtures

Use the files in `fixtures/task-a/` (shipped with this brief):

| File | What it tests |
|---|---|
| `receipt-clean.txt` | Happy path: items + VAT |
| `receipt-tax-only.txt` | Tax present, no useful line items |
| `receipt-mismatch.txt` | Line items do not sum to total |

`gold.json` is the expected shape of extraction for those three. You do not need a pixel-perfect OCR engine; matching the gold fields on fixtures is enough.

---

## Out of scope

Auth, UI, policy/compliance, ledgers, invoices, inventory, flights/hotels/rail, production file storage.

---

## Submit

1. Git repo URL + commit SHA.
2. `README.md`: how to run in one command, plus **example curls** for every endpoint.
3. Note which model/OCR vendor you used, **or that OCR is stubbed** (stub is fine).
4. Optional if time: half-page `ARCHITECTURE.md`.

A working vertical slice on the three fixtures beats an unfinished �platform.�
