# Tier 6 — Advanced Distributed Systems

## Goal

Build senior/staff-level intuition for partial failure, coordination, consensus, time, and distributed-system trade-offs.

Topics:

1. CAP theorem
2. Network partitions
3. Quorum
4. Leader election
5. Split brain
6. Distributed locks
7. Clock skew
8. Logical clocks
9. Idempotency
10. Failure detection
11. Consensus
12. Graceful degradation
13. Multi-region systems
14. Disaster recovery

---

# 1. Partial Failure

In a single process:

```text
Everything fails together.
```

In distributed systems:

```text
Service A = healthy
Service B = slow
Service C = unreachable
```

This is a partial failure.

Your design must assume:

> Some components can fail while others continue working.

---

# 2. CAP Theorem

During a network partition, a distributed system cannot simultaneously guarantee both:

- strong consistency
- availability

while also tolerating the partition.

CAP is specifically about behavior during a partition.

Do not simplify it as:

> "You can only ever have two of C/A/P."

P is about partition tolerance, which distributed systems generally must handle.

---

# 3. Quorum

For N replicas:

```text
N = 3
```

A common quorum:

```text
majority = 2
```

If 1 replica fails:

```text
2 replicas remain
```

The system can still reach majority.

Quorums help coordinate reads/writes/leadership depending on the system.

---

# 4. Leader Election

Multiple nodes may coordinate around one leader:

```text
Node A → Leader
Node B → Follower
Node C → Follower
```

If leader fails:

```text
A 💥
 ↓
election
 ↓
B becomes leader
```

Leader election must avoid two leaders simultaneously.

---

# 5. Split Brain

Dangerous scenario:

```text
Network partition

Group 1:
A thinks leader

Group 2:
B thinks leader
```

Both accept writes.

Now state diverges.

Prevent with:

- quorum
- fencing
- consensus mechanisms
- epoch/term numbers

---

# 6. Fencing

Suppose old leader loses connectivity but continues operating.

A new leader is elected.

If the old leader can still write to storage, both can modify data.

A fencing mechanism invalidates the old leader's authority.

Example concept:

```text
Leader epoch = 10

new leader:
epoch = 11

old leader:
epoch 10 → writes rejected
```

Fencing is a powerful concept for distributed locks and storage systems.

---

# 7. Distributed Lock

Need:

```text
Only one worker processes job X.
```

Architecture:

```text
Worker A ─┐
Worker B ─┼→ lock
Worker C ─┘
```

A lock needs:

- ownership
- expiry/lease
- safe release
- handling process crash
- protection against stale owners

A simple `SETNX` is not a complete distributed-lock design.

---

# 8. Clock Skew

Different machines have slightly different clocks.

```text
Server A = 10:00:00.100
Server B = 09:59:59.900
```

Therefore, don't blindly assume timestamps from different machines provide perfect ordering.

Problems:

- token expiry
- event ordering
- leases
- distributed transactions

Use monotonic clocks for measuring durations locally and logical/version mechanisms for ordering where appropriate.

---

# 9. Logical Ordering

For distributed events, use:

- sequence numbers
- versions
- Lamport clocks
- vector clocks in systems requiring richer causal reasoning

For many business systems, a simple entity version is enough.

Example:

```text
Order version 10
Order version 11
```

Reject version 9 arriving later.

---

# 10. Failure Detection

A service cannot instantly know whether another service has crashed or is merely slow.

Example:

```text
No response
```

Could mean:

- service crashed
- network failed
- packet delayed
- service overloaded
- response lost

Therefore:

> Failure detection in distributed systems is often based on timeouts and imperfect suspicion.

This is why systems need retries, idempotency, and reconciliation.

---

# 11. Exactly Once Is Hard

Consider:

```text
Service A
 ↓
Service B
```

A sends request.

B performs operation.

Response is lost.

A doesn't know whether B succeeded.

If A retries:

```text
operation may happen twice
```

Therefore:

```text
idempotency key
+
deduplication
+
state machine
```

are critical.

---

# 12. Multi-Region Architecture

Possible:

```text
Region A
 ├── App
 ├── DB
 └── Kafka

Region B
 ├── App
 ├── DB
 └── Kafka
```

Questions:

- active-active or active-passive?
- where is the source of truth?
- how is replication handled?
- what is RPO?
- what is RTO?
- how are conflicts resolved?
- how does failover work?
- how do clients discover the healthy region?

Multi-region increases availability but also consistency and operational complexity.

---

# 13. RPO and RTO

### RPO

Recovery Point Objective:

> How much data loss can the business tolerate?

Example:

```text
RPO = 5 minutes
```

At worst, you can lose approximately 5 minutes of data.

### RTO

Recovery Time Objective:

> How quickly must service be restored?

Example:

