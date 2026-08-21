# URL Shortener — Staff/SSE System Design

## 1. Problem Overview

Design a URL shortening service similar to Bitly.

A user provides:

```text
https://www.example.com/products/very-long-product-url?id=12345
```

The system returns:

```text
https://short.ly/aB92x
```

When someone opens the short URL:

```text
short.ly/aB92x
```

the system redirects them to the original URL.

The core system therefore has two very different workloads:

```text
WRITE:
Long URL → Short URL

READ:
Short URL → Long URL → Redirect
```

The read path is much heavier than the write path, so the design should optimize for extremely fast reads.

---

# 2. Functional Requirements

## Core

1. Create a short URL.
2. Redirect a short URL.
3. Support expiration.
4. Support custom aliases optionally.
5. Track click statistics optionally.
6. Allow users to manage their URLs optionally.

## Optional

- Custom domain
- Password-protected URLs
- Geo/device-based redirects
- Analytics dashboard
- Abuse detection
- Link editing

For the core interview, focus on:

```text
Create
+
Redirect
+
Expiration
```

---

# 3. Non-Functional Requirements

Assume:

| Requirement | Target |
|---|---|
| Availability | 99.99% |
| Redirect latency | p95 < 50-100ms |
| Create latency | p95 < 200ms |
| Read/write ratio | ~100:1 |
| Durability | Short URLs should not disappear |
| Scalability | Horizontal |
| Redirect consistency | Strong enough that valid URLs resolve correctly |
| Analytics | Eventually consistent |

Important statement:

> "The redirect path is the critical path. I want the short-code lookup to be extremely fast and cacheable."

---

# 4. Scale Estimation

Assume:

```text
100M new URLs/day
10B redirects/day
```

Average writes:

```text
100M / 86,400
≈ 1,157 writes/sec
```

Average reads:

```text
10B / 86,400
≈ 116K redirects/sec
```

Peak at 5×:

```text
≈ 580K redirects/sec
```

This immediately tells us:

```text
Read-heavy system
        ↓
Cache is critical
        ↓
Horizontal scaling
```

---

# 5. API Design

## Create Short URL

```http
POST /v1/urls
```

Request:

```json
{
  "longUrl": "https://example.com/products/123",
  "expiresAt": "2027-01-01T00:00:00Z",
  "customAlias": null,
  "idempotencyKey": "CREATE-123"
}
```

Response:

```json
{
  "shortCode": "aB92x",
  "shortUrl": "https://short.ly/aB92x",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

## Redirect

```http
GET /{shortCode}
```

Response:

```text
HTTP 302
Location: https://example.com/products/123
```

## Get Analytics

```http
GET /v1/urls/{shortCode}/analytics
```

---

# 6. Core Components

```text
Client
  |
API Gateway
  |
URL Service
  |
DB
  |
Redis
  |
Kafka
  |
Analytics Workers
```

Responsibilities:

### API Gateway

- Authentication
- Rate limiting
- Routing
- Abuse protection

### URL Service

- Validate URL
- Generate short code
- Persist mapping
- Resolve short code

### Redis

- Hot URL cache
- Very low latency lookup

### Database

Source of truth:

```text
shortCode → longUrl
```

### Kafka

Async analytics/events:

```text
Redirected
Created
Expired
```

---

# 7. HLD

```text
                           CLIENT
                              |
                              v
                       +--------------+
                       | API Gateway  |
                       | Auth/Rate    |
                       | Limit        |
                       +------+-------+
                              |
                    +---------+----------+
                    |                    |
                    v                    v
              Create URL            Redirect API
                    |                    |
                    v                    v
               URL Service             Redis
                    |                  /    \
                    v                HIT    MISS
               PostgreSQL             |       |
                    |                Return   v
                  Outbox                      DB
                    |                         |
                    v                         v
                  Kafka                   Redis Set
                    |
             +------+------+
             |             |
             v             v
        Analytics      Abuse/Fraud
          Worker          Worker
