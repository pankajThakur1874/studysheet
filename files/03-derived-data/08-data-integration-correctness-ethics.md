# Data Integration, Correctness & Ethics

**Prerequisites:** Topics 30–33 (messaging, CDC, stream processing, fault tolerance)
**Difficulty:** Intermediate (34, 36) / Advanced (35)
**Interview importance:** High (34, 35) / Medium (36)
**Source:** Chapter 12 — "Data Integration", "The End-to-End Argument", "Enforcing Constraints", "Timeliness and Integrity", "Trust, but Verify", "Doing the Right Thing"

---

## Topics in This File

This file consolidates three shorter Chapter 12 topics into one document:
- **Topic 34 — Data Integration:** combining specialized systems via derived data flows; the "unbundled database" thesis.
- **Topic 35 — Correctness:** the end-to-end argument, idempotency keys, uniqueness constraints without distributed transactions, timeliness vs integrity.
- **Topic 36 — Ethics:** responsibility, privacy, bias, power, and the human stakes of data system design.

---

# Part A: Data Integration (Topic 34)

## What Is It?

**Data integration** is the challenge of keeping the same data in sync across multiple specialized storage systems that each serve different access patterns.

Real applications don't use one database — they combine an OLTP database, a search index, a cache, an analytics store, and perhaps a message queue. Each holds a representation of the data optimised for its purpose. Keeping them in sync is the integration problem.

## The Core Thesis: Derived Data via Event Logs

The book's synthesis: **the most robust approach to data integration is to funnel all writes through a single system of record and derive all other representations from its ordered event log.**

This is the "unbundled database" vision: instead of one database that does everything (OLTP + full-text search + analytics + caching) at the cost of being mediocre at each, use specialised tools connected by immutable event logs. The database is the system of record (writes go here first); everything else is derived.

```
System of record (PostgreSQL)
    ↓ CDC / event log (Kafka)
    ├── Search index (Elasticsearch) — follower
    ├── Cache (Redis) — follower
    ├── Analytics store (Redshift/BigQuery) — follower
    └── ML feature store — follower
```

**Why this works:** the event log is totally ordered. All derived systems see changes in the same order — the database's commit order. The race-condition problem of dual writes (Topic 31) disappears because there is only one write source and one ordering authority.

## Reasoning About Dataflows

The question to answer for every piece of data: **where is it written first, and which representations are derived from which sources?**

Once this is clear:
- Derived systems can be rebuilt from the source by replaying the log.
- Adding a new derived system doesn't touch the application (add a new Kafka consumer group).
- A bug in a derived system is recoverable (replay the log with the fixed code).

**Derived data vs distributed transactions:** both keep multiple systems consistent, by different means. Distributed transactions use locks and atomic commit; derived data systems use log ordering and idempotent deterministic updates. The trade-off: transactions give linearizability and immediate consistency; derived data gives eventual consistency. The book's judgment: XA transactions have poor performance and fault tolerance; log-based derived data is the more practical approach for integrating systems at scale.

## The Limits of Total Ordering

Total ordering across all events requires a single leader or a consensus protocol. This has scaling limits:
- **Partitioned logs:** events on different partitions have no ordering guarantee — the system must handle or avoid ordering dependencies across partitions.
- **Multi-datacenter / microservices:** events from different geographic locations or independent services have no global order.
- **Offline clients:** events generated while offline may be much older than when they sync.

For many use cases, causality within a key (partition-based ordering) is sufficient. Total ordering is only needed for cross-partition invariants (uniqueness across all partitions, for example), which then requires consensus (Topic 26).

## Batch and Stream as Complementary Tools

Batch and stream processing are not competing choices — they're complementary. The pattern:
- **Stream:** keep derived data continuously up to date (low latency, recent events).
- **Batch:** periodically reprocess all history to rebuild a derived view from scratch (correctness, evolving logic).

**Lambda architecture:** run both a batch layer (exact, slow) and a streaming layer (approximate, fast) and merge their outputs. The book's critique: maintaining the same logic in two frameworks is expensive; and modern frameworks (Flink) can do both, making the architectural split unnecessary.

**The better model:** use a unified framework (Flink, or Spark with Structured Streaming) that handles both bounded (batch) and unbounded (stream) inputs with the same API. The distinction between "batch job to rebuild" and "streaming job to keep current" then becomes just a matter of what input you point the job at.

