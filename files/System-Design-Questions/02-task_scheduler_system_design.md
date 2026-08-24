# Distributed Task Scheduler — Staff/SSE System Design

## 1. Problem Overview

Design a distributed task/job scheduling system similar to:

- AWS EventBridge Scheduler
- Quartz at large scale
- Cron-as-a-service
- Internal platform used to schedule background jobs

Users/services should be able to create jobs such as:

- Run once at a specific time
- Run repeatedly using a cron expression
- Retry failed jobs
- Cancel/reschedule jobs
- Track execution status
- Execute jobs reliably even when workers crash

Example:

```text
Send invoice reminder
→ Every day at 9:00 AM

Generate report
→ Every Monday at 10:00 AM

Process payment reconciliation
→ Every 5 minutes

Delete expired data
→ Once at midnight
```

The hardest problems are:

1. **How do we find jobs that are due?**
2. **How do we prevent the same job from being executed concurrently by multiple workers?**
3. **What happens when a worker crashes after starting a job?**
4. **How do we scale to millions/billions of scheduled jobs?**
5. **How do we provide reliable execution without requiring exactly-once infrastructure?**

---

# 2. Functional Requirements

## Core Requirements

### Create Job

User can create:

```text
jobId
schedule
payload
destination/handler
retry policy
```

### One-time Job

Example:

```text
2026-08-22 10:00 UTC
```

### Recurring Job

Example:

```text
0 10 * * MON
```

### Execute Job

When due:

```text
Scheduler
→ Queue
→ Worker
→ Execute
```

### Retry

If execution fails:

```text
Attempt 1
   ↓
Attempt 2
   ↓
Attempt 3
   ↓
DLQ
```

### Cancel

```text
SCHEDULED → CANCELLED
```

### Reschedule

Change:

```text
next_run_at
```

### Execution History

Track:

```text
RUNNING
SUCCESS
FAILED
RETRYING
```

---

# 3. Non-Functional Requirements

Assume:

| Requirement | Target |
|---|---|
| Availability | 99.9%+ |
| Scheduler availability | 99.99% preferred |
| Scheduling accuracy | ± few seconds |
| Worker scalability | Horizontal |
| Durability | No scheduled job silently lost |
| Delivery model | At-least-once |
| Execution | Idempotent |
| Job creation | p95 < 300 ms |
| Scheduler query | p95 < 200 ms |
| Recovery | Automatic |

Important interview statement:

> "I would target at-least-once execution with idempotent jobs rather than promising exactly-once execution."

---

# 4. Scale Estimation

Assume:

```text
10M active users/services
100M scheduled jobs
```

Suppose:

```text
Average 10 executions/job/day
```

Then:

```text
1B executions/day
```

Average execution rate:

```text
1B / 86,400
≈ 11.6K executions/sec
```

Peak at 5×:

```text
≈ 58K executions/sec
```

This tells us:

- Scheduler must distribute work.
- Queue must absorb spikes.
- Workers must scale independently.
- We cannot scan all 100M jobs every second.

---

# 5. APIs

## Create Job

```http
POST /v1/jobs
```

```json
{
  "name": "invoice-reminder",
  "schedule": {
    "type": "CRON",
    "expression": "0 9 * * *",
    "timezone": "Asia/Kolkata"
  },
  "payload": {
    "customerId": "C123"
  },
  "handler": "invoice-reminder",
  "retryPolicy": {
    "maxAttempts": 3,
    "backoffSeconds": 10
  },
  "idempotencyKey": "CREATE-JOB-123"
}
```

## Get Job

```http
GET /v1/jobs/{jobId}
```

## Cancel

```http
POST /v1/jobs/{jobId}/cancel
```

## Pause

```http
POST /v1/jobs/{jobId}/pause
```

## Resume

```http
POST /v1/jobs/{jobId}/resume
```

## Trigger Now

```http
POST /v1/jobs/{jobId}/trigger
```

## Execution History

```http
GET /v1/jobs/{jobId}/executions
```

---

# 6. Core Components

```text
API Gateway
     |
Job Service
     |
Job Store
     |
Scheduler
     |
Message Queue
     |
Worker Pool
     |
Target Services
```

Additional components:

```text
Redis
Kafka / Queue
PostgreSQL
Monitoring
DLQ
```

Responsibilities:

### API Gateway