```

---

# 8. Database Selection

A relational database such as PostgreSQL is a good initial choice.

Why?

The core mapping is simple:

```text
short_code → long_url
```

We need:

- Unique short code
- Durable storage
- Expiration
- User ownership
- Metadata
- Simple point lookups

Schema:

```text
urls
-----------------------------
id
short_code UNIQUE
long_url
user_id
created_at
expires_at
status
```

Index:

```text
UNIQUE(short_code)
```

The redirect query becomes:

```sql
SELECT long_url, expires_at, status
FROM urls
WHERE short_code = ?;
```

---

# 9. Why Not Cassandra?

Cassandra can work at huge scale because this is a simple key-value access pattern:

```text
shortCode → longURL
```

But PostgreSQL is simpler initially and gives:

- Unique constraints
- Transactions
- Easy administration
- Rich metadata queries

At extreme scale, a distributed KV store/Cassandra/DynamoDB-like system becomes a reasonable option.

The key is:

> Start with the simplest storage that satisfies the scale and availability requirements.

---

# 10. Short-Code Generation

This is one of the most interesting parts.

Suppose we use:

```text
Base62
```

Characters:

```text
a-z
A-Z
0-9
```

There are:

```text
62^6 ≈ 56.8 billion
```

possible 6-character codes.

For 7 characters:

```text
62^7 ≈ 3.5 trillion
```

So 7 characters provides a very large namespace.

---

# 11. Option 1 — Auto-Increment ID + Base62

Generate:

```text
ID = 123456
```

Convert:

```text
123456
      ↓
Base62
      ↓
aB92x
```

Advantages:

- Simple
- No collision if ID is unique
- Very fast

Disadvantages:

- Sequential IDs reveal approximate creation volume
- Predictable URLs can enable enumeration

To mitigate predictability, use a non-sequential ID allocation or an encoding/permutation layer.

---

# 12. Option 2 — Random Short Code

Generate:

```text
aB92x
```

Then check:

```text
Does it already exist?
```

If yes:

```text
generate another
```

Advantages:

- Harder to enumerate
- Simple conceptually

Disadvantages:

- Collision probability
- Additional DB lookup
- At high scale, collision handling becomes more important

Use a sufficiently large namespace.

---

# 13. Option 3 — Distributed ID Generator

Use:

```text
Snowflake-like ID
```

Then:

```text
Distributed ID
      ↓
Base62
      ↓
Short Code
```

Advantages:

- Distributed generation
- No central DB sequence bottleneck
- Good scalability

Disadvantages:

- More complexity
- IDs may still be somewhat predictable depending on design

For an interview, a good starting answer is:

> "I'd use a distributed unique ID generator and encode the ID using Base62. If URL enumeration is a security concern, I'd add a reversible permutation/obfuscation layer or use random codes."

---

# 14. Redirect Flow

This is the most important path.

```text
User
 |
 v
GET /aB92x
 |
 v
Load Balancer
 |
 v
URL Service
 |
 v
Redis
 |
 +---- HIT ----> longUrl
 |
 MISS
 |
 v
PostgreSQL
 |
 v
Redis SET
 |
 v
longUrl
 |
 v
HTTP 302
```

---

# 15. Why Redis?

The redirect path may receive:

```text
500K+ requests/sec
```

A database query for every redirect would be expensive.

Cache:

```text
url:aB92x → https://example.com/...
```

Then:

```text
Request
 ↓
Redis
 ↓
HIT
 ↓
Redirect
```

This can make the common path extremely fast.

---

# 16. Cache-Aside

```text
Request
 ↓
Redis
 ↓
HIT → return

MISS
 ↓
DB
 ↓
Redis
 ↓
return
```

This is cache-aside.

For hot URLs:

```text
Redis
 ↓
hundreds of thousands of requests
```

without hitting PostgreSQL.

---

# 17. Cache Stampede

Suppose a very popular URL expires from Redis:

```text
1M requests
      ↓
same cache key
      ↓
all MISS
      ↓
