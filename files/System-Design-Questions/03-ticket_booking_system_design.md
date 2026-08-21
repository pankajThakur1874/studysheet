# Ticket Booking System — Staff/SSE System Design Cheat Sheet

## 1. Problem Overview

Design a large-scale ticket booking system similar to BookMyShow/online event or movie ticket booking.

Users should be able to:

- Search movies/events
- Select city/venue/show
- View available seats
- Temporarily hold seats
- Pay
- Confirm booking
- Cancel booking where allowed
- Receive confirmation/notifications

The hardest part is **concurrent seat booking**:

> Two users must never successfully book the same seat.

---

# 2. Functional Requirements

## Core

1. Search movies/events.
2. Browse venues and shows.
3. View seat layout and availability.
4. Select one or more seats.
5. Temporarily hold seats.
6. Initiate payment.
7. Confirm booking after successful payment.
8. Cancel/refund booking where applicable.
9. Send booking confirmation.
10. View booking history.

## Optional

- Coupons
- Dynamic pricing
- Food booking
- Recommendations
- Waitlist
- Seat upgrade
- Multiple payment providers

For the interview, clearly separate **must-have** from optional features.

---

# 3. Non-Functional Requirements

Assume:

| Requirement | Target |
|---|---|
| Availability | 99.9%+ |
| Seat availability read | p95 < 200 ms |
| Search | p95 < 300 ms |
| Booking correctness | Strong consistency |
| Search/recommendations | Eventual consistency |
| Payment | Idempotent |
| Notifications | Asynchronous |
| Scalability | Horizontal |
| Durability | Confirmed bookings must not be lost |

Important statement:

> "Seat availability can be slightly stale during browsing, but the final booking operation must be strongly consistent."

---

# 4. Scale Estimation

Assume:

- 20M DAU
- 5 searches/user/day
- 100M searches/day
- Average search QPS ≈ 1,157
- Peak at 5× ≈ 5,800 QPS

Booking traffic is much smaller than search traffic.

This suggests:

- Search needs aggressive caching/search infrastructure.
- Booking needs correctness and concurrency control more than raw throughput.

During a blockbuster movie launch, traffic can be extremely bursty.

So we need:

- Rate limiting
- Queueing where appropriate
- Caching
- Horizontal scaling
- Hot-show protection

---

# 5. APIs

## Search

```http
GET /v1/shows?city=Mumbai&date=2026-08-22
```

## Seat Map

```http
GET /v1/shows/{showId}/seats
```

Response:

```json
{
  "showId": "SHOW123",
  "seats": [
    {
      "seatId": "A10",
      "status": "AVAILABLE",
      "price": 250
    },
    {
      "seatId": "A11",
      "status": "BOOKED",
      "price": 250
    }
  ]
}
```

## Hold Seats

```http
POST /v1/holds
```

```json
{
  "showId": "SHOW123",
  "seatIds": ["A10", "A11"],
  "userId": "U123",
  "idempotencyKey": "IDEMP-123"
}
```

## Confirm Booking

```http
POST /v1/bookings
```

```json
{
  "holdId": "H123",
  "paymentId": "P123",
  "idempotencyKey": "BOOK-123"
}
```

## Cancel

```http
POST /v1/bookings/{bookingId}/cancel
```

---

# 6. Core Components

```text
Client
  |
API Gateway
  |
  +-- Search Service
  +-- Show Service
  +-- Seat Service
  +-- Booking Service
  +-- Payment Service
  +-- Notification Service
       |
   PostgreSQL
       |
     Redis
       |
     Kafka
```

Responsibilities:

### API Gateway

- Authentication
- Authorization
- Rate limiting
- Routing
- Request logging

### Search Service

- Search shows
- Filter by city/date/movie
- Uses search index/cache

### Seat Service

- Seat map
- Current availability
- Seat hold

### Booking Service

- Booking state
- Final confirmation
- Cancellation

### Payment Service

- Payment intent
- External payment provider
- Webhook/polling
- Idempotency

### Notification Service

- Email
- SMS
- Push notifications

---

# 7. High-Level Architecture

```text
                         CLIENT
                           |
                           v
                    +--------------+
                    | API Gateway  |
                    | Auth / Rate  |
                    | Limit        |
                    +------+-------+
                           |
             +-------------+-------------+
             |             |             |
             v             v             v
         Search        Seat Service   Booking Service
         Service            |              |
             |              |              |
             v              v              v
       Search Index       Redis        PostgreSQL
                            |              |
                            +-------+------+
                                    |
                                  Outbox
                                    |
                                    v
                                  Kafka
                           +--------+--------+
                           |        |        |
                           v        v        v
                      Payment  Notification Analytics
                      Service    Service
                           |
                           v
                    External Payment
                       Processor
```

