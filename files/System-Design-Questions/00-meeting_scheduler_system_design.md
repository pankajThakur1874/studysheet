# 01 - Meeting Scheduler System Design Interview Guide

## 1. Problem Overview

Design a meeting scheduling system similar to Google Calendar/Calendly
where users can:

-   Create calendars and events.
-   Invite participants.
-   Check availability.
-   Schedule one-time or recurring meetings.
-   Prevent double booking.
-   Update/cancel meetings.
-   Notify participants.
-   Handle timezone and daylight-saving changes.
-   Support reminders.
-   Scale to millions of users and high read/write traffic.

### Core engineering challenge

The hardest part is not creating an event. It is **correctly finding
availability and preventing conflicting bookings while keeping the
system scalable and highly available**.

A good design separates:

1.  Durable event state.
2.  Temporary reservation/hold state.
3.  Availability computation.
4.  Notification/reminder processing.
5.  Search/read models.

------------------------------------------------------------------------

# 2. Functional Requirements

## Must-have

### User and calendar

-   Create/update user.
-   Create one or more calendars.
-   Set calendar timezone.
-   Configure working hours.

### Availability

-   Check availability for one or more users.
-   Query a time range.
-   Return available/busy slots.

### Meeting creation

-   Create meeting.
-   Add participants.
-   Set start/end time.
-   Add title/description/location.
-   Generate meeting ID.
-   Optionally generate video-conference information.

### Invitations

-   Send invitations.
-   Accept/decline/tentative.
-   Track participant response.

### Update/cancel

-   Reschedule meeting.
-   Add/remove participants.
-   Cancel meeting.
-   Notify affected participants.

### Reminders

-   Send reminder before meeting.
-   Support configurable reminder times.

## Nice-to-have

-   Recurring meetings.
-   Calendar sharing.
-   Organization-wide calendars.
-   Room/resource booking.
-   External calendar synchronization.
-   Meeting search.
-   Time suggestions.

------------------------------------------------------------------------

# 3. Non-Functional Requirements

For an interview, state assumptions explicitly.

  -----------------------------------------------------------------------
  Requirement                         Target / Design Goal
  ----------------------------------- -----------------------------------
  Availability                        99.9%+

  Availability read latency           p95 \< 200 ms for normal queries

  Meeting creation                    p95 \< 300 ms excluding external
                                      notification providers

  Notification                        Asynchronous; seconds-level delay
                                      acceptable

  Consistency                         Strong consistency for final
                                      booking; eventual consistency for
                                      notifications/search

  Durability                          Confirmed meetings must not be lost

  Scalability                         Horizontal scaling

  Time handling                       Store timestamps in UTC; retain
                                      timezone separately

  Security                            Authentication, authorization,
                                      encryption

  Idempotency                         Required for create/update/cancel
                                      operations

  Recovery                            Retry + reconciliation
  -----------------------------------------------------------------------

### Important interview statement

> "I don't need strong consistency everywhere. The booking invariant
> needs strong consistency, while notifications and search can be
> eventually consistent."

------------------------------------------------------------------------

# 4. Scale Estimation

Assume:

-   100M registered users.
-   20M DAU.
-   5 calendar reads per active user per day.
-   0.5 meeting writes per active user per day.

### Availability reads

20M × 5 = 100M reads/day.

Average QPS:

100M / 86,400 ≈ 1,157 QPS.

At 5× peak:

≈ 5,800 QPS.

### Meeting writes

20M × 0.5 = 10M writes/day.

Average:

≈ 116 writes/sec.

At 5× peak:

≈ 580 writes/sec.

The read path is therefore much heavier than the write path.

### Design consequence

This suggests:

-   Optimize availability reads.
-   Use appropriate indexes/read models.
-   Cache carefully.
-   Keep writes authoritative in the transactional database.
-   Move notifications/reminders to asynchronous processing.

------------------------------------------------------------------------

# 5. API Schema

## Create meeting

`POST /v1/meetings`

### Request

``` json
{
  "organizerId": "U100",
  "title": "Architecture Review",
  "startTime": "2026-08-21T10:00:00Z",
  "endTime": "2026-08-21T11:00:00Z",
  "timezone": "Asia/Kolkata",
  "participants": [
    {
      "userId": "U200",
      "response": "PENDING"
    },
    {
      "userId": "U300",
      "response": "PENDING"
    }
  ],
  "calendarId": "CAL100",
  "idempotencyKey": "7d3e..."
}
```

