# Distributed Rate Limiter — Staff/SSE System Design

## 1. Problem Overview

Design a distributed rate-limiting service that protects APIs and backend systems from:

- Excessive traffic
- Abuse
- DDoS-like application traffic
- Accidental retry storms
- Noisy tenants
- Expensive downstream calls

Example:

```text
User U123
→ 100 requests/minute

Tenant T1
→ 10,000 requests/minute

POST /payments
→ 20 requests/second/user
```

The rate limiter must work correctly when thousands of API servers are sending requests concurrently.

The hardest problems are:

1. Distributed counters
2. Very low latency
3. Atomicity
4. Hot keys
5. Expiration
6. High availability
7. Choosing between exact and approximate limiting
8. Handling Redis failure

---

# 2. Functional Requirements

## Core

- Allow/deny requests.
- Configure limits.
- Support per-user limits.
- Support per-IP limits.
- Support per-API limits.
- Support per-tenant limits.
- Support multiple time windows.
- Return remaining quota.
- Return retry-after information.

Example:

```text
User:
100 requests/minute

API:
10,000 requests/minute

Tenant:
1M requests/hour
```

## Optional

- Dynamic limits
- Burst allowance
- Priority users
- Geographic limits
- Adaptive rate limiting
- Distributed quota management
- Admin dashboard

---

# 3. Non-Functional Requirements

Assume:

| Requirement | Target |
|---|---|
| Decision latency | p99 < 5-10 ms |
| Availability | 99.99% |
| Throughput | Millions of decisions/sec |
| Accuracy | Exact for critical APIs |
| Scalability | Horizontal |
| Failure mode | Configurable fail-open/fail-closed |
| Configuration propagation | Seconds or less |

Important statement:

> "The rate limiter sits on the critical request path, so latency and availability are as important as correctness."

---

# 4. Scale Estimation

Assume:

```text
100K API servers
10M requests/sec globally
```

The rate limiter may need to process:

```text
10M decisions/sec
```

Average:

```text
~10M QPS
```

Peak at 3×:

```text
~30M QPS
```

This immediately tells us:

- A single Redis instance is insufficient.
- We need sharding.
- The algorithm must be O(1).
- We need very small payloads.
- Network latency matters.

---

# 5. API Design

## Check Limit

```http
POST /v1/ratelimit/check
```

Request:

```json
{
  "key": "user:U123",
  "api": "POST:/payments",
  "cost": 1
}
```

Response:

```json
{
  "allowed": true,
  "limit": 100,
  "remaining": 87,
  "resetAt": "2026-08-21T10:01:00Z",
  "retryAfterSeconds": 0
}
```

## Configuration

```http
PUT /v1/ratelimit/rules/{ruleId}
```

```json
{
  "scope": "USER",
  "api": "POST:/payments",
  "limit": 100,
  "windowSeconds": 60,
  "algorithm": "TOKEN_BUCKET"
}
```

---

# 6. Core Components

```text
Client
   |
API Gateway
   |
Rate Limiter
   |
Redis Cluster
   |
Backend Service
```

Configuration path:

```text
Admin
  |
Config Service
  |
Config DB
  |
Pub/Sub
  |
Rate Limiter Nodes
```

Monitoring:

```text
Rate Limiter
   |
Metrics
   |
Prometheus/Grafana-like monitoring
```

---

# 7. HLD

```text
                         CLIENT
                            |
                            v
                     +-------------+
                     | Load Balancer|
                     +------+------+
                            |
                            v
                    +---------------+
                    | API Gateway   |
                    +-------+-------+
                            |
                    +-------v--------+
                    | Rate Limiter  |
                    | Middleware    |
                    +-------+--------+
                            |
                            v
                    +---------------+
                    | Redis Cluster |
                    | Sharded       |
                    +-------+-------+
                            |
                      ALLOW / DENY
                            |
                            v
                    +---------------+
                    | Backend APIs  |
                    +---------------+

Configuration:

Admin
  |
  v
Config Service
  |
  v
Config DB
  |
  v
Pub/Sub
  |
  +----> Rate Limiter Node 1
  +----> Rate Limiter Node 2
  +----> Rate Limiter Node N
```

---

# 8. Why Redis?

A rate limiter performs:

```text
read counter
+
update counter
+
possibly expire key
```

for every request.

Redis is a strong fit because:

- In-memory
- Very low latency
- Atomic operations
- TTL support
- Lua scripts
- Horizontal clustering
- High throughput

The core operation can be:

```text
O(1)
```

---

# 9. Why Not PostgreSQL?

PostgreSQL can store rate-limit configuration, but it should not usually be the per-request counter store.

Imagine:

```text
10M requests/sec
```

and every request does:

```text
UPDATE rate_limit_counter
```

This creates:

- Huge write load
- Lock contention
- Network overhead
- Connection pressure

So:

```text
PostgreSQL
→ configuration

Redis
→ hot counters
```

---

# 10. Rate-Limiting Algorithms

The interviewer may ask which algorithm you choose.

Know these four:

1. Fixed Window
2. Sliding Window
3. Sliding Window Log
4. Token Bucket
5. Leaky Bucket

---

# 11. Fixed Window

Example:

```text
100 requests/minute
```

Counter:

```text
10:00 - 10:01 → 100
10:01 - 10:02 → 100
```

Algorithm:

```text
counter[key]++
```

If:

```text
counter > 100
```

reject.

## Pros

- Very simple
- Very fast
- O(1)
- Easy Redis implementation

## Cons

Boundary problem.

Example:

```text
10:00:59 → 100 requests
10:01:01 → 100 requests
```

User can effectively send:

```text
200 requests in ~2 seconds
```

This is called the boundary burst problem.

---

# 12. Sliding Window Log

Store timestamps:

```text
[10:00:01,
 10:00:03,
 10:00:07,
 ...]
```

Remove timestamps older than the window.

Then count remaining requests.

## Pros

- Very accurate

## Cons

- Memory heavy
- Expensive at huge scale
- Many operations per request

Not ideal for extremely high QPS.

---

# 13. Sliding Window Counter

Approximate the sliding window using multiple buckets.

Example:

```text
Current minute
Previous minute
```

Weighted calculation:

```text
current_count
+
previous_count × overlap_percentage
```

## Pros

- More accurate than fixed window
- Much cheaper than storing every timestamp

## Cons

- Approximation
- More complexity

---

# 14. Token Bucket ⭐

This is often the best practical choice when you want both:

- Sustained rate
- Controlled bursts

Imagine a bucket:

```text
Capacity = 100 tokens
Refill = 10 tokens/sec
```

Every request consumes:

```text
1 token
```

If tokens are available:

```text
ALLOW
```

Otherwise:

```text
DENY
```

---

# 15. Token Bucket Example

Start:

```text
100 tokens
```

Requests:

```text
20 requests
```

Remaining:

```text
80
```

After 2 seconds:

```text
+20 tokens
```

Back to:

```text
100
```

But never above bucket capacity.

This allows controlled bursts.

---

# 16. Why Token Bucket?

Suppose an API allows:

```text
100 requests/sec
```

But the client sends:

```text
100 requests immediately
```

A strict fixed-rate limiter may reject bursts.

Token bucket allows:

```text
burst up to bucket capacity
```

while maintaining the average refill rate.

This is useful for APIs.

---

# 17. Token Bucket State

Conceptually store:

```text
key
tokens
last_refill_timestamp
```

For every request:

```text
elapsed = now - last_refill

tokens += elapsed × refill_rate

tokens = min(tokens, capacity)

if tokens >= request_cost:
    tokens -= request_cost
    ALLOW
else:
    DENY
```

This needs to be atomic.

---

# 18. Redis Lua Script

Don't do:

```text
GET
calculate
SET
```

as separate Redis operations.

Two requests can race:

```text
Request A GET → 1 token
Request B GET → 1 token

A consumes
B consumes

Both think they succeeded
```

Instead use:

```text
Redis Lua script
```

to atomically:

```text
read
calculate
update
set TTL
return decision
```

This is a strong interview point.

---

# 19. Redis Key

For a user-level limit:

```text
rl:user:U123:POST:/payments
```

For tenant:

```text
rl:tenant:T123:POST:/payments
```

For IP:

```text
rl:ip:1.2.3.4:POST:/payments
```

Be careful with cardinality.

---

# 20. Multiple Limits

A request may be subject to:

```text
User limit
+
Tenant limit
+
API limit
+
Global limit
```

Example:

```text
User:
100/min

Tenant:
10K/min

API:
1M/min
```