- Authentication
- Authorization
- Rate limiting
- Routing

### Job Service

- Create/update/cancel jobs
- Validate schedules
- Manage job metadata

### Job Store

Source of truth for:

- Job configuration
- Status
- next_run_at
- retry configuration

### Scheduler

Finds jobs whose:

```text
next_run_at <= now
```

and dispatches them.

### Queue

Buffers executable jobs.

### Workers

Execute jobs.

### DLQ

Stores jobs that exhausted retry attempts.

---

# 7. HLD

```text
                         CLIENT
                           |
                           v
                    +--------------+
                    | API Gateway  |
                    +------+-------+
                           |
                           v
                    +--------------+
                    |  Job Service |
                    +------+-------+
                           |
                           v
                    +--------------+
                    | PostgreSQL   |
                    | Job Store    |
                    +------+-------+
                           |
                           |
                    +------v-------+
                    |  Scheduler   |
                    |   Cluster    |
                    +------+-------+
                           |
                           v
                    +--------------+
                    | Queue/Kafka  |
                    +------+-------+
                           |
             +-------------+-------------+
             |             |             |
             v             v             v
          Worker 1      Worker 2      Worker N
             |             |             |
             +-------------+-------------+
                           |
                           v
                    Target Service/API

Supporting:
Redis → locks/cache/leases
DLQ → failed jobs
Monitoring → metrics/alerts
```

---

# 8. Why PostgreSQL?

The job store contains state such as:

```text
job status
next_run_at
retry count
lease information
```

We need:

- Transactions
- Conditional updates
- Strong consistency for claiming work
- Indexes on next_run_at
- Reliable state transitions

Therefore PostgreSQL is a good starting point.

Example:

```sql
CREATE INDEX idx_jobs_due
ON jobs(next_run_at)
WHERE status = 'SCHEDULED';
```

Then the scheduler can efficiently find due jobs.

---

# 9. Why Not Cassandra?

Cassandra can scale extremely well, but the critical scheduler operation is:

> "Claim this job so that only one scheduler/worker owns it."

This is easier to reason about with transactional/conditional database operations.

Cassandra can be considered later for:

- Massive execution history
- Append-heavy audit data
- Distributed scheduling metadata

But I would not start there without a scale requirement.

---

# 10. Job Schema

## jobs

```text
job_id
tenant_id
name
handler
payload
schedule_type
cron_expression
timezone
next_run_at
status
retry_policy
created_at
updated_at
version
```

Possible statuses:

```text
ACTIVE
PAUSED
CANCELLED
COMPLETED
```

---

# 11. Execution Schema

## job_executions

```text
execution_id
job_id
scheduled_at
started_at
completed_at
status
attempt
worker_id
error
created_at
```

Possible status:

```text
PENDING
RUNNING
SUCCESS
FAILED
RETRYING
```

For huge scale, execution history can eventually be moved to a separate store.

---

# 12. The Core Scheduling Problem

Naive implementation:

```text
Every second:
    SELECT * FROM jobs
    WHERE next_run_at <= now
```

With:

```text
100M jobs
```

this becomes expensive.

We need efficient scheduling.

---

# 13. Time Bucketing

Partition jobs by execution time.

For example:

```text
2026-08-22 10:00
2026-08-22 10:01
2026-08-22 10:02
```

Conceptually:

```text
Time Bucket
     |
     +-- Jobs due in this bucket
```

Scheduler only scans the current/future buckets rather than the entire dataset.

---

# 14. Database Index

A simpler initial design:

```sql
SELECT *
FROM jobs
WHERE status = 'ACTIVE'
AND next_run_at <= NOW()
ORDER BY next_run_at
LIMIT 1000;
```

with:

```text
INDEX(status, next_run_at)
```

This is enough for a moderate-scale first version.

Staff-level statement:

> "I would start with an indexed next_run_at query and move toward partitioned/time-bucketed scheduling when the job volume or scan pressure requires it."

---

# 15. Multiple Scheduler Instances

We want high availability:

```text
Scheduler 1
Scheduler 2
Scheduler 3
```

But now:

```text
All three see the same job.
```

Potentially:

```text
Job J1
 ↓
Scheduler 1 → execute
Scheduler 2 → execute
```

We need a claiming mechanism.

---

# 16. Job Claiming

Use an atomic database update.