### Response

``` json
{
  "meetingId": "M100",
  "status": "CONFIRMED",
  "startTime": "2026-08-21T10:00:00Z",
  "endTime": "2026-08-21T11:00:00Z"
}
```

------------------------------------------------------------------------

## Check availability

`POST /v1/availability/search`

``` json
{
  "userIds": ["U100", "U200", "U300"],
  "startTime": "2026-08-21T09:00:00Z",
  "endTime": "2026-08-21T18:00:00Z",
  "durationMinutes": 30
}
```

Response:

``` json
{
  "slots": [
    {
      "startTime": "2026-08-21T12:00:00Z",
      "endTime": "2026-08-21T12:30:00Z"
    }
  ]
}
```

------------------------------------------------------------------------

## Respond to invitation

`POST /v1/meetings/{meetingId}/response`

``` json
{
  "userId": "U200",
  "response": "ACCEPTED"
}
```

------------------------------------------------------------------------

## Cancel meeting

`POST /v1/meetings/{meetingId}/cancel`

Use an idempotency key.

------------------------------------------------------------------------

## Get calendar events

`GET /v1/calendars/{calendarId}/events?start=...&end=...`

------------------------------------------------------------------------

# 6. Core Components

``` text
1. API Gateway
2. Authentication/Authorization
3. Meeting Service
4. Availability Service
5. Calendar Service
6. Notification Service
7. Reminder/Scheduler Service
8. User Service
9. PostgreSQL / MySQL
10. Redis
11. Kafka
12. Search/read model (optional)
13. External notification/video providers
```

------------------------------------------------------------------------

# 7. High-Level Architecture

``` text
                         ┌─────────────────┐
                         │     Clients     │
                         │ Web / Mobile    │
                         └────────┬────────┘
                                  │
                             HTTPS / REST
                                  │
                         ┌────────▼────────┐
                         │  API Gateway    │
                         │ Auth / RateLimit│
                         └────────┬────────┘
                                  │
                  ┌───────────────┼────────────────┐
                  │               │                │
                  ▼               ▼                ▼
          ┌─────────────┐ ┌───────────────┐ ┌──────────────┐
          │   Meeting   │ │ Availability  │ │   Calendar   │
          │   Service   │ │    Service    │ │   Service    │
          └──────┬──────┘ └───────┬───────┘ └──────┬───────┘
                 │                │                │
                 └────────────────┼────────────────┘
                                  │
                         ┌────────▼────────┐
                         │  PostgreSQL     │
                         │ Source of Truth │
                         └────────┬────────┘
                                  │
                         Transaction / Outbox
                                  │
                         ┌────────▼────────┐
                         │     Kafka       │
                         └─────┬─────┬─────┘
                               │     │
                    ┌──────────┘     └──────────┐
                    ▼                           ▼
           ┌─────────────────┐         ┌────────────────┐
           │ Notification    │         │ Reminder       │
           │ Service         │         │ Scheduler      │
           └───────┬─────────┘         └───────┬────────┘
                   │                           │
             Email/Push/SMS               Reminder Queue
```

Redis sits alongside the services for:

-   Temporary holds.
-   Frequently accessed availability/read data.
-   Idempotency records where appropriate.
-   Distributed coordination where truly necessary.

------------------------------------------------------------------------

# 8. Component-by-Component Explanation

## API Gateway

Responsibilities:

-   TLS termination.
-   Authentication.
-   Authorization.
-   Rate limiting.
-   Request validation.
-   Request ID/correlation ID.
-   Routing.
-   Basic observability.

### Why?

We don't want every service implementing authentication, rate limiting
and request tracing independently.

### Pros

-   Centralized security.
-   Consistent rate limiting.
-   Easier observability.

### Cons

-   Can become a bottleneck.
-   Additional network hop.
-   Must be highly available.

Mitigation:

-   Multiple gateway instances.
-   Load balancer.
-   Stateless gateway.

------------------------------------------------------------------------

# 9. Meeting Service

Owns:

-   Meeting lifecycle.
-   Organizer permissions.
-   Participant state.
-   Create/update/cancel.
-   Meeting state transitions.

Example state:

``` text
DRAFT
  ↓
CONFIRMED
  ↓
CANCELLED
```

Participant state:

``` text
PENDING
ACCEPTED
DECLINED
TENTATIVE
```

### Why separate it?

Meeting business rules should not be mixed with notification or
availability logic.

This follows SRP and keeps services independently scalable.

------------------------------------------------------------------------

# 10. Availability Service

This is one of the most important components.

It answers:

> "When are all required participants available?"

Input:

``` text
Users: A, B, C
Window: 9 AM - 6 PM
Duration: 30 min
```

Conceptually:

``` text
A:
09-10 busy
13-14 busy

B:
10-11 busy
13-14 busy

C:
09-10 busy
15-16 busy

Intersection of free time
        ↓
Possible meeting slots
```

### Algorithm

1.  Fetch busy intervals for each user.
2.  Sort intervals by start time.
3.  Merge overlapping intervals per user.
4.  Convert busy intervals to free intervals.
5.  Intersect free intervals across participants.
6.  Return slots matching requested duration.

### Complexity

For `N` participants and `K` intervals:

-   Fetch: dependent on DB/query.
-   Merge: approximately `O(K log K)` per participant if sorting is
    required.
-   Intersection: approximately `O(total intervals)` after sorting.

For normal meeting durations, this is practical.

------------------------------------------------------------------------

# 11. Why not calculate availability directly from every meeting every time?

Suppose a user has 10,000 historical/future events.

Every availability request would scan them.

That creates unnecessary work.

Instead:

-   Query only the requested time range.
-   Use indexes.
-   Optionally maintain a compact availability/read model for high-scale
    workloads.
-   Cache carefully.

------------------------------------------------------------------------

# 12. Database Selection

## Why PostgreSQL as the primary database?

Meeting creation needs strong consistency.

Example:

Two requests arrive simultaneously:

``` text
User A books 10:00-11:00
User B books 10:00-11:00
```

If the same calendar/resource cannot have overlapping meetings, the
final booking decision must be authoritative.

PostgreSQL provides:

-   ACID transactions.
-   Row-level locking.
-   Unique constraints.
-   Range/exclusion constraints depending on database capabilities.
-   Rich querying.
-   Indexes.

### Why not Cassandra as primary?

Cassandra is excellent for:

-   Massive write volume.
-   Predictable queries.
-   Horizontal scale.

But booking requires transactional conflict handling, making a
relational database a better default.

### Why not MongoDB?

MongoDB can support transactions and flexible documents, but the core
meeting/availability problem is relational and interval-oriented.
PostgreSQL gives stronger relational/query primitives for this workload.

------------------------------------------------------------------------

# 13. Meeting Data Model

## users

``` text
users
-----
id PK
name
email
timezone
created_at
```

## calendars

``` text
calendars
---------
id PK
owner_id
timezone
name
created_at
```

## meetings

``` text
meetings
--------
id PK
calendar_id
organizer_id
title
start_time_utc
end_time_utc
status
created_at
updated_at
version
```

## meeting_participants

``` text
meeting_participants
--------------------
meeting_id
user_id
response
created_at

PK(meeting_id, user_id)
```

## outbox_events

``` text
outbox_events
-------------
id PK
aggregate_id
event_type
payload
created_at
published_at
```

------------------------------------------------------------------------

# 14. Critical Indexes

For availability:

``` text
(calendar_id, start_time_utc)
```

Potentially:

``` text
(user_id, start_time_utc)
```

depending on access pattern.

The goal is to avoid scanning all meetings.

------------------------------------------------------------------------

# 15. Timezone Handling

This is a classic interview trap.

### Store:

``` text
startTime = UTC
endTime = UTC
timezone = user's/calendar's IANA timezone
```

Example:

``` text
start = 2026-08-21T04:30:00Z
timezone = Asia/Kolkata
```

Do NOT store only:

``` text
10:00 AM
```

because 10 AM depends on timezone and DST rules.

### Why retain timezone?

For recurring events:

> "Every Monday at 10 AM America/New_York"

The meaning of 10 AM depends on timezone/DST.

------------------------------------------------------------------------

# 16. Meeting Creation --- Deep Dive

This is the most important flow.

