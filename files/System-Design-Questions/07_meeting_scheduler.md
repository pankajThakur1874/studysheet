# Meeting Scheduler

## 0. Why This Design Matters

Covers interval overlap, transactions, time zones, recurrence, external integrations and reminders.

## 1. Problem Overview — Explain It Simply

Users create meetings between participants, the system checks availability, prevents conflicting bookings, and asynchronously sends reminders and syncs external calendars.

## 2. Functional Requirements

- Create/update/cancel meeting
- Availability
- Invite/respond
- Recurring meetings
- Reminders
- External calendar sync

## 3. Non-Functional Requirements

- Strong consistency for final booking
- Low availability latency
- Time-zone correctness
- Eventual consistency acceptable for notifications/sync

## 4. Capacity Estimation

Example: 20M active users × 2 calendar operations/day = 40M/day ≈ 463 QPS average. Availability reads can be much higher than writes.

## 5. API Design

```text
GET /v1/users/{id}/availability?start=...&end=...
POST /v1/meetings
PUT /v1/meetings/{id}
POST /v1/meetings/{id}/cancel
POST /v1/meetings/{id}/response
```

## 6. High-Level Architecture

```text
Client
 ↓
API Gateway
 ↓
Meeting Service
 ├── Availability
 ├── Booking
 └── Recurrence
       ↓
   PostgreSQL
       ↓
     Outbox
       ↓
     Kafka
      ├── Reminder
      ├── Notification
      └── Calendar Sync
```

### HLD Flowchart

The following is the primary interview flowchart. Draw this first, then explain each box.

```mermaid
flowchart LR
    C[Client] --> G[API Gateway]
    G --> M[Meeting Service]
    M --> AV[Availability Service]
    M --> D["(PostgreSQL)"]
    M --> O[Outbox]
    O --> K[Kafka]
    K --> R[Reminder Worker]
    K --> N[Notification]
    K --> EXT[Calendar Sync Adapter]
    EXT --> GC[External Calendar]
```

## 7. Database Selection

PostgreSQL is ideal for meetings and participants because relational queries, transactions and constraints matter. Store instants in UTC and retain the event timezone for recurrence semantics.

### HLD Deep-Dive Flowchart

Use this second flowchart when the interviewer asks **"walk me through the complete flow"**.

```mermaid
flowchart TD
    A[Create meeting] --> B["Normalize time to UTC + retain timezone"]
    B --> C["Check participant/room availability"]
    C --> D{Conflict?}
    D -->|Yes| E["Reject / suggest alternatives"]
    D -->|No| F[Transactional booking]
    F --> G["Write meeting + outbox"]
    G --> H[Kafka]
    H --> I[Reminder scheduling]
    H --> J[External calendar sync]
    H --> K[Notification]
    J --> L{Provider available?}
    L -->|No| M[Retry sync]
    L -->|Yes| N[Sync successful]
```

## 8. HLD Deep Dive — Why Each Decision?

### Why How detect overlap?

For [start,end], conflict exists when existing.start < requested.end AND existing.end > requested.start.

### Why Why strong consistency only at final booking?

Availability can be cached/stale for a good user experience, but the final mutation must recheck authoritative state.

### Why Why UTC plus timezone?

UTC gives a stable instant; timezone preserves local recurring semantics and handles DST.

### Why How recurring meetings work?

Store RRULE + timezone and generate/check occurrences in a bounded window rather than materializing years unnecessarily.

### Why Why outbox?

Meeting creation and event publication must not silently diverge.

### Why External calendar failure?

Persist sync state and retry asynchronously; do not roll back the core meeting merely because a third-party provider is temporarily unavailable unless business rules require it.

## 9. Interview Question & Answer

### Q: Two users book the same room/time?

**Answer:** Transaction/locking/constraint prevents both from committing.

### Q: Calendar provider is down?

**Answer:** Retry sync and expose sync status.

### Q: DST changes?

**Answer:** Calculate recurrence in the event's timezone.

### Q: Can availability cache be stale?

**Answer:** Yes, but final commit must recheck.

## 10. LLD

```text
MeetingService
 ├── AvailabilityService
 ├── RecurrenceService
 ├── MeetingRepository
 └── CalendarSyncService

CalendarAdapter
 ├── GoogleCalendarAdapter
 └── MicrosoftCalendarAdapter

Patterns:
- Adapter → external calendars
- Strategy → recurrence/conflict policy
- Outbox → events
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

If the interviewer asks you to summarize **Meeting Scheduler**, use this structure:

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
