# Tier 5 — High-Scale Architecture

## Goal

Learn how to design systems when traffic, data volume, or concurrency grows by 10x or 100x.

Topics:

1. Traffic estimation
2. Stateless services
3. Horizontal scaling
4. Load balancing
5. Caching
6. Read scaling
7. Write scaling
8. Database sharding
9. Hot rows
10. Hot partitions
11. Rate limiting
12. Load shedding
13. Queues
14. CDN
15. Asynchronous processing
16. Capacity planning

---

# 1. Start With Numbers

Never design blindly.

Estimate:

```text
Requests/sec
Reads/sec
Writes/sec
Average request size
Peak traffic
Storage/day
Retention
Bandwidth
```

Example:

```text
10M users
1M daily active
10 requests/user/day

≈ 10M requests/day
≈ 116 req/sec average
```

If peak is 10x:

```text
≈ 1,160 req/sec peak
```

The exact numbers are estimates, but they expose bottlenecks.

---

# 2. Stateless Application Servers

Prefer:

```text
Load Balancer
 ├── App 1
 ├── App 2
 ├── App 3
 └── App 4
```

If application servers are stateless, add/remove instances easily.

Avoid storing session state only in local memory when requests can land on any instance.

Use external/shared state where required.

---

# 3. Horizontal Scaling

Vertical:

```text
4 CPU → 16 CPU
```

Horizontal:

```text
1 instance
 ↓
10 instances
```

Horizontal scaling is usually more flexible for stateless workloads.

But downstream systems can become the bottleneck.

---

# 4. Load Balancer

Distributes requests across instances.

Potential strategies:

- round robin
- least connections
- weighted
- consistent hashing for specific use cases

Health checks remove unhealthy instances.

---

# 5. Cache

If 90% of reads are repeated:

```text
Application
 ↓
Cache
 ↓ miss
DB
```

can dramatically reduce DB load.

But understand:

- invalidation
- staleness
- stampede
- hot keys
- cache failure

---

# 6. Read Scaling

If reads dominate:

```text
Primary
   ↓ replication
 ┌─┴────────┐
 ↓          ↓
Replica 1 Replica 2
```

Route suitable reads to replicas.

But replication lag means a recently written value may not immediately appear on a replica.

For read-after-write requirements, route accordingly.

---

# 7. Write Scaling

When one DB cannot handle writes:

Options:

- batching
- partitioning
- sharding
- event-driven processing
- workload separation

Sharding distributes data:

```text
Users
 ↓
shard(userId)
 ├── DB1
 ├── DB2
 └── DB3
```

---

# 8. Sharding

Choose a shard key carefully.

Good shard key:

- high cardinality
- evenly distributed
- commonly present in queries

Bad shard key:

```text
country
```

if 80% of users are in one country.

Could create a hot shard.

---

# 9. Hot Row

Suppose:

```text
account:123
```

receives 100K updates/sec.

All updates target one row.

Adding DB servers may not solve it.

Possible approaches:

- serialize updates through a queue
- partition counters
- distribute writes across buckets
- redesign aggregation
- batch updates
- use atomic database primitives

---

# 10. Distributed Counter

Instead of one counter:

```text
views = 1 billion
```

split:

```text
views:0
views:1
...
views:99
```

Writes distribute.

Read:

```text
SUM(all buckets)
```

Trade-off:

- more reads
- eventual aggregation
- complexity

---

# 11. Rate Limiting

Protect system capacity.

Common models:

### Fixed window

Simple but boundary bursts can occur.

### Sliding window

More accurate but more expensive.

### Token bucket

Tokens accumulate at a fixed rate.

Each request consumes a token.

Good for controlling average rate while allowing bounded bursts.

### Leaky bucket

Controls output rate more strictly.

---

# 12. Load Shedding

When capacity is exceeded:

```text
Incoming = 100K/sec
Capacity = 20K/sec
```

Do not let the system queue forever.

Reject or degrade lower-priority work.

Example:

```text
Critical payment → accept
Recommendation → disable
Analytics → defer
```

This is graceful degradation.

---

# 13. Backpressure

If producer is faster than consumer:

```text
Producer = 100K/sec
Consumer = 20K/sec
```

Use bounded queues and/or Kafka.

Monitor:

- queue depth
- oldest item age
- lag
- processing rate

An infinite queue only delays failure.

---

# 14. Async Processing

For long-running work:

```text
API
 ↓
Queue
 ↓
Worker
 ↓
DB/external service
```

API returns quickly.

Benefits:

- decoupling
- buffering
- controlled concurrency
- better resource utilization

Trade-off:

- eventual consistency
- harder user experience
- retries/idempotency required

---

# 15. CDN

For global static/cacheable content:

```text
User
 ↓
CDN
 ↓ miss
Origin
```

Reduces:

- origin traffic
- latency
- bandwidth

Useful for:

- images
- static files
- public content

---

# 16. Database Bottleneck

When DB becomes the bottleneck, ask:

```text
Reads or writes?
CPU or I/O?
Query or connection pool?
One table or whole DB?
One key/row or broad load?
```

Possible solutions:

```text
Query optimization
Indexes
Caching
Read replicas
Partitioning
Sharding
Batching
Archival
Capacity increase
```

---

# 17. Capacity Planning

Suppose:

```text
Current = 10K/sec
Growth = 3x/year
```

Don't provision exactly 10K.

Account for:

- peak traffic
- failure headroom
- replication
- deployment
- traffic bursts
- noisy neighbors

A system should have enough spare capacity to survive expected failures without immediately collapsing.

---

# 18. High-Scale Design Pattern

```text
                    CDN
                     ↓
                Load Balancer
                     ↓
             Stateless Services
              /       |       \
             ↓        ↓        ↓
          Cache     Kafka    Rate Limit
             ↓        ↓
             DB     Workers
             ↓
      Read Replicas / Shards
```

Each component solves a different bottleneck.

---

# Strong Interview Framework

When asked:

> "How do you scale this to 10x?"

Answer:

```text
1. Estimate traffic
2. Find current bottleneck
3. Make app stateless
4. Horizontal scale
5. Cache reads
6. Scale DB reads
7. Partition/shard writes if needed
8. Async expensive work
9. Add rate limiting/backpressure
10. Load shed during overload
11. Monitor and capacity plan
```

## Memorize

> Scaling one component can simply move the bottleneck. Always ask what the next bottleneck will be.