---

# 8. Database Choice

## PostgreSQL for booking state

The authoritative booking state should live in a transactional database.

Why?

We need:

- Transactions
- Strong consistency
- Conditional updates
- Constraints
- Reliable state transitions

Example tables:

### shows

```text
show_id
movie_id
venue_id
start_time
end_time
status
```

### seats

```text
seat_id
venue_id
row
seat_number
seat_type
```

### show_seats

```text
show_id
seat_id
status
price
hold_id
hold_expires_at
booking_id
version
```

### bookings

```text
booking_id
user_id
show_id
status
total_amount
created_at
```

### booking_seats

```text
booking_id
show_id
seat_id
```

---

# 9. Why not Cassandra?

Cassandra is excellent for:

- Huge scale
- High write throughput
- Predictable queries
- Distributed availability

But final seat booking has a strong concurrency/transaction requirement.

For the authoritative booking state, PostgreSQL is easier to reason about.

Cassandra could potentially be used for:

- Booking history
- Event streams
- Analytics/read models

if scale requires it.

---

# 10. Why Redis?

Redis is useful for temporary seat holds.

Example:

```text
SET hold:SHOW123:A10 USER123 NX EX 300
```

Meaning:

- Only create if the hold doesn't exist.
- Expire after 300 seconds.

This provides fast coordination.

But:

> Redis is NOT the authoritative booking source of truth.

The database must ultimately enforce:

```text
AVAILABLE -> BOOKED
```

---

# 11. The Critical Problem: Double Booking

Suppose:

```text
User A                    User B

Read A10                  Read A10
   |                         |
AVAILABLE                 AVAILABLE
   |                         |
Book A10                   Book A10
```

Without concurrency control:

```text
A → SUCCESS
B → SUCCESS
```

This is unacceptable.

---

# 12. Recommended Booking Flow

## Step 1 — User selects seats

Client asks:

```text
GET /shows/SHOW123/seats
```

Availability can be cached briefly.

## Step 2 — User requests a hold

```text
POST /holds
```

## Step 3 — Acquire temporary hold

```text
SET hold:SHOW123:A10 USER123 NX EX 300
```

If successful:

```text
A10 → HELD
```

If it fails:

```text
A10 → already held
```

Return an error to the user.

## Step 4 — Persist/validate hold

The DB remains authoritative for the booking lifecycle.

## Step 5 — Payment

```text
Hold
  |
Payment Intent
  |
External Processor
```

## Step 6 — Confirm booking

Final DB transaction:

```text
BEGIN

validate hold
validate seat state
change seat -> BOOKED
create booking
create booking_seats
create outbox event

COMMIT
```

## Step 7 — Async notification

```text
Outbox
  |
Kafka
  |
Notification Service
  |
Email/SMS/Push
```

---

# 13. Why Redis Lock Alone Is Not Enough

This is a common interview trap.

Imagine:

```text
Redis hold succeeds
       |
       v
DB update
       |
       v
DB crashes
```

If Redis is the only source of truth, the system can become inconsistent.

Also consider:

```text
Redis network partition
Redis restart
TTL expiry
process crash
```

Therefore:

> Redis is useful for fast temporary coordination, but the final booking invariant must be enforced by the durable database.

---

# 14. Better Database-Level Protection

A simple approach is an atomic conditional update:

```sql
UPDATE show_seats
SET status = 'BOOKED',
    booking_id = ?
WHERE show_id = ?
  AND seat_id = ?
  AND status = 'HELD'
  AND hold_id = ?;
```

Then inspect:

```text
rows_updated == 1
```

If:

```text
1 -> booking succeeded
0 -> hold invalid/expired/already consumed
```

This is often preferable to relying entirely on distributed locks.

---

# 15. Seat State Machine

```text
AVAILABLE
    |
    | hold
    v
  HELD
    |
    | payment success
    v
 BOOKED

HELD
  |
  | timeout/payment failure
  v
AVAILABLE
```

Invalid transitions should be rejected.

For example:

```text
BOOKED -> HELD
```

should never happen.

This state-machine thinking makes concurrency easier to reason about.

---

# 16. Hold Expiration

Suppose hold duration is 5 minutes.

```text
10:00 → HOLD
10:05 → EXPIRE
```