``` text
Client
  ↓
POST /meetings
  ↓
API Gateway
  ↓
Meeting Service
  ↓
Validate request
  ↓
Check authorization
  ↓
Check idempotency
  ↓
Check availability
  ↓
BEGIN DB TRANSACTION
  ↓
Validate conflict again
  ↓
Create meeting
  ↓
Create participants
  ↓
Create outbox event
  ↓
COMMIT
  ↓
Return meeting
```

Then asynchronously:

``` text
Outbox
  ↓
Kafka
  ↓
Notification Service
  ↓
Email / Push
```

------------------------------------------------------------------------

# 17. Why check availability twice?

This is a subtle but important point.

Suppose:

``` text
Request A → check availability → FREE
Request B → check availability → FREE
```

Both see the same state.

Then both attempt to book.

Therefore:

> **A read/check alone doesn't guarantee correctness.**

The final booking operation must enforce the invariant atomically.

This is why the DB transaction/locking/constraint is important.

------------------------------------------------------------------------

# 18. Preventing Double Booking

There are several approaches.

## Option A --- Pessimistic locking

Lock the relevant calendar/resource rows.

``` text
BEGIN
SELECT ... FOR UPDATE
check conflict
INSERT meeting
COMMIT
```

### Pros

-   Strong correctness.
-   Easy mental model.

### Cons

-   Lock contention.
-   Can reduce throughput for hot resources.

------------------------------------------------------------------------

## Option B --- Optimistic locking

Meeting/calendar has:

``` text
version = 10
```

Update only if version remains 10.

### Pros

-   Better concurrency when conflicts are rare.

### Cons

-   More retries.
-   Doesn't directly solve all interval-overlap problems.

------------------------------------------------------------------------

## Option C --- Database constraint

Where supported, use an exclusion/range constraint to prevent
overlapping intervals.

### Pros

-   Correctness enforced by DB.
-   Removes race-condition windows.

### Cons

-   Database-specific.
-   More complex to model for multiple resources/participants.

### Interview answer

> "I'd prefer enforcing the booking invariant at the database layer,
> using a transaction plus an appropriate locking or exclusion
> constraint. Redis can reduce contention but shouldn't be the only
> correctness mechanism."

------------------------------------------------------------------------

# 19. Redis Usage

Redis is NOT the primary meeting database.

Good uses:

### Availability cache

``` text
availability:user123:2026-08-21
```

### Temporary booking hold

For example, for a room/resource:

``` text
SET hold:room:R10:slot:S1 owner123 NX EX 300
```

### Idempotency

``` text
idempotency:create-meeting:key
```

with TTL.

### Distributed coordination

Only when necessary.

------------------------------------------------------------------------

# 20. Why not use Redis for all meeting data?

Because Redis is primarily an in-memory system.

If used as the sole authoritative store:

-   Durability model becomes more complex.
-   Large historical datasets become expensive.
-   Complex queries become harder.
-   Data lifecycle becomes difficult.

Therefore:

``` text
PostgreSQL → durable source of truth
Redis → fast/temporary layer
```

------------------------------------------------------------------------

# 21. Outbox Pattern

Meeting creation has a classic distributed consistency problem.

We need:

``` text
Meeting saved
+
Notification event published
```

If we do:

``` text
DB commit
Kafka publish
```

and Kafka fails:

``` text
Meeting exists
Notification event lost
```

Instead:

``` text
BEGIN
  INSERT meeting
  INSERT participants
  INSERT outbox event
COMMIT
```

Then:

``` text
Outbox Worker
    ↓
Kafka
    ↓
Notification
```

### Pros

-   Reliable event publication.
-   DB state and event creation are atomic.

### Cons

-   Additional table/worker.
-   Events may be delivered more than once.
-   Consumers must be idempotent.

------------------------------------------------------------------------

# 22. Notification Service

Consumes:

``` text
MeetingCreated
MeetingUpdated
MeetingCancelled
ParticipantResponded
```

Then sends:

``` text
Email
Push
SMS
```

### Why asynchronous?

The user doesn't need to wait for email provider latency.

Instead:

``` text
Create meeting
 ↓
Persist
 ↓
Return success
```

Then:

``` text
Kafka
 ↓
Notification
```

### Pros

-   Lower API latency.
-   Independent scaling.
-   Provider failures don't block meeting creation.

### Cons

-   Notifications aren't immediately guaranteed.
-   Requires retries and DLQ.

