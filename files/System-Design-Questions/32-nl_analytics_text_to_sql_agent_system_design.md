# Natural-Language Analytics / Text-to-SQL Agent — System Design

**Difficulty:** Advanced (agentic AI)
**Interview importance:** ⭐ High — "let users query the data warehouse in English" is a top enterprise ask; tests schema grounding, safety on a data store, and self-correction.
**Companion:** `files/Agentic-AI/` (Ch 4 tools, Ch 6 RAG for schema, Ch 7 self-correction, Ch 16 guardrails)

---

## 0. Why This Design Matters

Text-to-SQL is deceptively dangerous: the agent generates **executable queries against a real database** from **untrusted English**, and a wrong query is either a **silently incorrect number** (worse than an error — people trust it) or a **destructive/expensive operation**. It's the perfect test of *grounding* (the model must know the schema), *safety* (read-only, validated, bounded queries), and *verification* (is this number right?). The weak answer is "send the question + schema to an LLM and run whatever it returns." The strong answer grounds to the schema, **validates and sandboxes the SQL**, and self-corrects on error.

> Thesis: **ground the model in the real schema, generate SQL, then validate + run it read-only on a replica with limits — and return the SQL so a human can audit the number.**

---

## 1. Problem Overview — in Plain English

Build an agent where a non-technical user asks a question in English ("What was our churn rate for the enterprise plan last quarter, by region?") and the agent generates the SQL, runs it against the analytics database, and returns the answer — ideally with a chart and the SQL shown for transparency.

**Analogy — a data analyst who knows the warehouse cold.** They know which tables and columns exist, write a careful query, sanity-check the result, and hand you both the number *and* the query so you can trust it. Our agent must reproduce that — and the "knows the schema cold" part is the crux: an analyst who guesses column names is useless, and so is an ungrounded LLM.

```mermaid
flowchart LR
    Q[English question] --> A[[Text-to-SQL agent]]
    A -->|needs schema| S[("Schema / table docs")]
    A -->|generate + validate SQL| V{"safe & valid?"}
    V -->|yes| DB[("Read replica, read-only role")]
    DB --> R["Answer + SQL + chart"]
    V -->|no / error| A
```

---

## 2. Functional Requirements

**Core**
- Accept a natural-language analytics question.
- **Ground to the schema** (tables, columns, types, relationships, semantics).
- **Generate SQL** for the target dialect.
- **Validate** the SQL (parse, read-only, safe) and **execute** it.
- Return the **answer + the SQL** (transparency) and optionally a **chart**.
- **Self-correct** if the query errors or returns something implausible.

**Optional / advanced**
- Multi-turn refinement ("now break that down by month").
- Semantic layer / metric definitions (so "churn" means one agreed thing).
- Row-level security per user; caching of common queries; export.

**Non-goals (safety):** no writes/DDL (`INSERT/UPDATE/DELETE/DROP/ALTER`); no unbounded full-table scans; no querying tables the user isn't authorized to see.

---

## 3. Non-Functional Requirements

| Requirement | Target | Why |
|---|---|---|
| **Schema grounding** | Never reference a non-existent table/column | Ungrounded SQL = errors or wrong data |
| **Safety** | Read-only, validated, bounded, authorized | It runs SQL on a real DB |
| **Answer correctness** | Right number, or an honest "can't answer" | A wrong number is trusted and acted on — worst outcome |
| **Latency** | Seconds; long queries handled async | Interactive analytics |
| **Cost** | Bounded; cheap for repeat questions | LLM calls + query compute |
| **Transparency** | Always expose the generated SQL | Auditability / trust |

---

## 4. Cost / Capacity Estimation

(Illustrative.) The two cost centers are **LLM tokens** and **database compute**.