We should not rely only on a background scheduler.

At booking time, validate:

```text
hold_expires_at > current_time
```

If expired:

```text
reject booking
```

A background cleanup job can later clean stale records.

This gives us correctness even if the cleanup worker is delayed.

---

# 17. Payment Flow

```text
Client
  |
Booking Service
  |
Payment Service
  |
Payment Processor
  |
Webhook
  |
Payment Service
  |
Booking Service
```

Payment states:

```text
CREATED
   |
PROCESSING
   |
 +---+---+
 |       |
SUCCESS FAILED
```

Use:

```text
paymentId
+
idempotencyKey
```

so retrying the payment request doesn't create another payment.

---

# 18. Payment Success But Booking Service Crashes

Example:

```text
Payment Processor
       |
       v
SUCCESS
       |
       v
Booking Service crashes
```

Payment is successful, but booking may still be `PENDING`.

We need:

- Webhook
- Polling
- Reconciliation
- Idempotent booking confirmation

Reconciliation periodically checks:

```text
Payment Processor
       vs
Our Database
```

and resolves mismatches.

This is especially important in payment systems.

---

# 19. Outbox Pattern

Problem:

```text
Booking DB update = SUCCESS
Kafka publish = FAILURE
```

Then notification event is lost.

Use:

```text
BEGIN
    UPDATE booking
    INSERT outbox_event
COMMIT
```

Then:

```text
Outbox Worker
    |
    v
Kafka
```

The business update and event creation become atomic.

Consumers must still be idempotent because Kafka delivery can be at-least-once.

---

# 20. Kafka

Events:

```text
BookingCreated
BookingConfirmed
BookingCancelled
PaymentSuccessful
PaymentFailed
```

Kafka enables:

```text
Booking
  |
  v
Kafka
  |
  +--> Notification
  +--> Analytics
  +--> Loyalty
  +--> Invoice
```

Benefits:

- Decoupling
- Buffering
- Replay
- Independent scaling

Trade-off:

- Eventual consistency
- Duplicate messages
- Operational complexity

---

# 21. Idempotency

Client sends:

```text
POST /bookings
Idempotency-Key: B123
```

Network times out.

Client retries:

```text
POST /bookings
Idempotency-Key: B123
```

The system should return the original result rather than creating another booking.

Store:

```text
idempotency_key
request_hash
response
status
```

with a uniqueness constraint.

---

# 22. Hot Show Problem

This is a Staff-level scaling problem.

Suppose a blockbuster show opens at 10 AM.

```text
1M users
   |
   v
same show
   |
   v
same seat inventory
```

This creates a hot partition/row contention problem.

Solutions:

### 1. Rate limiting

Limit requests per user/IP.

### 2. Virtual waiting room

Users enter a queue before accessing booking.

### 3. Cache seat map

Reduce DB reads.

### 4. Partition traffic

Distribute requests across stateless services.

### 5. Atomic DB operations

Keep the critical section extremely small.

### 6. Short-lived holds

Don't hold seats indefinitely.

---

# 23. Search Architecture

Search is read-heavy.

Use:

```text
PostgreSQL
    |
    v
Kafka / CDC
    |
    v
Elasticsearch/OpenSearch
```

Search request:

```text
Client
  |
Search API
  |
Search Index
```

Do not query the booking database for every search.

Search index can be eventually consistent.

---

# 24. Caching

Cache:

- Movie metadata
- Venue metadata
- Show listings
- Seat layout

Be careful caching actual seat availability for too long.

A stale seat map is acceptable during browsing if the final booking operation revalidates availability.

This gives us:

```text
Fast read
+
Correct final booking
```

---

# 25. Failure Scenarios

## Redis down

Fallback to DB/alternate coordination strategy.

Booking correctness must not depend solely on Redis.

## PostgreSQL down

Don't claim booking success.

Return retryable failure.

## Kafka down

Outbox retains events and publishes later.

## Payment provider down

Timeout + circuit breaker + retry where safe.

## Notification provider down

Retry + DLQ.

## Booking service crashes

Persistent booking/payment state enables recovery.

---

# 26. Scaling the Database

Initially:

```text
PostgreSQL Primary
       |
 +-----+------+
 |            |
Read Replica Read Replica
```

Reads:

```text
Search / browsing → replicas/cache
```

Writes:

```text
Booking → primary
```

If one show/tenant becomes extremely large, consider sharding.

Possible shard key:

```text
show_id
```

because most seat operations are show-specific.

