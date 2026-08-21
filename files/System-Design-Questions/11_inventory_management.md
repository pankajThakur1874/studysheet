# Inventory Management

## 0. Why This Design Matters

Covers atomic updates, reservations, concurrency, stock correctness, Redis, transactions and Saga.

## 1. Problem Overview — Explain It Simply

Inventory answers how many units are available and prevents concurrent orders from consuming more stock than allowed. A common flow is reserve → pay → confirm, with release if payment fails.

## 2. Functional Requirements

- Check stock
- Reserve
- Confirm
- Release
- Inventory adjustments
- Multi-warehouse support
- History

## 3. Non-Functional Requirements

- Strong correctness for critical inventory
- High read throughput
- Concurrency safety
- Recovery from abandoned reservations

## 4. Capacity Estimation

Example: 20M products/variants with 100M stock reads/day ≈ 1,157 QPS average and ≈ 5,800 peak at 5×. Popular SKUs create concentrated contention.

## 5. API Design

```text
GET /v1/inventory/{productId}
POST /v1/inventory/reserve
POST /v1/inventory/confirm
POST /v1/inventory/release
```

## 6. High-Level Architecture

```text
Order
 ↓
Inventory Service
 ↓
Atomic Reservation
 ↓
Payment
 ↓
Confirm

Payment failure
 ↓
Release

PostgreSQL = authoritative
Redis = cache/temporary state
Kafka/Outbox = events
```

### HLD Flowchart

The following is the primary interview flowchart. Draw this first, then explain each box.

```mermaid
flowchart LR
    O[Order Service] --> I[Inventory Service]
    I --> D[(PostgreSQL)]
    I --> R[(Redis Cache)]
    I --> P[Payment Service]
    I --> X[Reservation Expiry Worker]
    I --> OB[Outbox]
    OB --> K[Kafka]
    K --> N[Notification / Other Services]
```

## 7. Database Selection

PostgreSQL is a strong starting point because inventory mutations need transactions/conditional updates. Cassandra is useful for large histories or predictable distributed reads, not necessarily for the core decrement.

### HLD Deep-Dive Flowchart

Use this second flowchart when the interviewer asks **"walk me through the complete flow"**.

```mermaid
flowchart TD
    A[Order] --> B[Reserve inventory]
    B --> C[Atomic available > 0 update]
    C --> D{Reserved?}
    D -->|No| E[Out of stock]
    D -->|Yes| F[Reservation TTL]
    F --> G[Payment]
    G --> H{Payment success?}
    H -->|Yes| I[Confirm reservation]
    H -->|No/timeout| J[Release inventory]
    F --> K{TTL expired?}
    K -->|Yes| J
    I --> L[Outbox -> Kafka]
    J --> M[Inventory available again]
```

## 8. HLD Deep Dive — Why Each Decision?

### Why How prevent negative stock?

Atomic conditional update: UPDATE inventory SET available=available-1 WHERE product_id=? AND available>0.

### Why Why reserve before payment?

Payment can take time. Reservation prevents another order from consuming the stock while payment is in progress.

### Why Why TTL?

Abandoned carts must eventually release stock.

### Why Saga?

Inventory, payment and order are separate services. Compensation releases inventory if later steps fail.

### Why Redis-only inventory?

Fast but risky as authoritative business state. Use durable source of truth for critical inventory.

### Why What about overselling?

Decide based on business requirement. Limited/high-value inventory generally requires strict correctness; some businesses tolerate controlled oversell with reconciliation.

## 9. Interview Question & Answer

### Q: Two buyers want the last item?

**Answer:** Only one atomic conditional update succeeds.

### Q: Payment fails after reservation?

**Answer:** Release reservation through compensation.

### Q: Worker crashes?

**Answer:** Reservation expiry/lease recovery releases it.

### Q: Duplicate reserve?

**Answer:** Idempotency key maps to the existing reservation.

### Q: Hot SKU?

**Answer:** Queue requests, virtual waiting room, shard/partition inventory by location where possible.

## 10. LLD

```text
InventoryService
 ├── reserve()
 ├── confirm()
 ├── release()
 └── adjust()

ReservationManager
 └── ExpiryWorker

Patterns:
- State Machine
- Saga
- Strategy → allocation
- Idempotency
- Outbox
```

## 11. Failure Checklist

Always walk through:
- Service crash
- Database failure
- Cache/Redis failure
- Kafka/queue failure
- External dependency timeout
- Duplicate request
- Duplicate event
- Hot key/hot partition
- Network partition
- Partial success
- Recovery/reconciliation

## 12. Trade-Offs You Should Say Out Loud

A strong interview answer does not say "this is the only solution."

Instead say:

> "I'm choosing X because of requirement Y. The alternative is Z, which would be better if requirement A were more important."

Typical trade-offs:

| Choice | Benefit | Cost |
|---|---|---|
| Strong consistency | Correctness | Higher latency/coordination |
| Eventual consistency | Scale/availability | Stale reads |
| Redis | Very low latency | Memory/cost/failure considerations |
| PostgreSQL | Transactions | Horizontal write scaling is harder |
| Cassandra | Huge scale/availability | Query-driven modeling, weaker transactions |
| Kafka | Decoupling/replay | Operational complexity |
| Sync processing | Simple response semantics | Higher latency/coupling |
| Async processing | Resilience/scale | Eventual completion |

## 13. Staff-Level Follow-Up Questions

