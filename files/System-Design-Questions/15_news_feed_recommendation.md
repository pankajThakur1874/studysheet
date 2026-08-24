# News Feed / Recommendation System

## 0. Why This Design Matters

Covers candidate generation, ranking, feature pipelines, Kafka, caching, ML fallbacks and two-stage architecture.

## 1. Problem Overview — Explain It Simply

A recommendation system should not score every piece of content for every user. It first generates a manageable set of relevant candidates and then ranks those candidates using user, content and context signals.

## 2. Functional Requirements

- Personalized feed
- Candidate generation
- Ranking
- Pagination
- User feedback
- Freshness
- Fallback feed

## 3. Non-Functional Requirements

- Low feed latency
- High throughput
- High availability
- Personalization quality
- Eventual consistency acceptable

## 4. Capacity Estimation

Example: 100M DAU × 10 feed requests/day = 1B/day ≈ 11.6K average QPS and ≈ 58K at 5×. Ranking all content per request is impossible, so candidate generation is mandatory.

## 5. API Design

```text
GET /v1/feed?cursor=abc&limit=20
POST /v1/events
{
  "event":"LIKE",
  "contentId":"C1"
}
```

## 6. High-Level Architecture

```text
User Events
 ↓
Kafka
 ↓
Stream Processing
 ↓
Feature Store

Feed Request
 ↓
Candidate Generator
 ├── Followed
 ├── Trending
 ├── Similar
 └── Recent
 ↓
100s/1000s candidates
 ↓
Ranker
 ↓
Feed Cache
 ↓
Client
```

### HLD Flowchart

The following is the primary interview flowchart. Draw this first, then explain each box.

```mermaid
flowchart LR
    U[User] --> API[Feed API]
    API --> CG[Candidate Generator]
    CG --> F1[Followed]
    CG --> F2[Trending]
    CG --> F3[Similar]
    CG --> F4[Recent]
    F1 --> R[Ranker]
    F2 --> R
    F3 --> R
    F4 --> R
    R --> C["(Feed Cache)"]
    C --> U
    U --> EV[Behavior Events]
    EV --> K[Kafka]
    K --> FS["(Feature Store)"]
```

## 7. Database Selection

PostgreSQL for account/config metadata. Redis for hot features/feed cache. Cassandra/distributed KV for high-volume activity/timeline-like access. OLAP/data lake for historical analytics and model training.

### HLD Deep-Dive Flowchart

Use this second flowchart when the interviewer asks **"walk me through the complete flow"**.

```mermaid
flowchart TD
    A[Feed request] --> B[Read cached candidates if available]
    B --> C{Cache usable?}
    C -->|Yes| D["Return / refresh asynchronously"]
    C -->|No| E[Candidate generation]
    E --> F["Hundreds/thousands of candidates"]
    F --> G[Feature retrieval]
    G --> H[Ranking model]
    H --> I["Business rules + diversity"]
    I --> J[Cursor pagination]
    J --> K[Return feed]
    L[User behavior] --> M[Kafka]
    M --> N[Feature update]
    N --> G
```

## 8. HLD Deep Dive — Why Each Decision?

### Why Why candidate generation?

Ranking millions of items per request is too expensive. Generate hundreds/thousands first.

### Why Why two-stage ranking?

Use a cheap broad retriever and a more expensive accurate ranker on a small candidate set.

### Why What if ranker is down?

Fallback to deterministic recency/popularity/rule-based ranking.

### Why What is a feature store?

Low-latency serving layer for user/content/context features used by ranking.

### Why Cold start?

Use trending/popular content, onboarding preferences and exploration.

### Why How fresh are features?

Streaming updates improve freshness; some features can tolerate delay depending on business impact.

## 9. Interview Question & Answer

### Q: Why not rank everything?

**Answer:** Computational cost grows with total content; candidate generation reduces it dramatically.

### Q: Feature store unavailable?

**Answer:** Use cached/default features and fallback ranking.

### Q: Kafka down?

**Answer:** Event updates are delayed; feed can continue using existing features.

### Q: Feed cache stale?

**Answer:** Short TTL/partial refresh; recommendation systems generally tolerate some staleness.

## 10. LLD

```text
CandidateGenerator
 ├── FollowedGenerator
 ├── TrendingGenerator
 ├── SimilarGenerator
 └── RecentGenerator

Ranker
 ├── MLRanker
 └── RuleBasedRanker

Patterns:
- Strategy → candidate/ranking
- Pipeline → recommendation stages
- Event-driven → behavior updates
- Cache-aside → feed serving
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

If the interviewer asks you to summarize **News Feed / Recommendation System**, use this structure:

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

---

# 📚 Book Cross-Reference & Added Depth

**Source:** Alex Xu, *System Design Interview* Vol 1, Ch 11 — *Design a News Feed System* (companion note `Book-System-Design-Vol-1/11-design-a-news-feed-system.md`).

The book's core mechanics to weave in:

- **Two feed-generation strategies + hybrid.** Fan-out-on-**write** precomputes each user's feed for fast reads but melts down for celebrities (millions of writes per post). Fan-out-on-**read** is cheap to write but slow to read. **Hybrid** = push for normal users, **pull-and-merge for celebrities** — the standard answer.
- **Feed cache holds IDs only** (`<post_id, user_id>`), not post bodies — content is hydrated separately, keeping the cache small.
- **Web tier is stateless**; auth + rate limiting sit in front; a **fanout service** + message queue does the write-time fan-out work.

```mermaid
flowchart LR
    Post[New post] --> FO[Fanout service]
    FO -->|normal user| FC[("Per-follower feed cache: post IDs")]
    FO -->|celebrity| Skip[Skip fanout - pull at read]
    Read[Feed request] --> FC
    Read --> Cele[Merge celebrity posts]
```

**Interview line:** *"Hybrid fan-out — push for ordinary users, pull for celebrities — with a feed cache that stores only post IDs and hydrates content on read."*