But be careful:

> A single blockbuster show can itself become a hot shard.

You may need specialized partitioning/bucketing or a queue/waiting-room mechanism.

---

# 27. LLD

## Booking Service

```java
interface BookingService {
    HoldResult holdSeats(HoldRequest request);

    BookingResult confirmBooking(ConfirmBookingRequest request);

    void cancelBooking(String bookingId);
}
```

## Seat Inventory

```java
interface SeatInventoryService {
    List<Seat> getSeats(String showId);

    HoldResult hold(String showId, List<String> seatIds, String userId);

    void release(String holdId);

    void book(String holdId);
}
```

## Payment

```java
interface PaymentService {
    PaymentIntent createPayment(CreatePaymentRequest request);

    PaymentStatus getStatus(String paymentId);
}
```

---

# 28. Strategy Pattern

Different pricing strategies:

```text
PricingStrategy
      |
 +----+----+
 |         |
Normal   Dynamic
Pricing  Pricing
```

```java
interface PricingStrategy {
    Money calculatePrice(Show show, List<Seat> seats);
}
```

Useful for:

- Weekend pricing
- Dynamic pricing
- Premium seats
- Coupons

---

# 29. Factory Pattern

```text
PaymentProcessorFactory
        |
 +------+------+ 
 |             |
UPI           Card
 |             |
Processor     Processor
```

The Booking Service doesn't need to know concrete implementations.

---

# 30. Adapter Pattern

External payment providers may expose different APIs:

```text
Our PaymentProcessor
        |
     Adapter
     /     \
Provider A Provider B
```

This isolates external API differences.

---

# 31. SOLID

### SRP

Separate:

```text
BookingService
SeatService
PaymentService
NotificationService
```

### OCP

Add new pricing/payment strategies without changing core booking logic.

### DIP

Depend on:

```text
PaymentProcessor
```

rather than:

```text
RazorpayClient
```

### ISP

Keep interfaces focused.

---

# 32. Final HLD

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
             +------------------+------------------+
             |                  |                  |
             v                  v                  v
        Search Service      Seat Service      Booking Service
             |                  |                  |
             v                  |                  |
     Elasticsearch/OpenSearch   |              PostgreSQL
                                |                  |
                              Redis                |
                         Hold/Cache                |
                                                   |
                                                Outbox
                                                   |
                                                   v
                                                 Kafka
                                           +-------+-------+
                                           |       |       |
                                           v       v       v
                                      Payment  Notify  Analytics
                                      Service  Service
                                           |
                                           v
                                    Payment Provider
                                           |
                                           v
                                        Webhook
                                           |
                                           v
                                    Payment Service
```

---

# 33. The key trade-offs to say in the interview

### PostgreSQL vs Cassandra

> "I'd use PostgreSQL for authoritative booking state because transactional correctness is more important than raw write throughput. Cassandra could be useful for very large historical/read-model workloads."

### Redis vs PostgreSQL

> "Redis provides fast temporary holds and caching, but PostgreSQL remains the source of truth."

### Kafka

> "Kafka decouples booking from notifications, analytics and other consumers, but introduces eventual consistency and requires idempotent consumers."

### Search index

> "Search doesn't need strong consistency, so I can maintain an eventually consistent search index."

### Waiting room

> "For extremely hot shows, I'd introduce a virtual waiting room to control concurrency before traffic reaches the seat inventory."

---

# 34. The Staff-level summary

The design can be summarized as:

```text
SEARCH
→ Elasticsearch + Cache
→ Eventually consistent

SEAT BROWSING
→ Redis + DB
→ Slightly stale is acceptable

SEAT HOLD
→ Redis NX + TTL
→ Short-lived coordination

FINAL BOOKING
→ PostgreSQL transaction
→ Strong consistency

PAYMENT
→ Idempotency
→ Webhook + Polling
→ Reconciliation

EVENTS
→ Outbox → Kafka
→ At-least-once + idempotent consumers

NOTIFICATIONS
→ Async workers

HOT SHOW
→ Rate limiting
→ Waiting room
→ Cache
→ Protect inventory DB
```

---

# 35. The single most important sentence

If the interviewer asks you to explain the core of the system:

> **"The key design principle is to separate the high-volume read path from the correctness-critical booking path. I can tolerate stale data while users browse seats, but when a user actually confirms a booking, the authoritative database must atomically enforce that the seat transitions from the expected state to BOOKED. Redis and Kafka improve performance and decoupling, but they don't replace that source of truth."**