1M DB queries
```

This can overload the database.

Solutions:

### Request coalescing

Only one request fetches the DB.

### Distributed lock

```text
SET lock:url:aB92x worker NX EX 5
```

### TTL jitter

Don't let millions of keys expire simultaneously.

### Background refresh

Refresh hot keys before expiration.

---

# 18. Expiration

Suppose:

```text
expires_at = 2026-12-31
```

The redirect service checks:

```text
now < expires_at
```

If expired:

```text
HTTP 410 Gone
```

or:

```text
404 Not Found
```

depending on API semantics.

Important:

> Cache expiration and business expiration are different.

A Redis key might have:

```text
TTL = 1 hour
```

while the actual URL could remain valid for 1 year.

The business `expires_at` must be checked.

---

# 19. HTTP 301 vs 302

### 301

Permanent redirect.

Browsers/CDNs may cache aggressively.

### 302

Temporary redirect.

More control over redirect behavior.

For a URL shortener where destination may change or analytics/control matters:

> Start with 302 unless permanent semantics are explicitly required.

---

# 20. Analytics

Do NOT make analytics synchronous.

Bad:

```text
Redirect
 ↓
DB update click count
 ↓
return redirect
```

This increases redirect latency.

Instead:

```text
Redirect
 |
 +--> Return immediately
 |
 +--> Publish Redirected event
             |
             v
           Kafka
             |
             v
       Analytics Worker
             |
             v
        Analytics DB
```

The redirect remains fast.

---

# 21. Click Analytics

Event:

```json
{
  "eventId": "E123",
  "shortCode": "aB92x",
  "timestamp": "2026-08-21T10:00:00Z",
  "country": "IN",
  "device": "mobile"
}
```

Consumers can calculate:

```text
clicks/day
clicks/hour
country
device
referrer
```

Analytics can be eventually consistent.

---

# 22. Why Kafka?

Kafka provides:

- Buffering
- Decoupling
- High throughput
- Replay
- Independent consumers

Architecture:

```text
Redirect
   |
Kafka
   |
+--+--------+----------+
|           |          |
v           v          v
Analytics  Fraud     Reporting
```

If analytics is down:

```text
Redirects still work
```

Kafka retains events.

---

# 23. Idempotency

### Create URL

Client sends:

```text
Idempotency-Key: K123
```

Request times out.

Client retries.

Without idempotency:

```text
URL 1 → aB92x
URL 2 → cD12z
```

Potential duplicate creation.

With idempotency:

```text
K123 → existing response
```

Return the original result.

---

# 24. Duplicate Analytics Events

Kafka consumers can see duplicate events.

Use:

```text
eventId
```

and make processing idempotent.

For example:

```text
processed_events
----------------
event_id UNIQUE
```

or use idempotent aggregation strategies.

---

# 25. Hot Key Problem

Suppose:

```text
short.ly/abc
```

becomes viral.

```text
1M requests/sec
       |
       v
same Redis key
```

Redis can handle many reads, but the key becomes hot.

Solutions:

- Local in-process cache
- CDN/edge caching
- Replicated cache
- Request coalescing
- CDN redirect handling

For extremely popular URLs:

```text
Client
 ↓
CDN/Edge
 ↓
Redirect
```

can keep traffic away from application servers.

---

# 26. CDN

A short URL redirect is an excellent candidate for edge caching when semantics permit.

Architecture:

```text
User
 ↓
CDN
 ↓
Cache HIT
 ↓