---

# Part B: Correctness & Integrity (Topic 35)

## The End-to-End Argument

**The principle** (Saltzer, Reed, Clark, 1984): *correct behaviour of a system can only be fully guaranteed by the endpoints of a communication, not by intermediate layers.* TCP eliminates duplicate packets in transit, but can't prevent a network timeout causing the application to retry an operation that already succeeded. The deduplication must happen end-to-end.

**Application to databases:**

```
User clicks "Pay ₹500"
  → HTTP POST (may retry on timeout)
    → Application server (may retry on DB timeout)
      → Database transaction (may retry on deadlock)
```

At each hop, a retry may duplicate the operation. TCP handles its layer; a database transaction handles its layer; but neither prevents the *user* from clicking again after a timeout, or the *application* from retrying the DB call if the commit response was lost.

The result: **serializable transactions are necessary but not sufficient for end-to-end correctness.** The application must also manage deduplication across the full request path.

## Idempotency Keys — The Practical Solution

The solution: generate a **unique operation ID** at the very beginning of the request (the client, or the earliest application layer) and pass it through every hop. Each layer uses it to deduplicate:

```sql
-- Application generates UUID request_id per user action
ALTER TABLE requests ADD UNIQUE (request_id);

BEGIN TRANSACTION;
  -- This INSERT fails (and the transaction aborts) if request_id already exists
  INSERT INTO requests (request_id, from_account, to_account, amount)
  VALUES ('0286FDB8-...', 4321, 1234, 11.00);
  
  UPDATE accounts SET balance = balance + 11.00 WHERE account_id = 1234;
  UPDATE accounts SET balance = balance - 11.00 WHERE account_id = 4321;
COMMIT;
```

If the user retries (same `request_id`), the INSERT fails on the unique constraint → the whole transaction aborts → the balance update doesn't happen → no double charge. The `requests` table also acts as an audit log — a natural form of event sourcing.

**Why the uniqueness constraint works at weak isolation:** a uniqueness constraint is enforced even at read-committed isolation for single-row inserts. The INSERT either succeeds or fails, atomically. This is safer than an application-level "check then insert" which is subject to write skew (Topic 18).

## Uniqueness Constraints Without Distributed Transactions

Enforcing uniqueness at scale without 2PC (which is expensive and blocks):

**Log-partitioned approach:**
1. Route all requests for a given unique value (username, request_id) to the **same partition** (by hash of the value).
2. A **single-threaded stream processor** per partition reads requests sequentially.
3. It maintains a local lookup of claimed values.
4. First request for a value → accept; subsequent requests → reject.

Because the log enforces a total order within each partition, and all requests for a given key go to the same partition, the processor sees them in a single, deterministic sequence — no race condition. This is equivalent to the total-order-broadcast approach to linearizable storage (Topic 24), but without a centralised coordinator.

**Scaling:** add partitions (and stream processor instances) to increase throughput. Each partition is independent. No cross-partition coordination for single-key uniqueness.

## Timeliness vs Integrity — The Critical Distinction

The book draws a distinction that most of the "consistency" discussion conflates:

**Timeliness:** can you see the latest write immediately after it's committed? A violation: eventual consistency — you might not see a write you just made for a few seconds. Annoying, but often acceptable.

**Integrity:** is the data correct — free from contradictions, missing facts, and unsanctioned mutations? A violation: perpetual inconsistency — a transfer debited from one account but never credited to the other. Catastrophic.

> *"Violations of timeliness are 'eventual consistency'; violations of integrity are 'perpetual inconsistency.'"*

**The implication:** in most applications, **integrity matters far more than timeliness.** A stale read (timeliness violation) is a UX problem. A double charge or a corrupted balance (integrity violation) is a financial and legal problem.

The insight: **log-based derived data systems separate these two properties.** Asynchronous log processing accepts timeliness violations (eventual consistency) but can maintain integrity through:
- **Exactly-once / effectively-once semantics** (fault-tolerant message delivery + deduplication).
- **Deterministic derivation** (same input → same output → idempotent retries are safe).
- **End-to-end request IDs** (deduplication across all hops).
- **Event sourcing** (immutable log → auditable; replay to verify correctness).