For example:

```sql
UPDATE jobs
SET status = 'RUNNING',
    lease_owner = 'scheduler-1',
    lease_until = NOW() + INTERVAL '30 seconds'
WHERE job_id = 'J123'
  AND status = 'ACTIVE'
  AND next_run_at <= NOW();
```

Then check:

```text
rows_updated = 1
```

Only one scheduler successfully claims it.

This is much safer than:

```text
SELECT
then
UPDATE
```

because the claim itself is atomic.

---

# 17. Redis Lease

At larger scale, Redis can reduce database contention.

Example:

```text
SET job-lock:J123 scheduler-1 NX EX 30
```

Meaning:

```text
If no one owns the job:
    acquire lease
    expire after 30 seconds
```

But:

> Redis should not be the only correctness mechanism for a critical durable workflow.

The DB/job state and idempotent execution still protect correctness.

---

# 18. Why Lease Instead of Permanent Lock?

Suppose worker gets the job:

```text
Worker 1
   |
   v
Job J123
```

Then worker crashes.

If the lock never expires:

```text
J123
 ↓
stuck forever
```

With a lease:

```text
lease_until = 10:30:30
```

If worker dies:

```text
10:30:30
   ↓
lease expires
   ↓
another worker can retry
```

This is a core distributed-systems concept.

---

# 19. Critical Flow — Job Execution

```text
              Scheduler
                  |
                  v
        Find due jobs
                  |
                  v
            Claim/Lease
                  |
                  v
              Queue
                  |
                  v
              Worker
                  |
                  v
        Mark execution RUNNING
                  |
                  v
           Execute handler
             /       \
          success    failure
             |          |
             v          v
          SUCCESS     Retry
                        |
                 +------+------+
                 |             |
              attempts      max attempts
               remain          reached
                 |             |
                 v             v
               Queue          DLQ
```

---

# 20. Why Queue Between Scheduler and Workers?

Without queue:

```text
Scheduler
 ↓
Worker directly
```

Scheduler becomes tightly coupled to execution capacity.

With queue:

```text
Scheduler
 ↓
Queue
 ↓
Workers
```

Benefits:

### Buffering

If 1M jobs become due at once:

```text
Scheduler → Queue
```

Workers process at their capacity.

### Independent scaling

Increase workers without changing scheduler.

### Retry

Failed jobs can be requeued.

### Backpressure

Queue depth indicates overload.

---

# 21. Kafka vs Traditional Queue

Kafka is useful when we need:

- High throughput
- Durable event log
- Replay
- Multiple consumers

A queue system can be simpler when:

- We only need work distribution
- Jobs should be consumed once by a worker group
- Replay isn't a primary requirement

For the interview:

> "Kafka is a valid implementation for the dispatch layer, but I would choose a work-queue abstraction if replay/event-stream semantics aren't required. The important requirement is durable buffering and controlled consumption."

---

# 22. Worker Execution

Worker receives:

```text
jobId
executionId
payload
attempt
```

Then:

```text
1. Verify execution is still valid.
2. Mark execution RUNNING.
3. Execute handler.
4. Record SUCCESS/FAILED.
5. Calculate next_run_at for recurring jobs.
6. Release/expire lease.
```

---

# 23. At-Least-Once Execution

This is critical.

Suppose:

```text
Worker
  |
  v
Execute payment reminder
  |
  v
SUCCESS
  |
  v
Worker crashes before updating DB
```

Scheduler may retry.

Therefore:

```text
Same job may execute twice.
```

We should design:

> **At-least-once execution + idempotent jobs.**

---

# 24. Idempotency

Every execution gets:

```text
execution_id
```

For example:

```text
J123:2026-08-22T10:00
```

Target service can use this as an idempotency key.

If the worker retries:

```text
executionId = E123
```

the target service recognizes:

```text
E123 already completed
```

and does not repeat the business side effect.

---

# 25. Exactly-Once vs Effectively-Once

Don't claim:

> "The scheduler guarantees exactly once."

Distributed systems make that difficult.

Better:

> "The scheduler provides at-least-once delivery/execution, while idempotent handlers make the business effect effectively once."

This is a strong interview answer.

---

# 26. Retry Design

Suppose a job fails.

Use:

```text
attempt 1
   |
1 sec
   |
attempt 2
   |
2 sec
   |
attempt 3
   |
4 sec
```