302
```

Only misses reach our service.

Benefits:

- Lower latency
- Reduced origin traffic
- Better global performance

Trade-off:

- Cache invalidation becomes harder
- Dynamic destination changes need careful TTL control

---

# 27. Abuse and Security

A public URL shortener can be abused for:

- Phishing
- Malware
- Spam
- URL enumeration
- DDoS

Therefore add:

```text
Rate limiting
URL reputation checks
Malware scanning
Domain blocklist
Abuse reporting
Authentication for high-volume creation
```

Creation can be stricter than redirect traffic.

---

# 28. Rate Limiting

At API Gateway:

```text
POST /urls
```

could have:

```text
100 requests/min/user
```

Redirect endpoint should have a different policy because it may receive legitimate high traffic.

Do not apply the same limit to both.

---

# 29. Custom Alias

User asks:

```text
short.ly/paytm
```

Need:

```text
UNIQUE(short_code)
```

Attempt:

```text
INSERT short_code = paytm
```

If uniqueness constraint fails:

```text
409 Conflict
```

This should be enforced by the DB, not just:

```text
SELECT first
then INSERT
```

because concurrent requests can race.

---

# 30. Custom Alias Race

Two users:

```text
A → /paytm
B → /paytm
```

Both check:

```text
not found
```

Both insert.

Without a DB unique constraint:

```text
duplicate
```

With:

```text
UNIQUE(short_code)
```

only one succeeds.

This is a great example of:

> **Prefer database-enforced invariants over application-only checks.**

---

# 31. Sharding

At huge scale, the URL mapping can be sharded by:

```text
hash(short_code)
```

Why?

Redirect lookup is:

```text
shortCode → longUrl
```

So:

```text
hash(shortCode)
      ↓
Shard
```

This gives direct data locality.

Potential architecture:

```text
shortCode
   |
hash
   |
+--+---+---+
|      |   |
S1     S2  S3
```

---

# 32. Replication

Each shard can have:

```text
Primary
  |
  +-- Replica
  +-- Replica
```

Redirect reads can go to replicas if replication lag is acceptable.

But if a newly created URL must be immediately redirectable, read-after-write consistency becomes important.

One approach:

```text
Create URL
 ↓
Primary
 ↓
Immediately available
```

For the first few seconds, route reads appropriately or populate Redis directly after successful creation.

---

# 33. Read-After-Write

User creates:

```text
aB92x
```

Immediately clicks it.

If redirect reads from a lagging replica:

```text
Replica doesn't know aB92x yet
```

Potential failure.

Solution:

```text
Create:
DB primary
+
Redis cache populate
```

Then:

```text
Redirect:
Redis HIT
```

This gives fast read-after-write behavior.

---

# 34. Database Failure

If PostgreSQL is temporarily unavailable:

### Existing popular URLs

Redis/CDN may continue serving them.

### Cache miss

We cannot resolve the URL reliably.

Do not invent a redirect.

Return:

```text
503
```

and recover after DB becomes available.

This demonstrates graceful degradation.

---

# 35. Redis Failure

If Redis goes down:

```text
Redirect
 ↓
DB
```

Performance decreases but correctness remains.

Then:

```text
DB
 ↓
repopulate Redis
```

This is another example of:

> Cache is not source of truth.

---

# 36. Kafka Failure

If analytics events cannot be published:

Options:

### Outbox

Persist redirect event and publish later.

However, don't necessarily put every ultra-high-volume click event synchronously into the primary URL DB, because that can defeat the low-latency goal.

A practical architecture may use:

```text
Redirect
 ↓
local/event buffer
 ↓
Kafka
```

with appropriate durability guarantees.

The core redirect should not depend synchronously on analytics.

---

# 37. LLD

## URL Service

```java
interface UrlService {

    ShortUrl create(CreateUrlRequest request);

    RedirectResult resolve(String shortCode);
}
```

## Code Generator

```java
interface ShortCodeGenerator {

    String generate();
}
```

Implementations:

```text
Base62IdGenerator
RandomCodeGenerator
```

This is a Strategy-style abstraction.

---

# 38. Repository

```java
interface UrlRepository {

    Optional<ShortUrl> findByShortCode(String shortCode);

    ShortUrl save(ShortUrl url);
}
```

This keeps the service independent from PostgreSQL details.

---

# 39. Cache

```java
interface UrlCache {

    Optional<String> get(String shortCode);

    void put(String shortCode, String longUrl, Duration ttl);

