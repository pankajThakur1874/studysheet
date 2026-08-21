# Twitter / Social Feed

## 0. Why This Design Matters

Covers fanout, hot users, Kafka, timeline stores, caching, ranking and read/write trade-offs.

## 1. Problem Overview — Explain It Simply

Users publish posts and followers need a fast personalized timeline. The central problem is how to avoid doing huge amounts of work every time someone posts or every time someone opens the feed.

## 2. Functional Requirements

- Create post
- Follow/unfollow
- Home feed
- Profile feed
- Like/repost
- Notifications
- Optional search/trends

## 3. Non-Functional Requirements

- Very high read throughput
- Low feed latency
- High availability
- Eventual consistency acceptable for feed
- Horizontal scalability

## 4. Capacity Estimation

Example: 100M DAU × 10 feed opens/day = 1B feed requests/day ≈ 11.6K average QPS and ≈ 58K at 5×. Celebrity fanout is the major write-amplification risk.

## 5. API Design

```text
POST /v1/tweets
GET /v1/feed?cursor=abc&limit=20
POST /v1/users/{userId}/follow
POST /v1/tweets/{tweetId}/like
```

## 6. High-Level Architecture

```text
Client
 ↓
API Gateway
 ├── Tweet Service
 ├── Follow Service
 └── Feed Service

Tweet Service
 ↓
Post Store
 ↓
Outbox
 ↓
Kafka
 ↓
Fanout Workers
 ├── Normal users → fanout-on-write
 └── Celebrities → fanout-on-read

Feed Service
 ↓
Redis/Timeline Store
 ↓
Ranking
 ↓
Client
```

### HLD Flowchart

The following is the primary interview flowchart. Draw this first, then explain each box.

```mermaid
flowchart LR
    C[Client] --> G[API Gateway]
    G --> T[Tweet Service]
    T --> D[(Post Store)]
    T --> O[Outbox]
    O --> K[Kafka]
    K --> F[Fanout Workers]
    F --> TL[(Timeline Store / Redis)]
    C --> FE[Feed Service]
    FE --> TL
    FE --> R[Ranking]
    R --> C
    T --> S[Search Index]
```

## 7. Database Selection

Cassandra/distributed KV is a good fit for high-volume predictable timeline/post access. Redis caches hot timelines. PostgreSQL can hold account/relationship metadata. Elasticsearch/OpenSearch handles search.

### HLD Deep-Dive Flowchart

Use this second flowchart when the interviewer asks **"walk me through the complete flow"**.

```mermaid
flowchart TD
    A[User posts tweet] --> B[Persist tweet]
    B --> C[Outbox]
    C --> D[Kafka]
    D --> E{Follower count}
    E -->|Normal| F[Fanout-on-write]
    E -->|Celebrity| G[Store once]
    F --> H[Write tweet ID to follower timelines]
    G --> I[Fanout-on-read during feed request]
    H --> J[Feed request]
    I --> J
    J --> K[Merge candidates]
    K --> L[Rank]
    L --> M[Return cursor page]
```

## 8. HLD Deep Dive — Why Each Decision?

### Why Fanout-on-write?

When a normal user posts, write the tweet ID into follower timelines. Reads are fast, but writes grow with follower count.

### Why Fanout-on-read?

Store the celebrity tweet once and merge it into followers' feeds when they read. This avoids millions of writes but makes reads more expensive.

### Why Why hybrid?

Normal users benefit from cheap fast reads; celebrities create massive write amplification, so read-time fanout is safer.

### Why Why Kafka?

Decouples tweet creation from fanout, notifications, analytics and search indexing.

### Why Why cursor pagination?

OFFSET becomes expensive and unstable at large offsets. A cursor based on timestamp/id is more scalable.

### Why What is a hot key?

A celebrity or viral post can receive huge traffic. CDN/local cache/replication and hybrid fanout reduce concentration.

## 9. Interview Question & Answer

### Q: Why not always fanout-on-write?

**Answer:** A celebrity with 50M followers creates huge write amplification.

### Q: Why not always fanout-on-read?

**Answer:** Every feed read would merge many sources and become expensive.

### Q: What if fanout worker crashes?

**Answer:** Kafka retry/replay and idempotent writes.

### Q: How preserve post ordering?

**Answer:** Partition relevant events by author or use explicit sequence numbers; Kafka guarantees ordering only within a partition.

### Q: What if Redis is down?

**Answer:** Read from durable timeline storage or rebuild cache.

## 10. LLD

```text
FeedService
 ├── CandidateSource
 ├── FanoutStrategy
 ├── TimelineRepository
 └── Ranker

FanoutStrategy:
- WriteFanoutStrategy
- ReadFanoutStrategy
- HybridFanoutStrategy

Patterns:
- Strategy → fanout/ranking
- Outbox → event publication
- Event-driven → async fanout
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

If the interviewer asks you to summarize **Twitter / Social Feed**, use this structure:

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