Use:

```text
Exponential Backoff
+
Jitter
```

Why jitter?

If 1M jobs fail at once and all retry exactly after 10 seconds:

```text
10 seconds later
   ↓
1M retries
   ↓
thundering herd
```

Jitter spreads them.

---

# 27. Retryable vs Non-Retryable Errors

Not every error should be retried.

### Retryable

```text
Timeout
503
Temporary network error
Rate limit
```

### Non-retryable

```text
Invalid payload
Authentication permanently invalid
Business validation failure
```

Non-retryable errors can go directly to:

```text
FAILED / DLQ
```

---

# 28. Dead Letter Queue

After:

```text
maxAttempts = 5
```

move the job to:

```text
DLQ
```

Operators can:

- Inspect
- Fix
- Replay
- Cancel

Never silently discard failed jobs.

---

# 29. Recurring Jobs

Suppose:

```text
Every day at 9 AM
```

After execution:

```text
next_run_at = next occurrence
```

Example:

```text
Aug 21 09:00
     |
     v
Aug 22 09:00
```

Important:

> Do not calculate recurrence using server-local time.

Use the job's configured timezone.

---

# 30. Time Zones and DST

Store:

```text
timezone = Asia/Kolkata
cron = 0 9 * * *
```

and calculate next execution based on that timezone.

Do not store only:

```text
09:00
```

because:

```text
America/New_York
```

has DST changes.

---

# 31. Scheduler Accuracy

There are two different requirements:

### Exactly at 10:00:00

Hard and expensive.

### Within a few seconds

Much easier.

I'd define:

```text
Scheduling SLA:
job starts within ±5 seconds
```

Then the scheduler can poll:

```text
every 1 second
```

or use time buckets/delay queues.

---

# 32. Scheduler Polling

Simple approach:

```text
Every 1 second:

SELECT jobs
WHERE next_run_at <= NOW()
LIMIT 1000
```

Then claim them atomically.

Pros:

- Simple
- Easy to reason about
- Easy to recover

Cons:

- Polling overhead
- DB load
- Precision depends on interval

---

# 33. Delayed Queue / Time Wheel

At very large scale, use:

```text
Hierarchical Timing Wheel
```

or a delayed-message system.

Conceptually:

```text
Current time
     |
     +-- 10:00 → jobs
     +-- 10:01 → jobs
     +-- 10:02 → jobs
```

This avoids scanning the entire database repeatedly.

Pros:

- Efficient scheduling
- Lower DB polling load

Cons:

- More complexity
- Recovery is harder
- Durable state still needs a source of truth

---

# 34. Recovery After Scheduler Crash

Suppose:

```text
Scheduler 1 crashes
```

Another scheduler starts.

It queries:

```text
next_run_at <= now
AND
job not completed
```

and claims jobs.

This is why the database remains the durable source of truth.

---

# 35. Recovery After Worker Crash

Suppose:

```text
Worker 1
  |
  v
RUNNING
  |
  X crash
```

The lease expires:

```text
lease_until < NOW()
```

Scheduler/recovery worker finds it and requeues.

But because execution is at-least-once:

```text
job may execute again
```

Therefore idempotency is essential.

---

# 36. Handling Long-Running Jobs

Suppose a job takes:

```text
2 hours
```

Don't use a 30-second lease without renewal.

Worker can heartbeat:

```text
lease_until = now + 30 sec
```

every few seconds.

If heartbeat stops:

```text
lease expires
```

and another worker can recover it.

But this creates a trade-off:

- Too short → unnecessary duplicate execution
- Too long → slow recovery

---

# 37. Tenant Fairness

Suppose one customer creates:

```text
1M jobs
```

and another creates:

```text
100 jobs
```

We don't want the first tenant to consume every worker.

Use:

- Per-tenant quotas
- Fair scheduling
- Rate limiting
- Separate queues
- Weighted scheduling

This is a useful Staff-level consideration.

---

# 38. Backpressure

Suppose:

```text
Incoming jobs = 100K/sec
Worker capacity = 20K/sec
```

Queue grows.

Monitor:

```text
queue depth
oldest message age
worker utilization
```

Actions:

- Scale workers
- Apply quotas
- Slow producers
- Reject low-priority jobs
- Prioritize critical jobs

---

# 39. Priority Scheduling