------------------------------------------------------------------------

# 23. Reminder Scheduler

Suppose meeting starts at:

``` text
10:00 AM
```

Reminder:

``` text
09:45 AM
```

We should not rely on the API request staying alive for 15 minutes.

Store:

``` text
reminder_time
meeting_id
status
```

Then scheduler finds due reminders.

At scale:

``` text
Reminder DB
 ↓
Scheduler
 ↓
Queue
 ↓
Reminder Workers
 ↓
Push/Email
```

### Important

Reminder processing should be **idempotent**.

If worker retries:

``` text
Reminder R123
```

should not send 5 identical notifications.

------------------------------------------------------------------------

# 24. Recurring Meetings

Do not create 10 years × 52 events upfront unless requirements justify
it.

Store:

``` text
series_id
recurrence_rule
timezone
start_time
end_time
```

Example:

``` text
RRULE:
Every Monday at 10 AM
```

Then materialize occurrences as needed or within a rolling horizon.

### Why?

-   Less storage.
-   Easier updates to future occurrences.
-   Better handling of recurrence changes.

------------------------------------------------------------------------

# 25. Search

Search isn't part of the critical booking path.

If users need:

> "Find all meetings containing 'architecture'."

Don't make the primary DB do expensive full-text search at scale.

Use:

``` text
PostgreSQL
   ↓
Outbox/Kafka
   ↓
Search Index
```

For example:

``` text
OpenSearch/Elasticsearch
```

This is an eventually consistent read model.

------------------------------------------------------------------------

# 26. External Calendar Sync

Suppose we integrate Google Calendar/Microsoft Calendar.

Do NOT make external synchronization part of the critical transaction.

Bad:

``` text
Create meeting
 ↓
Google API
 ↓
Microsoft API
 ↓
Return
```

If Microsoft takes 5 seconds, your API takes 5 seconds.

Better:

``` text
Meeting DB
 ↓
Kafka
 ↓
Calendar Sync Worker
 ↓
External Calendar
```

Handle:

-   retries
-   rate limits
-   OAuth expiration
-   duplicate events
-   reconciliation

------------------------------------------------------------------------

# 27. What if external calendar says the user is busy?

This becomes a consistency problem.

Your local calendar might say:

``` text
10:00 FREE
```

but Google Calendar says:

``` text
10:00 BUSY
```

Therefore:

> External availability should be treated as an input/read model, not
> assumed to be perfectly synchronized.

You can periodically sync and/or query the external provider when strict
freshness is required.

------------------------------------------------------------------------

# 28. Scaling

## API layer

Stateless services:

``` text
Load Balancer
    ↓
Meeting Service × N
Availability Service × N
```

Easy horizontal scaling.

## Database

Start:

``` text
PostgreSQL primary
+
read replicas
```

But booking writes go to primary.

At very large scale:

``` text
Shard by calendar_id / tenant_id
```

depending on access patterns.

### Important

Don't shard immediately.

Say:

> "I'd start with a single transactional database and read replicas.
> Once the data/write volume exceeds a single-node boundary, I'd shard
> based on the dominant access pattern."

------------------------------------------------------------------------

# 29. Read path optimization

Availability is read-heavy.

Possible architecture:

``` text
Availability Request
        ↓
Redis cache
        ↓
MISS
        ↓
Availability DB/read model
        ↓
Compute
        ↓
Cache
```

But cached availability has a correctness issue.

If a meeting is created:

``` text
Cache says FREE
DB says BUSY
```

Therefore cache must be invalidated/updated when meetings change.

For critical booking:

> Always validate against authoritative state before final commit.

------------------------------------------------------------------------

# 30. Failure Scenarios

## DB unavailable

-   Booking cannot safely commit.
-   Return temporary failure.
-   Do not claim booking succeeded.

## Redis unavailable

-   Availability may become slower.
-   Fall back to DB.
-   Temporary holds may need a DB-backed path.

## Kafka unavailable

Outbox retains events.

``` text
DB
 ↓
Outbox
 ↓
Kafka unavailable
 ↓
Retry later
```

Meeting creation can still succeed if the meeting transaction itself
succeeded.

## Notification provider unavailable

-   Retry with backoff.
-   DLQ after retry limit.
-   Reconciliation/manual replay.

## Scheduler crashes

Use leases/claims:

``` text
PENDING
 ↓
CLAIMED
 ↓
RUNNING
```

If worker dies and lease expires:

``` text
RUNNING
 ↓
lease expires
 ↓
PENDING/RETRY
```

------------------------------------------------------------------------

# 31. Observability

Track:

### Metrics

-   Availability API p50/p95/p99.
-   Meeting creation latency.
-   DB latency.
-   Cache hit rate.
-   Kafka consumer lag.
-   Notification success/failure.
-   Reminder delay.
-   Booking conflicts.

### Logs

Use:

``` text
requestId
meetingId
userId
eventId
```

### Tracing

Trace:

``` text
Gateway
 → Meeting Service
 → DB
 → Kafka
 → Notification
```

------------------------------------------------------------------------

# 32. Security

-   OAuth/JWT authentication.
-   Organizer/participant authorization.
-   Users can only modify meetings they own or have permission to
    modify.
-   Encrypt data in transit.
-   Encrypt sensitive data at rest.
-   Audit changes.
-   Avoid exposing private calendar details in availability APIs.

For availability, you may return:

``` text
BUSY
```

instead of:

``` text
Meeting title = "Confidential Acquisition Discussion"
```

------------------------------------------------------------------------

# 33. LLD

Now move from distributed architecture to code-level design.

## Main interfaces

``` java
interface MeetingService {
    Meeting createMeeting(CreateMeetingRequest request);
    Meeting updateMeeting(UpdateMeetingRequest request);
    void cancelMeeting(String meetingId);
}

interface AvailabilityService {
    List<TimeSlot> findAvailableSlots(
        List<String> userIds,
        TimeRange range,
        Duration duration
    );
}

interface NotificationService {
    void notifyMeetingCreated(Meeting meeting);
    void notifyMeetingUpdated(Meeting meeting);
    void notifyMeetingCancelled(Meeting meeting);
}
```

------------------------------------------------------------------------

# 34. Domain Objects

``` java
class Meeting {
    String id;
    String organizerId;
    String calendarId;
    String title;
    Instant startTime;
    Instant endTime;
    MeetingStatus status;
    List<Participant> participants;
    long version;
}
```

``` java
class Participant {
    String userId;
    ParticipantResponse response;
}
```

``` java
class TimeSlot {
    Instant start;
    Instant end;
}
```

------------------------------------------------------------------------

# 35. Repository Layer

``` java
interface MeetingRepository {
    Optional<Meeting> findById(String meetingId);

    List<Meeting> findOverlappingMeetings(
        String calendarId,
        Instant start,
        Instant end
    );

    Meeting save(Meeting meeting);
}
```

The repository hides database implementation details.

------------------------------------------------------------------------

# 36. Availability Strategy

If availability rules can vary, use Strategy Pattern.

``` text
AvailabilityStrategy
       ↑
 ┌─────┼────────┐
Standard  WorkingHours  ExternalCalendar
```

``` java
interface AvailabilityStrategy {
    List<TimeSlot> findAvailableSlots(
        AvailabilityRequest request
    );
}
```

This follows:

-   Strategy Pattern.
-   Open/Closed Principle.
-   Dependency Inversion.

------------------------------------------------------------------------

# 37. Meeting Creation LLD Flow

``` text
createMeeting()
    ↓
validate request
    ↓
authorize organizer
    ↓
check idempotency
    ↓
check availability
    ↓
BEGIN TRANSACTION
    ↓
lock / enforce booking invariant
    ↓
create meeting
    ↓
create participants
    ↓
create outbox event
    ↓
COMMIT
    ↓
return response
```

Then asynchronously:

``` text
Outbox
 ↓
Kafka
 ↓
Notification Worker
```

------------------------------------------------------------------------

# 38. Important Race Condition

Two requests:

``` text
A → check availability → FREE
B → check availability → FREE
```

Both proceed.

Therefore:

``` text
availability check
```

is not enough.

The final write must enforce:

``` text
NO OVERLAPPING BOOKING
```

inside the authoritative transactional boundary.

This is one of the most important points in the entire design.

------------------------------------------------------------------------

# 39. Idempotency

Client sends:

``` text
Idempotency-Key: ABC123
```

First request:

``` text
ABC123
 ↓
create M100
```

Client times out.

Retries:

``` text
ABC123
 ↓
find existing result
 ↓
return M100
```