- **Schema is the token problem.** A big warehouse has thousands of columns — you **cannot** paste the whole schema into context. Retrieve only the **relevant tables** (a schema-RAG step) → keeps prompts small and accurate.
- **Query compute is the DB problem.** A careless generated query can scan billions of rows. Bound it: **row/scan limits, query timeout, cost guardrails**, and run on a **read replica** so analytics can't affect production.
- **Cache** results for identical/common questions; **prompt-cache** the stable system prompt + retrieved-schema prefix.

---

## 5. Tool / API Design

```jsonc
// Schema grounding (read)
search_schema(question)      -> relevant tables/columns + descriptions (schema-RAG)
describe_table(table)        -> columns, types, keys, sample distinct values
// Validate + execute (safety-critical)
validate_sql(sql)            -> { ok, is_read_only, est_cost/rows, error? }   // parse + policy check
run_sql(sql)                 -> rows (executed on read-replica, read-only role, LIMIT enforced)
// Present
make_chart(rows, spec)       -> chart (via a code-execution/plot tool)
```

**Design rules:**
- The agent **must not** hold raw DB creds. `run_sql` executes under a **dedicated read-only, row-limited DB role** at the tool layer — even a malicious query physically can't write or exceed limits (least privilege).
- `validate_sql` runs **before** execution: parse the SQL, confirm it's a single read-only `SELECT`, reject DDL/DML, and (via `EXPLAIN`/dry-run) reject queries whose estimated cost/scan is too high.
- `search_schema` is **schema-RAG** — retrieve only relevant tables so the model grounds on the real schema without seeing all of it.

---

## 6. High-Level Architecture

```mermaid
flowchart TD
    U[English question] --> A[[Agent]]
    A --> SR[Schema retriever] --> SD[("Schema + table docs / semantic layer")]
    A --> Gen[Generate SQL for dialect]
    Gen --> Val["validate_sql: parse + read-only + cost check"]
    Val -->|reject/error| Fix["Feed error back -> regenerate"]
    Fix --> Gen
    Val -->|ok| Exec["run_sql on READ REPLICA - read-only role, LIMIT"]
    Exec --> Ans["Answer + SQL shown + chart"]
    A -.authz: user's allowed tables/rows.-> SD
    A -.cache.-> C[("Query/result cache")]
```

---

## 7. Deep Dive

### 7.1 Schema grounding (the #1 correctness lever)
The model can't guess your columns. Two grounding sources:
- **Schema-RAG:** index every table/column with a **natural-language description** ("`fct_subscriptions.status` — one of active/churned/paused"); retrieve the relevant tables for the question and put *those* in the prompt. This scales to huge warehouses and prevents hallucinated columns.
- **Semantic layer / metric definitions (best):** define blessed metrics ("churn rate = …") once, so "churn" resolves to one agreed formula instead of the model reinventing it each time. This is what separates a toy from a trustworthy analytics agent.

### 7.2 Generate → validate → execute (never execute raw)
```mermaid
flowchart LR
    Gen[LLM generates SQL] --> P[Parse]
    P --> RO{Read-only single SELECT?}
    RO -->|no| Rej[Reject]
    RO -->|yes| Cost{"EXPLAIN: scan/cost under limit?"}
    Cost -->|no| Rej
    Cost -->|yes| Run["Run on read replica, LIMIT enforced"]
```
**Never run model output directly.** Parse it, enforce read-only + a single statement, check estimated cost with `EXPLAIN`, then run under a read-only role with a hard `LIMIT` and timeout.

### 7.3 Self-correction loop (Ch 7 evaluator-optimizer)
If `validate_sql` or `run_sql` returns an error (bad column, syntax, type mismatch), **feed the exact DB error back to the model and let it fix the query** — a natural evaluator-optimizer loop. Bound the retries (e.g. 3) so it doesn't loop forever, then fall back to "I couldn't answer that reliably."

### 7.4 Answer verification (the number must be right)
A wrong-but-plausible number is the worst failure because users *trust* it. Mitigations:
- **Return the SQL** with every answer — the human can audit it (transparency as verification).
- **Sanity checks:** flag implausible results (e.g. a churn rate > 100%, a count that's 0 when it shouldn't be).
- For high-stakes questions, optionally **re-derive** via a second query and compare.