Request is allowed only if:

```text
ALL required limits allow
```

Conceptually:

```text
Request
  |
  +--> User bucket
  |
  +--> Tenant bucket
  |
  +--> API bucket
  |
  v
ALL ALLOW?
 /       \
YES       NO
 |         |
ALLOW     DENY
```

---

# 21. Important Atomicity Question

Suppose:

```text
User bucket → ALLOW
Tenant bucket → DENY
```

We already consumed a user token.

Do we refund it?

There are several options.

### Option 1

Check higher-level/global limits first.

### Option 2

Perform all bucket operations atomically in one Redis Lua script.

This is cleaner when all counters live in the same Redis shard.

But there is a catch:

> Redis Cluster Lua scripts generally require keys involved in the script to be in the same hash slot.

Therefore key design matters.

---

# 22. Redis Cluster and Cross-Shard Atomicity

This is a very useful Staff-level discussion.

Suppose:

```text
user bucket → shard 1
tenant bucket → shard 2
```

A single atomic Redis Lua script cannot simply update both shards as one atomic operation.

Solutions:

### 1. Co-locate related keys

Use Redis hash tags:

```text
{tenant123}:user:U123
{tenant123}:limit
```

Both map to the same hash slot.

### 2. Accept non-atomic multi-step logic

Risk of temporary quota inconsistency.

### 3. Hierarchical rate limiting

Perform local/user limit first, then tenant/global.

Trade-off between accuracy and performance.

---

# 23. Hot Keys

Suppose:

```text
Global API limit
```

has one key:

```text
rl:global:payments
```

Millions of requests hit the same Redis key.

This becomes a hot key.

Solutions:

### Sharded counters

Split:

```text
global:payments:0
global:payments:1
...
global:payments:99
```

But this makes exact counting harder.

### Local token buckets

Each gateway gets a local quota.

Example:

```text
Global limit = 1M/sec

Gateway 1 → 100K
Gateway 2 → 100K
...
```

This reduces Redis traffic but introduces approximation.

---

# 24. Local + Global Rate Limiting

A practical architecture:

```text
Request
   |
Local limiter
   |
   +-- DENY → reject immediately
   |
   +-- ALLOW
         |
         v
     Redis/global limiter
         |
         +-- DENY
         |
         +-- ALLOW
                |
                v
             Backend
```

Benefits:

- Extremely fast local rejection
- Less Redis traffic
- Better resilience

Trade-off:

- Global accuracy becomes harder
- Quota allocation needs careful design

---

# 25. Fail-Open vs Fail-Closed

This is a very important interview question.

Suppose Redis is unavailable.

What do we do?

## Fail Open

```text
Redis unavailable
      ↓
Allow request
```

Pros:

- Backend remains available

Cons:

- System may be overloaded
- Abuse protection temporarily disappears

## Fail Closed

```text
Redis unavailable
      ↓
Reject request
```

Pros:

- Protects backend

Cons:

- Availability suffers

---

# 26. Which Should We Choose?

Depends on API criticality.

### Payment API

Potentially:

```text
Fail closed
```

or conservative local fallback.

### Public read API

Potentially:

```text
Fail open
```

with backend protection.

A strong answer:

> "I wouldn't choose one global policy. I'd make fail-open/fail-closed configurable per API based on the business risk."

---

# 27. Configuration Management

Rate limits shouldn't require restarting services.

Architecture:

```text
Admin
  |
Config Service
  |
Config DB
  |
Pub/Sub
  |
Rate Limiter Nodes
```

Example:

```text
POST /payments
100 req/sec/user
```

Admin changes:

```text
100 → 200
```

Publish:

```text
RateLimitConfigUpdated
```

Gateways update local configuration.

---

# 28. Configuration Cache

Each rate limiter can keep:

```text
local in-memory config cache
```

for:

```text
user rule
API rule
tenant rule
```

This avoids querying the configuration database per request.

The actual counters remain distributed.

---

# 29. API Gateway Integration

Rate limiter can be:

### Inline middleware

```text
API Gateway
 ↓
Rate Limiter
 ↓
Service
```

Best when:

- All APIs need protection
- Centralized policy

### Library/SDK

```text
Service
 ↓
RateLimiter SDK
 ↓
Redis
```

Best when:

- Service-specific limits
- Low network hops

Trade-off:

Central gateway gives consistency; SDK gives flexibility.

---

# 30. Distributed Rate Limiter HLD

```text
                         CLIENTS
                            |
                            v
                     +-------------+
                     | LoadBalancer|
                     +------+------+
                            |
              +-------------+-------------+
              |             |             |
              v             v             v
          Gateway 1      Gateway 2      Gateway N
              |             |             |
          Local Limiter Local Limiter Local Limiter
              |             |             |
              +-------------+-------------+
                            |
                            v
                    +---------------+
                    | Redis Cluster |
                    | Sharded       |
                    +-------+-------+
                            |
                     ALLOW / DENY
                            |
                            v
                    Backend Services


Configuration:
Admin
 |
Config Service
 |
Config DB
 |
Pub/Sub
 |
Gateways
```

---

# 31. Latency Budget

Suppose:

```text
API p99 target = 100ms
```

Rate limiter should consume only:

```text
~1-5ms
```

Possible budget:

```text
Gateway processing     1ms
Redis rate check       1ms
Network overhead       1ms
Backend                80ms
Response               10ms
```

This is why:

> Don't put a slow database query in the rate limiter's critical path.

---

# 32. Rate Limit Headers

Useful response headers:

```text
X-RateLimit-Limit
X-RateLimit-Remaining
X-RateLimit-Reset
Retry-After
```

Example:

```text
HTTP 429 Too Many Requests

Retry-After: 2
```

---

# 33. Retry Storm Problem

Suppose API returns:

```text
429
```

and every client immediately retries.

Traffic gets worse:

```text
Request
 ↓
429
 ↓
Retry
 ↓
429
 ↓
Retry
```

Clients should use:

```text
Exponential Backoff
+
Jitter
```

This is especially important for SDKs.

---

# 34. Distributed System Failure Scenarios

## Redis failure

Choose:

```text
fail-open
or
fail-closed
or
local fallback
```

depending on API.

## Gateway failure

Load balancer routes to another gateway.

## Config Service failure

Use cached configuration.

## Pub/Sub failure

Existing configuration continues; new config propagation may be delayed.

## Redis shard failure

Use Redis Cluster replication/failover.

---

# 35. Observability

Track:

### Traffic

```text
requests/sec
```

### Rate limiting

```text
allowed/sec
denied/sec
429 rate
```

### Redis

```text
latency
CPU
memory
hot keys
errors
```

### Configuration

```text
config propagation delay
```

### Fairness

```text
top users
top tenants
top APIs
```

---

# 36. LLD

## RateLimiter

```java
interface RateLimiter {

    RateLimitDecision check(
        String key,
        int cost
    );
}
```

## Algorithm

```java
interface RateLimitAlgorithm {

    RateLimitDecision check(
        RateLimitState state,
        RateLimitRequest request
    );
}
```

Implementations:

```text
FixedWindowAlgorithm
SlidingWindowAlgorithm
TokenBucketAlgorithm
LeakyBucketAlgorithm
```

This is the Strategy Pattern.

---

# 37. Token Bucket LLD

```java
class TokenBucketState {

    long capacity;
    double tokens;
    long refillRate;
    long lastRefillTime;
}
```

Core logic:

```text
calculate elapsed
       ↓
refill tokens
       ↓
cap at capacity
       ↓
if tokens >= cost
       |
   consume
       |
     ALLOW
else
     DENY
```

---

# 38. Redis Repository

```java
interface RateLimitStore {

    RateLimitState get(String key);

    RateLimitDecision executeAtomically(
        String key,
        RateLimitRequest request
    );
}
```

Implementation:

```text
RedisRateLimitStore
```

uses a Lua script for atomic state transitions.

---

# 39. Strategy Pattern

```text
RateLimitAlgorithm
       |
 +-----+--------+-------------+
 |              |             |
TokenBucket  FixedWindow  SlidingWindow
```

Configuration determines the implementation.

---

# 40. Chain of Responsibility

Multiple limits:

```text
User Limit
   ↓
Tenant Limit
   ↓
API Limit
   ↓
Global Limit
   ↓
Backend
```

Each handler can reject the request.

---

# 41. SOLID

### SRP

Separate:

```text
RateLimiter
Algorithm
Store
ConfigProvider
Metrics
```

### OCP

New algorithms can be added without rewriting the core service.

### DIP

RateLimiter depends on:

```text
RateLimitStore
```

not directly on Redis.

### ISP

Keep configuration/store interfaces focused.

---

# 42. Why Token Bucket vs Fixed Window?

## Fixed Window

Pros:

- Simple
- Fast
- Low memory

Cons:

- Boundary bursts

## Token Bucket

Pros:

- Supports bursts
- Smooth rate control
- O(1)
- Small state

Cons:

- More computation
- Atomic state update required

For a general API gateway:

> **I'd usually choose Token Bucket.**

For very simple low-cost limits:

> Fixed Window can be perfectly adequate.

---

# 43. Why Not Sliding Window Log?

Pros:

- Accurate

Cons:

- Stores every request timestamp
- Memory-heavy
- Expensive at millions of QPS

For huge-scale infrastructure:

> The extra accuracy often isn't worth the cost.

---

# 44. Final Architecture

```text
                          CLIENT
                             |
                             v
                      +--------------+
                      | LoadBalancer |
                      +------+-------+
                             |
                +------------+------------+
                |            |            |
                v            v            v
             Gateway 1    Gateway 2    Gateway N
                |            |            |
             Local        Local        Local
             Bucket       Bucket       Bucket
                |            |            |
                +------------+------------+
                             |
                             v
                     +---------------+
                     | Redis Cluster |
                     | Token Buckets |
                     +-------+-------+
                             |
                       ALLOW / DENY
                             |
                             v
                      Backend APIs


CONFIGURATION
Admin
  |
Config Service
  |
Config DB
  |
Pub/Sub
  |
Gateways

OBSERVABILITY
Gateways + Redis
       |
     Metrics
       |
 Monitoring
```

---

# 45. Staff-Level Trade-offs

### Central Redis vs Local Limiter

Central Redis:

- More accurate
- Global view
- Higher network dependency

Local:

- Extremely fast
- Resilient
- Approximate global quota

### Exact vs Approximate

Exact:

- More coordination
- Higher cost

Approximate:

- Better scale
- Slight quota variance

### Fail Open vs Fail Closed

Fail open:

- Better availability

Fail closed:

- Better protection

### Token Bucket vs Fixed Window

Token Bucket:

- Better burst control

Fixed Window:

- Simpler

---

# 46. Staff-Level Questions

### What if Redis becomes a bottleneck?

- Shard keys
- Redis Cluster
- Local pre-limiting
- Token allocation
- Hot-key mitigation

### What if one user becomes a hot key?

Use local caching/limiting or hierarchical quotas.

### What if Redis is unavailable?

API-specific fail-open/fail-closed policy plus local fallback.

### How do you make the operation atomic?

Redis Lua script.

### What if limits are changed?

Config service + pub/sub + local config cache.

### How do you support multiple limits?

Evaluate user + tenant + API + global policies, ideally atomically where required.

### How do you prevent retry storms?

429 + Retry-After + exponential backoff + jitter.

---

# 47. 30-Second Interview Answer

> "I'd place the rate limiter at the API gateway because it protects downstream services before expensive work happens. For the limiting algorithm, I'd generally choose a token bucket because it supports controlled bursts while enforcing an average rate. The hot counter state would live in a sharded Redis cluster, with the token calculation and update executed atomically through Lua. For high throughput, I'd use local rate limiting at gateways to reject obvious excess traffic and a distributed Redis limit for shared quotas such as tenant or global limits. Configuration would be stored separately and propagated through pub/sub into local caches. If Redis fails, the behavior should be configurable per API—fail-open for availability-sensitive APIs and fail-closed for APIs where overload or abuse is more dangerous. I'd use metrics for rejection rate, Redis latency, hot keys and configuration propagation, and return standard 429/Retry-After headers."

---

# 48. Mental Model

```text
REQUEST
   ↓
LOCAL LIMIT
   ↓
GLOBAL LIMIT
   ↓
REDIS ATOMIC CHECK
   ↓
+---------+
| ALLOW   | → BACKEND
| DENY    | → 429
+---------+

Remember:

ALGORITHM → Token Bucket
STATE → Redis
ATOMICITY → Lua
SCALE → Sharding + Local Limiter
CONFIG → Config Service + Pub/Sub
FAILURE → Open/Closed Policy
CLIENT RETRIES → Backoff + Jitter
OBSERVABILITY → 429 + latency + hot keys