No duplicate meeting.

------------------------------------------------------------------------

# 40. Design Patterns Used

### Strategy

Different availability algorithms.

### Factory

Create notification provider:

``` text
EmailNotification
PushNotification
SMSNotification
```

### Adapter

External calendar integrations:

``` text
CalendarProvider
   ↑
GoogleAdapter
MicrosoftAdapter
```

### Observer/Event-driven

Meeting created:

``` text
MeetingCreated
 ↓
Notification
Reminder
Analytics
Sync
```

### Builder

Useful for complex meeting objects.

------------------------------------------------------------------------

# 41. SOLID Applied

### Single Responsibility

``` text
MeetingService
AvailabilityService
NotificationService
ReminderService
```

### Open/Closed

Add:

``` text
GoogleCalendarProvider
```

without rewriting MeetingService.

### Liskov

All `CalendarProvider` implementations should behave according to the
same contract.

### Interface Segregation

Don't create one giant:

``` text
CalendarEverythingService
```

Use focused interfaces.

### Dependency Inversion

``` text
MeetingService
     ↓
CalendarProvider
     ↑
GoogleCalendarAdapter
```

------------------------------------------------------------------------

# 42. What I would NOT do

## Don't put everything in one service

Bad:

``` text
MeetingService
 ├── Availability
 ├── Notification
 ├── Reminder
 ├── Calendar Sync
 └── Search
```

This becomes difficult to scale and maintain.

## Don't use Redis as source of truth

Use it for:

-   cache
-   temporary state
-   coordination

## Don't synchronously call email/Google/Microsoft

Keep external integrations asynchronous where possible.

## Don't rely only on availability read

Final booking needs atomic correctness.

## Don't shard immediately

Start simple, measure, then scale.

------------------------------------------------------------------------

# 43. Final Architecture

``` text
                         CLIENT
                           │
                           ▼
                     API GATEWAY
                  Auth / Rate Limit
                           │
             ┌─────────────┼─────────────┐
             ▼             ▼             ▼
        Meeting Svc   Availability Svc  Calendar Svc
             │             │             │
             └─────────────┼─────────────┘
                           ▼
                      PostgreSQL
                    SOURCE OF TRUTH
                           │
                     ┌─────┴─────┐
                     │  Outbox   │
                     └─────┬─────┘
                           ▼
                         Kafka
                    ┌──────┼──────┐
                    ▼      ▼      ▼
              Notification Reminder Sync
                 Service    Service Service
                    │          │       │
                  Email      Queue   External
                  Push               Calendar

        Redis
        ├── Availability cache
        ├── Idempotency
        ├── Temporary holds
        └── Ephemeral coordination
```

------------------------------------------------------------------------

# 44. Interview Summary

If the interviewer gives you **"Design a Meeting Scheduler"**, your
answer should revolve around four things:

### 1. Availability

> Efficiently calculate common free slots.

### 2. Correctness

> Prevent overlapping bookings under concurrent requests.

### 3. Time

> UTC storage + timezone-aware recurrence.

### 4. Async processing

> Kafka + outbox for notifications, reminders and integrations.

The strongest sentence to remember:

> **"Availability is a read operation, but booking is a
> correctness-critical write operation. I can optimize availability with
> caching/read models, but the final booking decision must be enforced
> atomically against the authoritative database."**

------------------------------------------------------------------------

# 45. 2-Minute Interview Version

If the interviewer says, "Give me a high-level design," you can say:

> "I'd expose REST APIs through an API gateway, with stateless Meeting
> and Availability services. PostgreSQL would be the source of truth
> because creating a meeting requires transactional conflict handling.
> Availability queries would use indexed time-range queries and
> potentially Redis/read-model caching, while final booking would always
> validate against the authoritative database. After the meeting
> transaction commits, I'd write an outbox event in the same transaction
> and asynchronously publish it to Kafka. Notification, reminder and
> external-calendar synchronization would consume those events
> independently. I'd use Redis for ephemeral state such as temporary
> holds, caching and idempotency, not as the source of truth. I'd store
> timestamps in UTC and retain IANA timezone information for recurring
> events. At larger scale I'd horizontally scale stateless services, add
> read replicas, and eventually shard based on calendar/tenant access
> patterns."

That is the core answer I would aim to deliver in an interview.