The result: log-based systems can achieve comparable correctness to distributed transactions at much better performance and operational robustness.

## Multi-Partition Correctness Without Atomic Commit

A transfer of money between account A and account B, without 2PC:

1. **Client sends one message** with a unique request_id, routed to a request-log partition by hash(request_id). Single-object write → atomic.
2. **A stream processor** reads the request-log, emits two derived events: "debit A by ₹500" (routed to A's partition) and "credit B by ₹500" (routed to B's partition). Both carry the original request_id. If the processor crashes and replays, it emits the same derived events (deterministic) — downstream deduplicates by request_id.
3. **Two further processors** (one per account) apply the debit and credit, deduplicating by request_id.

No distributed transaction needed. Integrity is preserved by: (a) the single-message atomic write as the source of truth; (b) deterministic derivation; (c) end-to-end deduplication by request_id.

This is the practical embodiment of the book's thesis: **integrity without linearizability, without 2PC, through log ordering + idempotency.**

## "Trust, but Verify" — Auditability

Even with all the above mechanisms, data can be silently corrupted by bugs, hardware faults, and operational errors. The book recommends:

- **Immutable event logs** as the source of truth (bugs can be fixed by replaying with corrected code; mutable databases can permanently lose data).
- **Periodic checksums / consistency checks** on derived data — verify that the search index content matches the database, that account balances equal the sum of transactions, that row counts match. Consistency checks catch silent corruption; without them, you only discover problems when users notice.
- **End-to-end checksums** on data that traverses multiple systems — analogous to how TCP checksums don't prevent corruption inside the application, you need application-level checksums to detect corruption at all layers.

---

# Part C: Ethics (Topic 36)

## Why This Chapter Exists

Chapter 12 ends with something unusual for a technical book: a direct engagement with the ethical responsibilities of software engineers who build data systems. The book argues that the scale and power of modern data systems create obligations that engineers cannot ignore.

## Predictive Analytics and Bias

Systems that predict behaviour (creditworthiness, recidivism risk, insurance pricing, hiring) can cause real harm:

- **Feedback loops:** if you predict a person is a bad credit risk and deny them credit, they can't build a credit history, which confirms the prediction. The model creates the outcome it predicts.
- **Discriminatory proxies:** a model trained on historical data encodes historical discrimination. Using zip code as a feature may be a proxy for race. "Optimising" for historical patterns can perpetuate and amplify injustice.
- **Opacity and lack of recourse:** algorithmic decisions that affect people's lives — jobs, loans, housing, parole — are often opaque. People can't understand or challenge a decision made by a model.

The book's point: data engineers build these systems. Understanding the failure modes isn't just technical — it's an ethical obligation.

## Privacy and Surveillance

Large-scale behavioural data collection enables surveillance far beyond what was previously possible:

- Data collected for one purpose is routinely used for others (mission creep).
- Data combined from multiple sources reveals intimate details that no single dataset would expose.
- Data is often held indefinitely — "we might use it someday" — without a clear purpose.
- Security breaches expose sensitive information people never intended to share.

The book's position: the ability to collect data is not the same as the right to collect it, nor the right to use it in any way that maximises revenue. Informed consent and data minimisation are not just regulatory requirements (GDPR, CCPA) but ethical ones.

## Power and Accountability

Data systems concentrate power. A few large platforms hold detailed behavioural profiles of billions of people. This creates:

- **Asymmetry:** the platform knows far more about the user than the user knows about the platform.
- **Lock-in:** users can't take their data and their social graph to a competitor.
- **Accountability gaps:** when algorithms cause harm, it's difficult to identify who is responsible — the data collector? The model trainer? The deployer?

The book quotes David Gelernter: *"the goal of computing is to help human beings."* When a data system causes discrimination, enables stalking, or dehumanises individuals by reducing them to statistical profiles, it has failed at this fundamental goal — regardless of whether it achieves its business metrics.

## The Engineer's Responsibility

The book closes with a challenge: engineers are not absolved by "I just built what I was asked to build." Medical professionals have codes of ethics; lawyers have professional responsibility; engineers are developing similar obligations.

Questions an engineer should ask:
- Who is harmed if this data is breached, misused, or analysed in unintended ways?
- Does this system make decisions that affect people without their knowledge or recourse?
- Am I treating users as a population to be optimised over, or as individuals with agency?
- What data should we collect, and what should we simply not collect (even if we could)?