```text
RTO = 30 minutes
```

The business requires recovery within about 30 minutes.

---

# 14. Disaster Recovery

A robust DR plan includes:

```text
Backups
+
Replication
+
Independent storage
+
Cross-region copy
+
Restore testing
+
Runbooks
+
Monitoring
```

Replication alone is not a backup.

---

# 15. Graceful Degradation

When a dependency fails:

```text
Core functionality
    ↓
continue

Non-critical feature
    ↓
disable
```

Example:

```text
Payment = required
Recommendations = optional
Analytics = asynchronous
```

Keep the critical path alive.

---

# 16. Senior System Design Thinking

For every component ask:

### Failure

```text
What if it crashes?
```

### Slow

```text
What if it becomes 10x slower?
```

### Duplicate

```text
What if request/event arrives twice?
```

### Lost response

```text
What if operation succeeds but response is lost?
```

### Network partition

```text
What if A cannot reach B?
```

### Overload

```text
What if traffic becomes 10x?
```

### Recovery

```text
What happens when it comes back?
```

That last question is frequently forgotten.

---

# 17. Recovery Can Cause Another Failure

Example:

```text
Redis down
 ↓
DB absorbs traffic
 ↓
Redis recovers
 ↓
millions of clients refill cache
 ↓
Redis overloaded
```

Or:

```text
Kafka recovers
 ↓
huge consumer backlog
 ↓
consumers scale aggressively
 ↓
DB overloaded
```

Recovery should be gradual and controlled.

---

# 18. The Senior-Level Failure Matrix

| Failure | Protection |
|---|---|
| Slow dependency | Timeout |
| Dependency failure | Circuit breaker |
| Resource exhaustion | Bulkhead |
| Retry amplification | Backoff + jitter |
| Duplicate request | Idempotency |
| Duplicate event | Deduplication |
| DB corruption | Backup + PITR |
| Broker failure | Replication |
| Cache failure | Graceful fallback |
| Cache stampede | Single-flight |
| Hot key | Local cache/replication |
| Traffic spike | Rate limiting/backpressure |
| Cross-service workflow | Saga |
| DB + event dual write | Outbox |
| Ambiguous external result | Reconciliation |
| Split brain | Quorum/fencing |
| Region failure | DR/failover |

---

# Final Interview Framework

When designing any production system, walk through:

## 1. Functional requirements

What does the system need to do?

## 2. Non-functional requirements

- latency
- throughput
- availability
- consistency
- durability

## 3. Scale

Estimate:

- requests/sec
- storage
- bandwidth
- peak traffic

## 4. Data model

What is the source of truth?

## 5. Architecture

```text
Client
 ↓
Gateway
 ↓
Services
 ↓
Cache / DB / Kafka
```

## 6. Bottlenecks

Identify likely bottlenecks before they occur.

## 7. Failure handling

Ask:

```text
What if DB fails?
What if Redis fails?
What if Kafka fails?
What if dependency is slow?
What if request is duplicated?
What if service crashes?
What if network partitions?
```

## 8. Consistency

Decide:

```text
strong?
eventual?
transactional?
idempotent?
```

## 9. Recovery

Define:

```text
RPO
RTO
backup
failover
reconciliation
```

## 10. Observability

Monitor:

- latency
- throughput
- errors
- saturation
- queues
- lag
- dependency health

---

# The most important senior-level principle

> **A distributed system is not designed only for the happy path. It is designed around what happens when things are slow, duplicated, unavailable, partially failed, or recovering.**

## Final mental model

```text
                    SYSTEM DESIGN
                         │
          ┌──────────────┼──────────────┐
          ↓              ↓              ↓
        SCALE        CONSISTENCY     RESILIENCE
          │              │              │
       Cache          Txns          Timeout
       Shard          Saga          Retry
       Queue          Outbox        Circuit
       Replica        Idempotency   Bulkhead
          │              │              │
          └──────────────┼──────────────┘
                         ↓
                    OBSERVABILITY
                         ↓
                    RECOVERY / DR
```

## Memorize these principles

1. **Find the bottleneck before scaling.**
2. **Assume dependencies can be slow, not just down.**
3. **Assume requests and events can be duplicated.**
4. **Never let retries be unlimited.**
5. **Timeouts protect resources.**
6. **Bulkheads isolate failures.**
7. **Circuit breakers stop repeated calls to unhealthy dependencies.**
8. **Backpressure prevents overload propagation.**
9. **Replication improves availability; backups provide recoverability.**
10. **Make business operations idempotent.**
11. **Use outbox for DB + event consistency.**
12. **Use Saga for distributed business workflows.**
13. **Design recovery, not just failure handling.**
14. **Monitor saturation, not just errors.**
15. **Always ask what happens when the failed component comes back.**
