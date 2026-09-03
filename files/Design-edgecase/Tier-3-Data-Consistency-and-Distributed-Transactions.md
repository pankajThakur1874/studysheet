# Tier 3 — Data Consistency & Distributed Transactions

## Goal

Understand how to keep business state correct when multiple requests, services, databases, and asynchronous events interact.

Core topics:

1. Concurrency and race conditions
2. Optimistic locking
3. Pessimistic locking
4. Idempotency
5. Duplicate requests
6. Eventual consistency
7. Distributed transactions
8. Saga pattern
9. Transactional Outbox
10. Inbox/idempotent consumers
11. Ordering and versioning
12. Reconciliation
13. Exactly-once misconceptions

---

# 1. Race Condition

Suppose account balance is ₹1,000.

Two requests arrive simultaneously:

```text
Request A reads 1000
Request B reads 1000

A adds 500 → 1500
B subtracts 300 → 700
```

Expected:

```text
1000 + 500 - 300 = 1200
```

But without concurrency control, final state may become ₹700.

This is a race condition.

---

# 2. Optimistic Locking

Add a version:

```text
id | balance | version
123| 1000    | 5
```

Update:

```sql
UPDATE accounts
SET balance = 1200,
    version = 6
WHERE id = 123
  AND version = 5;
```

If another request already changed version 5 → 6, this update affects zero rows.

The application detects conflict and retries/rejects.

Best when conflicts are relatively uncommon.

---

# 3. Pessimistic Locking

Lock the row while modifying it:

```sql
SELECT *
FROM accounts
WHERE id = 123
FOR UPDATE;
```

Other transactions wait.

Good when:

- conflicts are frequent
- correctness requires serialized updates

Trade-offs:

- lock contention
- deadlocks
- reduced concurrency
- longer transactions can be dangerous

---

# 4. Idempotency

A request can be repeated because of:

- client timeout
- network failure
- service retry
- user double-click
- message redelivery

For payment:

```text
Idempotency-Key = ABC123
```

Store:

```text
ABC123 → paymentId + result
```

Repeated request returns the same logical result.

Key idea:

> One logical operation may have many physical attempts.

---

# 5. State Machine

Avoid arbitrary state updates.

Example:

```text
CREATED
  ↓
PROCESSING
  ↓
SUCCESS
  ↓
REFUNDED
```

Reject invalid transitions:

```text
SUCCESS → PROCESSING ❌
REFUNDED → SUCCESS ❌
```

State machines are powerful for payments, orders, shipments, subscriptions, etc.

---

# 6. Eventual Consistency

Suppose:

```text
Payment Service
   ↓
DB = SUCCESS
   ↓
Kafka
   ↓
Order Service
```

For a short period:

```text
Payment = SUCCESS
Order = PENDING
```

This can be acceptable if the business can tolerate it.

The system becomes eventually consistent when the event is processed.

Important interview question:

> What inconsistency window is acceptable?

---

# 7. Strong vs Eventual Consistency

### Strong consistency

A read after a successful write observes the new state.

Useful for:

- critical financial state
- operations where stale data is unacceptable

### Eventual consistency

Replicas/services converge over time.

Useful for:

- search indexes
- notifications
- analytics
- recommendations

Choose based on business requirements, not preference.

---

# 8. Distributed Transaction Problem

Suppose:

```text
Order DB
+
Payment DB
```

You want:

```text
Order created
AND
Payment successful
```

as one atomic transaction.

But they are independent systems.

A normal DB transaction cannot span them.

Options:

- 2PC
- Saga
- asynchronous workflow
- transactional outbox for event publication

---

# 9. Two-Phase Commit

Conceptually:

```text
Coordinator
   ↓
Prepare
 ┌─┴────────┐
DB1        DB2
 └─┬────────┘
   ↓
Commit
```

Advantages:

- atomic distributed commit

Problems:

- coordination overhead
- blocking
- availability impact
- operational complexity

At scale, many modern architectures prefer Saga/event-driven approaches.

---

# 10. Saga Pattern

Break one distributed transaction into local transactions.

Example:

```text
Create Order
    ↓
Reserve Inventory
    ↓
Charge Payment
    ↓
Confirm Order
```

If Payment fails:

```text
Cancel Order
+
Release Inventory
```

These are compensating actions.

## Choreography

Services communicate through events:

```text
OrderCreated
   ↓
InventoryReserved
   ↓
PaymentCompleted
   ↓
OrderConfirmed
```

No central coordinator.

## Orchestration

A Saga orchestrator coordinates:

```text
Orchestrator
 ├── Create Order
 ├── Reserve Inventory
 ├── Charge Payment
 └── Confirm Order
```

Easier to visualize/control, but adds a coordinator.

---

# 11. Saga Trade-offs

Saga does not magically provide ACID across services.

You accept:

- intermediate states
- eventual consistency
- compensating actions
- more complex failure handling

Example:

```text
Payment succeeds
Inventory fails
```

You may need:

```text
Refund Payment
```

A compensation itself can fail, so compensation needs retries/idempotency too.

---

# 12. Transactional Outbox

When DB state and event publication must stay consistent:

```text
DB transaction
 ├── update business state
 └── insert outbox event
             ↓
        publisher/CDC
             ↓
           Kafka
```

This prevents:

```text
DB success + event lost
```

when implemented correctly.

Delivery can still be at-least-once, so consumers should be idempotent.

---

# 13. Inbox Pattern

Consumer receives:

```text
eventId = E123
```

Store it in an inbox/processed-events table in the same transaction as the business change.

```text
BEGIN
insert E123
update business state
COMMIT
```

Duplicate:

```text
E123 already exists
→ ignore
```

---

# 14. Exactly Once

Do not casually claim:

> "Kafka gives exactly once."

Separate:

- exactly-once Kafka processing
- exactly-once business effect
- exactly-once delivery

For external DB/API side effects, idempotency and transactional boundaries are still required.

Strong statement:

> "I design for at-least-once delivery and make the business effect idempotent."

---

# 15. Ordering

Kafka ordering is guaranteed within a partition.

If events for one order must be ordered:

```text
key = orderId
```

Then:

```text
OrderCreated
OrderPaid
OrderShipped
```

can stay in the same partition.

For stronger protection, include:

```text
sequenceNumber
version
```

and reject stale events.

---

# 16. Reconciliation

Distributed systems can get into ambiguous states.

Example:

```text
Payment gateway = SUCCESS
Local DB = PENDING
```

A reconciliation job can:

```text
find stale PENDING payments
       ↓
query source of truth
       ↓
repair local state
```

Reconciliation is a key production mechanism for financial systems.

---

# Interview Framework

When asked about consistency:

```text
What must be atomic?
What can be eventual?
Where is the source of truth?
What happens on retry?
What happens on duplicate?
What happens if service crashes?
How do we reconcile?
```

## Strong answer

> "I first identify the business invariants that must be atomic. I keep local state changes inside a database transaction, use an outbox for reliable event publication, and use idempotency for retries and duplicate messages. For cross-service workflows I prefer Saga when eventual consistency and compensation are acceptable. I also define reconciliation for ambiguous states."

## Memorize

> Atomic local transaction + reliable event intent + idempotent processing + compensation + reconciliation.