Jobs can have:

```text
HIGH
MEDIUM
LOW
```

Example:

```text
Payment reconciliation → HIGH
Analytics report       → LOW
```

Use separate queues:

```text
High Priority Queue
Medium Priority Queue
Low Priority Queue
```

or a priority-aware dispatcher.

---

# 40. Observability

Track:

### Scheduler

```text
jobs discovered/sec
jobs claimed/sec
scheduler lag
```

### Queue

```text
queue depth
oldest message age
consumer lag
```

### Workers

```text
success rate
failure rate
execution latency
retry rate
```

### Business

```text
missed executions
duplicate executions
DLQ count
```

Most important metric:

> **Schedule lag = actual execution start time - expected execution time**

---

# 41. Security

Jobs can contain sensitive payloads.

Need:

- Authentication
- Authorization
- Tenant isolation
- Encryption
- Payload validation
- Audit logs
- Secret management

Do not allow arbitrary users to execute arbitrary internal methods.

Use registered handlers:

```text
handler = "SEND_EMAIL"
```

rather than:

```text
handler = "execute arbitrary Java class"
```

---

# 42. LLD

## Job Service

```java
interface JobService {

    Job createJob(CreateJobRequest request);

    Job updateJob(String jobId, UpdateJobRequest request);

    void pauseJob(String jobId);

    void resumeJob(String jobId);

    void cancelJob(String jobId);
}
```

## Scheduler

```java
interface Scheduler {

    List<Job> findDueJobs();

    boolean claim(Job job);

    void dispatch(Job job);
}
```

## Executor

```java
interface JobExecutor {

    ExecutionResult execute(JobExecution execution);
}
```

## Retry Policy

```java
interface RetryPolicy {

    boolean shouldRetry(Exception exception, int attempt);

    Duration nextDelay(int attempt);
}
```

Implementations:

```text
ExponentialBackoffRetryPolicy
FixedDelayRetryPolicy
NoRetryPolicy
```

This is a Strategy Pattern.

---

# 43. Handler Factory

```text
JobHandlerFactory
       |
 +-----+-----+-------+
 |           |       |
Email      Report   Cleanup
Handler    Handler  Handler
```

```java
interface JobHandler {
    void execute(JobExecution execution);
}
```

Factory selects the correct handler.

---

# 44. State Machine

Job lifecycle:

```text
CREATED
   |
   v
ACTIVE
   |
   +------> PAUSED
   |          |
   |          v
   |        ACTIVE
   |
   +------> CANCELLED
```

Execution lifecycle:

```text
PENDING
  |
  v
CLAIMED
  |
  v
RUNNING
  |
 +----+------+
 |           |
SUCCESS     FAILED
             |
          RETRYING
             |
          PENDING
```

---

# 45. SOLID

### SRP

Separate:

```text
JobService
Scheduler
Executor
RetryPolicy
NotificationService
```

### OCP

Add new retry policies without modifying executor.

### DIP

Executor depends on:

```text
JobHandler
```

not concrete implementations.

### Strategy

Retry strategy:

```text
RetryPolicy
```

### Factory

Job handler creation:

```text
JobHandlerFactory
```

### Adapter

External job providers:

```text
WebhookAdapter
EmailProviderAdapter
```

---

# 46. Failure Matrix

| Failure | Handling |
|---|---|
| Scheduler crashes | Another scheduler claims due jobs |
| Worker crashes | Lease expires, job is retried |
| DB unavailable | Don't claim/confirm new work |
| Redis unavailable | Fall back to DB-based claim if possible |
| Kafka unavailable | Retry/outbox |
| Target API timeout | Retry with backoff |
| Target API permanently fails | DLQ |
| Duplicate delivery | Idempotent execution |
| Huge traffic spike | Queue + autoscaling + backpressure |
| One tenant dominates | Quotas/fair scheduling |

---

# 47. Why This Architecture?

## PostgreSQL

Pros:

- Strong state transitions
- Transactions
- Conditional updates
- Easy initial implementation

Cons:

- Very large scheduler scans can become expensive
- Eventually requires partitioning/sharding

## Redis

Pros:

- Fast locks/leases
- Low latency
- Useful for ephemeral scheduling state

Cons:

- Not ideal as durable source of truth
- TTL/lock failure scenarios

## Kafka/Queue

Pros:

- Decoupling
- Buffering
- Replay
- Independent worker scaling

Cons:

- Operational complexity
- Duplicate processing
- Eventual consistency

## Workers

Pros:

- Horizontal scaling
- Isolation
- Controlled concurrency

Cons:

- Need idempotency
- Retry management
- Worker failures

---

# 48. Final HLD

```text
                             CLIENT
                               |
                               v
                        +--------------+
                        | API Gateway  |
                        +------+-------+
                               |
                               v
                        +--------------+
                        |  Job Service |
                        +------+-------+
                               |
                               v
                     +-------------------+
                     |    PostgreSQL     |
                     |   Job Source of   |
                     |      Truth        |
                     +---------+---------+
                               |
                     next_run_at index
                               |
                               v
                     +-------------------+
                     | Scheduler Cluster |
                     | S1 / S2 / S3      |
                     +---------+---------+
                               |
                         Atomic Claim
                               |
                               v
                     +-------------------+
                     | Queue / Kafka     |
                     +---------+---------+
                               |
                +--------------+--------------+
                |              |              |
                v              v              v
             Worker 1       Worker 2       Worker N
                |              |              |
                +--------------+--------------+
                               |
                               v
                        Target Services

        Redis
        ├── Lease / Lock
        ├── Cache
        └── Ephemeral state

        DLQ
        └── Exhausted jobs

        Monitoring
        ├── Scheduler lag
        ├── Queue lag
        ├── Execution latency
        └── Failure/retry rate
```

---

# 49. The Most Important Interview Trade-offs

### Polling vs timing wheel

Start with polling + DB index because it is simple.

Move to time buckets/timing wheel when scale demands it.

### Redis lock vs DB atomic claim

Prefer atomic DB updates for correctness.

Use Redis to reduce contention/latency, not as the sole source of truth.

### Kafka vs queue

Use Kafka when replay/multiple consumers/event streams matter.

Use a simpler queue when the requirement is primarily work distribution.

### Exactly-once vs at-least-once

Choose:

```text
At-least-once
+
Idempotent execution
```

because it is much more practical.

### SQL vs Cassandra

Start with SQL for job state and atomic claiming.

Consider Cassandra for extremely large execution history or high-throughput append workloads.

---

# 50. The Staff-Level Answer

If asked:

> "How would you make this scheduler reliable?"

Answer:

> "I'd keep the job definition and execution state in a durable database, indexed by next_run_at. Multiple scheduler instances would discover due jobs, but use an atomic claim/lease so only one scheduler dispatches a particular execution. I'd place a durable queue between scheduling and execution to absorb spikes and allow independent worker scaling. Since worker crashes can cause redelivery, I'd use at-least-once execution with execution IDs and idempotent handlers. Failed jobs would use exponential backoff with jitter and eventually move to a DLQ. For recurring jobs, next_run_at would be calculated using the configured timezone. At larger scale I'd move from simple indexed polling to time buckets or a timing-wheel/delayed-queue approach, while retaining the database as the durable source of truth."

---

# 51. 30-Second Interview Summary

> "The scheduler stores jobs durably in PostgreSQL and indexes them by next_run_at. A horizontally scaled scheduler cluster discovers due jobs and atomically claims them using a lease. Claimed jobs go into a durable queue, which decouples scheduling from worker execution and absorbs spikes. Workers execute jobs with at-least-once semantics, using execution IDs and idempotent handlers to handle duplicates. Retries use exponential backoff and jitter, exhausted jobs go to a DLQ, and worker crashes are recovered through lease expiry. Redis can improve coordination and caching, but isn't the source of truth. At very large scale, I'd introduce time buckets or a timing wheel to avoid repeatedly scanning the full job table."

---

# 52. Mental Model

```text
SCHEDULE
   ↓
FIND DUE JOB
   ↓
CLAIM / LEASE
   ↓
QUEUE
   ↓
WORKER
   ↓
EXECUTE
   ↓
+----------+
| SUCCESS  |
| RETRY    |
| DLQ      |
+----------+

Always remember:

DURABILITY → DB
COORDINATION → Redis/DB lease
BUFFERING → Queue/Kafka
EXECUTION → Workers
RELIABILITY → Retry + Idempotency
RECOVERY → Lease expiry
OBSERVABILITY → Scheduler lag + queue lag
```