Be ready for these:

1. What breaks first at 10× traffic?
2. What is your hottest key/partition?
3. Can this operation be retried safely?
4. What happens if the service crashes after the external side effect but before the DB update?
5. Which data needs strong consistency?
6. Where can you tolerate eventual consistency?
7. How would you shard it?
8. What happens to a shard becoming hot?
9. How do you recover from a partial failure?
10. How do you observe correctness, not just availability?
11. What would you cache?
12. What would you never cache?
13. Where does backpressure happen?
14. How do you handle replay?
15. Why did you choose this database over the alternatives?

## 14. 2-Minute Interview Explanation

If the interviewer asks you to summarize **Inventory Management**, use this structure:

> "I'll first separate the read-heavy and correctness-critical paths. The authoritative state lives in the database best suited to the business invariant, while cache and distributed stores handle high-volume/temporary access. Requests that don't need to block the user are moved to an asynchronous queue/event stream. For concurrency, I use atomic updates, constraints, locks or idempotency depending on the invariant. For failures, I use timeout, retry with exponential backoff and jitter, circuit breakers where appropriate, and reconciliation when an external system can have an unknown outcome. At scale, I shard based on the dominant query/access pattern and explicitly handle hot keys and backpressure."


# Interview Framework

Use this exact flow in the interview:

```text
1. Clarify requirements
       ↓
2. Estimate scale
       ↓
3. Define APIs
       ↓
4. Define data model
       ↓
5. Draw HLD
       ↓
6. Explain request/data flow
       ↓
7. Deep dive into the hardest invariant
       ↓
8. Discuss DB/cache/Kafka
       ↓
9. Concurrency + consistency
       ↓
10. Failure handling
       ↓
11. Scaling/sharding
       ↓
12. LLD
       ↓
13. Trade-offs
```

## Universal Scale Formula

```text
Average QPS = daily requests / 86,400
Peak QPS = average QPS × peak factor
```

Start with an assumption such as 5× and say:

> "I'll use 5× as a starting peak multiplier; we can adjust if you give me a traffic pattern."

## Universal Database Cheat Sheet

### PostgreSQL

Use when you need:

- ACID transactions
- Strong consistency
- Relationships
- Constraints
- Conditional updates
- Booking/payment/inventory

### MongoDB

Use when:

- Data is naturally document-shaped
- Flexible schema matters
- Access is mostly by document
- Relationships are not the dominant problem

### Cassandra

Use when:

- Very high write volume
- High availability
- Predictable query patterns
- Massive time-series/activity/message data

Remember:

> Cassandra data modeling starts from queries, not from normalized entities.

### Redis

Use for:

- Cache
- Counters
- Rate limits
- Locks/leases
- Sessions
- Presence
- Temporary state

Do not automatically make Redis the source of truth for critical durable business state.

### Elasticsearch/OpenSearch

Use for:

- Full-text search
- Ranking
- Autocomplete
- Filtering/faceting

Think of it as a read model, not automatically your transactional source of truth.

### Kafka

Use for:

- Async events
- Decoupling
- Buffering
- Replay
- Fanout
- Stream processing

## Universal Reliability

When interviewer asks "what if X fails?":

```text
Timeout
 ↓
Should we retry?
 ↓
Idempotency
 ↓
Exponential backoff + jitter
 ↓
Circuit breaker
 ↓
Fallback?
 ↓
DLQ?
 ↓
Reconciliation?
```

## Universal Cache Problems

```text
Cache Stampede
→ request coalescing / lock / background refresh

Hot Key
→ local cache / CDN / replication / sharding

Cache Penetration
→ negative cache / Bloom filter

Cache Avalanche
→ TTL jitter / staggered expiry
```

## Universal Kafka

```text
Producer
 ↓
Topic
 ↓
Partitions
 ↓
Consumer Group
 ↓
Consumers
```

- Ordering → within a partition
- Scale → partitions
- Parallelism → consumers
- Replay → retention
- Duplicate events → idempotent consumers

## Universal Consistency Rule

Use strong consistency when the business invariant cannot tolerate stale state:

- Payment
- Wallet
- Booking
- Critical inventory
- Ledger

Eventual consistency is often fine for:

- Search index
- Analytics
- Recommendations
- Notifications
- Like/view counters

The best sentence to remember:

> "Consistency should follow the business invariant, not the technology."

## Universal Concurrency

Ask:

> "What happens if two requests execute this operation at exactly the same time?"

Possible tools:

- Atomic DB update
- Optimistic locking
- Pessimistic locking
- Unique constraint
- Distributed lock/lease
- Idempotency key
- Queue serialization

## Universal Idempotency

If the same request can safely be retried:

```text
request
 ↓
idempotency key
 ↓
existing result?
 ├── yes → return existing result
 └── no → process + store result
```

## Universal Staff-Level Thinking

Always discuss:

1. What happens at 10× traffic?
2. What becomes the bottleneck?
3. What is the hot key/hot partition?
4. What if a dependency fails?
5. What happens if the same request arrives twice?
6. Which state must be strongly consistent?
7. What can be eventually consistent?
8. What can be asynchronous?
9. Where does backpressure happen?
10. How do we observe and reconcile failures?

# Final Interview Rule

Do not jump directly into technologies.

First say:

> "Let me clarify the requirements and scale. Then I'll identify the critical business invariant, because that will drive my consistency and database choices."

That sentence alone makes the discussion much more senior.