    void evict(String shortCode);
}
```

Implementation:

```text
RedisUrlCache
```

---

# 40. Design Patterns

### Strategy

Short-code generation:

```text
ShortCodeGenerator
      |
 +----+----+
Base62   Random
```

### Factory

Select generator based on configuration.

### Adapter

External URL reputation provider:

```text
ReputationProvider
      |
    Adapter
      |
VirusTotal/Other provider
```

### Decorator

Add:

```text
Logging
Metrics
Caching
```

around URL resolution.

### Chain of Responsibility

Creation pipeline:

```text
Authentication
 ↓
RateLimit
 ↓
URL Validation
 ↓
Abuse Check
 ↓
Create
```

---

# 41. SOLID

### SRP

Separate:

```text
UrlService
CodeGenerator
Repository
Cache
AnalyticsPublisher
```

### OCP

Add another code generation strategy without modifying URL service.

### DIP

URL service depends on:

```text
UrlRepository
UrlCache
```

interfaces rather than Redis/PostgreSQL classes.

### ISP

Keep interfaces small and focused.

---

# 42. Final Architecture

```text
                         CLIENT
                            |
                            v
                      +-----------+
                      | CDN/Edge  |
                      +-----+-----+
                            |
                          MISS
                            |
                            v
                     +-------------+
                     | API Gateway |
                     | Auth/Rate   |
                     | Limit       |
                     +------+------+
                            |
              +-------------+-------------+
              |                           |
              v                           v
        Create URL                  Redirect Service
              |                           |
              v                           v
         URL Service                    Redis
              |                       /      \
              v                     HIT       MISS
         PostgreSQL                    |          |
              |                        |          v
           Outbox                     |       PostgreSQL
              |                        |          |
              v                        +----------+
            Kafka                             |
              |                             Redis
        +-----+-----+                         |
        |           |                         v
        v           v                      302
    Analytics     Abuse
      Worker       Worker
```

For extreme global scale:

```text
Client
 ↓
Global CDN
 ↓
Regional URL Service
 ↓
Regional Redis
 ↓
Distributed DB
```

---

# 43. Important Trade-offs

## PostgreSQL

Pros:

- Simple
- Durable
- Unique constraints
- Strong consistency

Cons:

- Eventually needs sharding at extreme scale

## Redis

Pros:

- Very fast
- Perfect for read-heavy cache

Cons:

- Memory cost
- Not source of truth

## Kafka

Pros:

- High throughput
- Replay
- Decoupled analytics

Cons:

- Complexity
- At-least-once duplicates

## CDN

Pros:

- Extremely low latency
- Removes load from origin

Cons:

- Cache invalidation
- Dynamic URLs are harder

---

# 44. Staff-Level Questions You Should Expect

### What if two users request the same custom alias?

DB unique constraint.

### What if Redis goes down?

Fallback to DB.

### What if DB goes down?

Existing cache/CDN hits can continue; cache misses fail safely.

### What if Kafka is down?

Analytics can lag; redirect shouldn't synchronously depend on Kafka.

### What if one URL becomes viral?

CDN + local cache + hot-key mitigation.

### How do you generate unique short codes?

Distributed unique ID + Base62 or sufficiently large random namespace.

### How do you prevent URL enumeration?

Random/obfuscated codes + authorization on management APIs.

### Why 302 instead of 301?

More control and avoids aggressive permanent caching when destination semantics can change.

---

# 45. 30-Second Interview Answer

> "I'd design the URL shortener as a highly read-heavy system. The authoritative mapping between short code and long URL would live in a durable store such as PostgreSQL initially, with a unique index on short_code. I'd generate globally unique IDs and encode them using Base62. The redirect path would first check Redis and ideally a CDN for very hot links, falling back to the database on a cache miss. Analytics would be completely asynchronous through Kafka so it doesn't increase redirect latency. I'd use idempotency for URL creation, database uniqueness for custom aliases, and shard by short_code if the mapping becomes too large. For viral URLs, CDN and local caching would protect the origin from hot-key traffic. Redis and the CDN are optimization layers; the database remains the source of truth."