**The practical stance:** data minimisation (collect only what you need), purpose limitation (use data only for its stated purpose), access controls, deletion policies, transparency, and proportionality between data collection and the value provided to users.

---

## Combined Interview Questions

**Q: How do you keep a search index in sync with a primary database without dual writes?**
CDC (Topic 31): tap the database's WAL, publish to Kafka, run an Elasticsearch indexer as a consumer group. The database is the single source of truth; the log is the distribution mechanism; the search index is a follower. No race condition — all consumers see changes in the database's commit order. Adding a new derived system is just adding a consumer group. To bootstrap: use a Kafka compacted topic — a new consumer reads from offset 0 and gets the full current state.

**Q: A payment transaction times out. The user retries. How do you prevent a double charge?**
End-to-end idempotency key. The client generates a UUID request_id before the first attempt. Every retry carries the same UUID. On the server, a unique constraint on request_id in the requests table ensures that only the first successful INSERT succeeds; subsequent attempts with the same UUID fail silently. The request_id is also included in the downstream debit/credit events, so each hop in the multi-step pipeline deduplicates independently. The key insight is that TCP's deduplication and the database transaction's atomicity are each local to their layer — only an end-to-end unique identifier provides deduplication across the full request path.

**Q: Explain the difference between timeliness and integrity in distributed systems.**
Timeliness: can you read the latest write immediately? A violation is eventual consistency — seeing stale data for a few seconds. Annoying, sometimes confusing, but usually tolerable. Integrity: is the data correct — no missing facts, no contradictions, no unsanctioned mutations? A violation is perpetual inconsistency — a payment debited but not credited, a constraint permanently violated. Catastrophic. Log-based systems accept timeliness violations (async delivery) but preserve integrity through exactly-once delivery, deterministic processing, and end-to-end deduplication. The implication is that "eventual consistency" is often a timeliness trade-off, not an integrity trade-off — and many applications only need timeliness guarantees in limited places (their own recent writes), while needing integrity everywhere.

**Q: How do you enforce a uniqueness constraint at scale without a single-node bottleneck or 2PC?**
Partition by hash of the unique value (e.g., username). Route all requests for the same username to the same partition. A single-threaded stream processor per partition reads requests sequentially, maintains a local record of claimed usernames, and accepts the first claim while rejecting duplicates. Because the log enforces total order within each partition, and all requests for a given key go to the same partition, there's no race condition — no two requests for the same value are processed concurrently. Throughput scales by adding partitions. This is equivalent to the total-order-broadcast approach to linearizable storage, but implemented as a stream processor, without a global coordinator.

---

## ⚡ Quick Revision (all three topics)

**Data Integration (34):**
- No single system does everything → specialised tools connected by event logs.
- System of record → Kafka CDC → derived followers (search, cache, warehouse).
- Log ordering = single timeline = no race conditions.
- Derived data beats 2PC for practical integration at scale.
- Batch (rebuild from history) + stream (keep current) are complementary, not competing.
- Total ordering has limits: partitioned logs, multi-DC, microservices → only partition-level ordering.

**Correctness (35):**
- **End-to-end argument:** TCP deduplication and DB transactions are not enough — end-to-end idempotency keys are required.
- **Idempotency key:** UUID generated at the client, passed through every hop, used for dedup at each layer.
- **Uniqueness without 2PC:** partition by the unique value; single-threaded stream processor per partition; first write wins.
- **Timeliness vs integrity:** timeliness = eventual consistency (annoying). Integrity = correctness of data (critical). Log-based systems trade timeliness for integrity.
- **Multi-partition correctness without atomic commit:** single message as source of truth → deterministic derived events → end-to-end dedup by request_id.
- **Auditability:** immutable logs + periodic consistency checks catch silent corruption.

**Ethics (36):**
- Predictive models can encode and amplify historical bias. Feedback loops reinforce predictions.
- Data collected for one purpose is often used for others; combination reveals more than any single dataset.
- Engineers are responsible for what they build, not just how well it performs its stated metric.
- Practical stances: data minimisation, purpose limitation, access controls, deletion policies, transparency.
- **"The goal of computing is to help human beings."** When a system causes harm, technical correctness is insufficient.