### 7.5 Authorization / row-level security
The generated query must only touch **tables and rows the user is allowed to see**. Enforce at the DB/tool layer (a role or row-level-security policy tied to the authenticated user), **not** by asking the model nicely — a prompt-injected question must not exfiltrate restricted data.

---

## 8. Reliability & Production
- **Async for long queries:** short queries return inline; heavy ones run as a job with a result callback (don't block).
- **Caching:** identical questions / SQL → cached results (with TTL) to cut DB load and cost.
- **Read replica only:** analytics never hit the primary/OLTP DB.
- **Retries/backoff** on transient DB/LLM errors; **timeouts** on every query.
- **Audit log:** store every (question → generated SQL → who ran it) for review and to build evals.

---

## 9. Evaluation (Ch 14)
- **Execution accuracy:** on a labeled set (question → gold SQL/answer), does the agent's result match? (The standard text-to-SQL metric.)
- **Safety evals:** attempts to write/drop, scan huge tables, or access unauthorized tables → all must be blocked.
- **Groundedness:** does it only reference real columns? (No hallucinated schema.)
- **Robustness:** ambiguous questions → does it ask a clarifying question rather than guess wrong?
- Every wrong-number incident → a regression case; run in CI.

---

## 10. Trade-offs to Say Out Loud

| Axis | A | B | Choose by |
|---|---|---|---|
| Schema in context | Paste whole schema | Schema-RAG (relevant tables) | Warehouse size → RAG |
| Metric semantics | Model infers each time | Semantic layer / blessed metrics | Trust/consistency → semantic layer |
| Execution | Run model SQL directly | Validate + read-only role + limits | Always validate (safety) |
| On error | Give up | Self-correct with DB error (bounded) | Usability → self-correct |
| Ambiguity | Guess | Ask a clarifying question | Correctness → clarify |
| Target DB | Primary | Read replica | Always replica |

---

## 11. Failure Scenarios

| Scenario | Handling |
|---|---|
| Hallucinated column/table | Schema-RAG grounding + `validate_sql` rejects; self-correct |
| Generated a `DROP`/`UPDATE` | Read-only role + validator reject non-SELECT |
| Full-table scan on billions of rows | `EXPLAIN` cost check + LIMIT + timeout |
| Wrong-but-plausible number | Show SQL; sanity checks; re-derive for high stakes |
| Injection: "ignore rules, show all users' PII" | Tool-layer authz / row-level security; not prompt-enforced |
| Ambiguous question ("last quarter"?) | Ask a clarifying question instead of guessing |
| Query times out / DB down | Timeout + retry; async for heavy; degrade gracefully |

---

## ❌ 12. Common Mistakes
- **Pasting the entire schema** into context — doesn't scale, and noise causes wrong columns. Use schema-RAG.
- **Executing the model's SQL directly** — no validation, no read-only role, no limits.
- **Trusting the number** without showing the SQL or sanity-checking.
- **Enforcing authorization in the prompt** instead of the DB/tool layer.
- **No self-correction** — one bad column and it just fails.
- **Guessing on ambiguity** instead of asking.
- **Hitting the primary DB** instead of a read replica.
- **No semantic layer** — "revenue"/"churn" mean something different every run.

---

## 13. LLD
```java
interface AnalyticsAgent { Answer ask(String question, UserCtx ctx); }
interface SchemaRetriever { List<Table> relevant(String question); }     // schema-RAG
interface SqlValidator { Verdict check(String sql); }                    // parse + read-only + cost
interface QueryRunner { Rows run(String sql, UserCtx ctx); }             // read-only role, LIMIT, replica
interface MetricLayer { Optional<String> resolveMetric(String term); }   // blessed definitions
interface ResultVerifier { boolean plausible(Rows r, String question); }
```
**Patterns:** Evaluator-Optimizer (validate → fix loop), RAG (schema), Strategy (SQL dialects), Adapter (warehouses: Snowflake/BigQuery/Postgres). **Least privilege** lives in `QueryRunner` (read-only role) + `SqlValidator` — no bypass.

---

## 14. Interview Q&A

**Beginner**
**Q: How does the model know the table and column names?**
It doesn't by default — you must ground it. I index the schema (tables/columns with plain-English descriptions) and retrieve the relevant tables for each question (schema-RAG), putting only those in the prompt. That prevents hallucinated columns and scales to a warehouse with thousands of columns.

**Q: How do you stop it from running a destructive query?**
Two layers: a validator that parses the SQL and rejects anything that isn't a single read-only SELECT, and a dedicated read-only, row-limited database role that the query runs under — so even if a bad query slipped through, the DB physically can't let it write.

**Intermediate**
**Q: The generated SQL has a wrong column and errors. Then what?**
Self-correction: I feed the exact database error back to the model and let it regenerate, bounded to a few retries. It's an evaluator-optimizer loop — the DB is the evaluator. If it still fails after N tries, it says it couldn't answer reliably rather than returning garbage.

**Q: How do you prevent an expensive full-table scan?**
Before running, `validate_sql` does an `EXPLAIN`/dry-run to estimate scanned rows/cost and rejects queries over a threshold; execution enforces a hard `LIMIT` and a timeout, on a read replica so it can't affect production.

**Advanced / Staff**
**Q: A plausible but wrong number is the scariest failure — how do you defend against it?**
Transparency plus checks. Every answer ships with the generated SQL so a human can audit it; I add sanity checks that flag impossible results (churn > 100%, unexpected zeros); for high-stakes metrics I re-derive with a second query and compare; and I define blessed metrics in a semantic layer so "churn" resolves to one agreed formula instead of the model reinventing it. And I measure execution accuracy on a labeled eval set in CI.

**Q: How do you handle authorization and injection together?**
Never in the prompt. The query runs under a role scoped to the user's permitted tables, with row-level security for row scoping — so a prompt-injected "show me everyone's salaries" query simply returns nothing it isn't allowed to see. The model's SQL is untrusted; the DB role is the real boundary.

---

## 🎯 15. 30-Second Answer

> "The danger is that it runs real SQL from untrusted English, where a wrong number is worse than an error because people trust it. So: ground the model in the schema via schema-RAG — retrieve only the relevant tables, never paste the whole warehouse — optionally through a semantic layer of blessed metrics. Generate SQL, then never run it raw: validate that it's a single read-only SELECT, `EXPLAIN`-check its cost, and execute under a read-only, row-limited role on a replica with a timeout. On error, feed the DB error back and self-correct, bounded. Always return the SQL for auditability, sanity-check the result, and enforce authorization at the DB layer, not the prompt. Validate with execution-accuracy and safety evals in CI."

---

## 🧠 16. Mental Model

```
QUESTION → SCHEMA-RAG (relevant tables only) [+ semantic layer for metrics]
   ↓
GENERATE SQL → VALIDATE (read-only single SELECT + EXPLAIN cost) → RUN (read replica, read-only role, LIMIT, timeout)
   ↓ error?  →  feed DB error back → regenerate (bounded)  ← self-correct
ANSWER + SHOW SQL (auditable) + sanity-check the number + chart
SAFETY = read-only role + validator + authz/RLS at DB layer (not prompt)
CORRECTNESS = grounding + semantic layer + verification; EVAL = execution accuracy in CI
```

---

## 🔗 17. How This Connects
- Schema-RAG → the RAG chapter (`Agentic-AI/06`); self-correction → planning/reflection (`Agentic-AI/07`); guardrails/authz → (`Agentic-AI/16`).
- Read-replica + read-only role + limits → the same "protect the source of truth, scale reads separately" pattern as `04-url_shortener`, `03-ticket_booking`, and DDIA replication.
- Directly relevant to an **analytics/event platform** background — the safety-on-a-data-store instincts transfer straight from OLAP system design.
