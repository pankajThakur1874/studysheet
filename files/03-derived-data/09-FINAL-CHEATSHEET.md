# DDIA Final Cheatsheet

*The whole book in one page — use as a pre-interview reference.*

---

## The Book in Three Sentences

1. **Part I:** On one machine, you choose a storage engine (LSM-tree vs B-tree; OLTP vs column-store), an encoding format, and a data model — all engineering trade-offs between read speed, write speed, and flexibility.
2. **Part II:** When data lives on more than one machine, **partial failure you can't detect** is the root of every difficulty — replication lag, failover, conflicts, clock unreliability, and the need for consensus.
3. **Part III:** No one system does everything; the solution is to funnel all writes through a system of record and derive all other representations — search, cache, analytics — by consuming an immutable, ordered event log.

---

## One-Line Summaries (Topics 1–36)

| # | Topic | One line |
|---|---|---|
| 1 | Reliability | A system that keeps working correctly even when things go wrong — hardware, software, human. |
| 2 | Scalability | Coping with increased load. Latency percentiles > averages. Twitter's celebrity fan-out = skew problem. |
| 3 | Maintainability | Operability, simplicity, evolvability — design for the majority of life that is maintenance. |
| 4 | Relational vs Document | Relational = joins + normalisation; Document = self-contained, schema-flexible. Graph when all is edges. |
| 5 | Graph Models | Vertices + edges for many-to-many. Cypher = declarative; Gremlin = traversal; SPARQL = RDF. |
| 6 | LSM-Trees | Append-only log → in-memory memtable → immutable SSTables → merge compaction. Fast writes, slower reads. |
| 7 | B-Trees | Fixed-size pages, in-place updates, WAL for crash recovery. Good for reads and range scans. |
| 8 | OLTP vs OLAP | OLTP: row-store, index lookups. OLAP: column-store, full scans, aggregation, compression, vectorized. |
| 9 | Encoding | Backward compat (new reads old) + forward compat (old reads new) both needed for rolling upgrades. LWW loses data. idempotent retries always. |
| 10 | Single-Leader Replication | All writes → leader → follower log. No conflicts. Failover is the hard part (split-brain, discarded writes). Logical log enables CDC + rolling upgrades. |
| 11 | Replication Lag | Three anomalies: (1) my write vanished → read-after-write; (2) data goes backwards → monotonic reads; (3) effect before cause → consistent prefix reads. |
| 12 | Multi-Leader | Multiple write nodes → conflicts. Best avoided. Conflict avoidance >> LWW (loses data). CRDT/OT for complex data. Version vectors for causality. |
| 13 | Leaderless | Any replica takes writes. Quorum: w + r > n guarantees overlap — but not absolutely. LWW loses data; only safe for immutable keys. Siblings + tombstones for conflict resolution. |
| 14 | Partitioning | Range (range scans, hot spots) vs hash (even load, no range). Compound key = both. Single hot key → split with random suffix. |
| 15 | Secondary Indexes | Local = cheap writes, scatter/gather reads. Global = cheap reads, async writes. No cheap-on-both option. |
| 16 | Rebalancing | Never mod N. Fixed partitions: steal whole partitions. Dynamic: split/merge by size. Human-gated to avoid cascade. ZooKeeper for routing map. |
| 17 | Transactions & ACID | All-or-nothing (abortability). C is the app's job. Isolation = ideally serializable. Durability = risk reduction. Retry edges: lost ack, external side effects, overload. |
| 18 | Weak Isolation | Read Committed: no dirty reads/writes. Snapshot isolation: no read skew (MVCC). **Write skew** (on-call, double booking) needs **serializable**. Level name lies — check what's actually prevented. |
| 19 | Serializability | 3 ways: serial execution (one thread, stored procs, in-memory), 2PL (pessimistic, readers/writers block each other, deadlocks), SSI (optimistic, abort on stale premise). 2PL ≠ 2PC. |
| 20 | Unreliable Networks | Timeout = guess. No reply = {lost req ∣ dead ∣ paused ∣ lost reply ∣ slow}. Unbounded delay (queueing). Make ops idempotent. Defined partition behaviour. |
| 21 | Clocks & Pauses | Time-of-day can jump backward; monotonic is forward-only but per-machine only. LWW + clock skew = silent data loss. GC pause → node looks dead but isn't. Timestamp = range, not point. |
| 22 | Truth & Fencing | Truth = majority vote (only one majority). Fencing token: monotonically increasing number; resource rejects lower tokens. Byzantine = lying nodes; only needed for aerospace/blockchains. |
| 23 | Linearizability & CAP | Once any client sees the new value, all subsequent reads see it. Expensive (always). CAP is a poor frame: partitions aren't optional; use "Consistent OR Available when Partitioned." Multi-leader/leaderless ≠ linearizable. |
| 24 | Ordering & Causality | Causal consistency = strongest model without latency penalty. Lamport timestamps: total order consistent with causality, can't distinguish concurrent. Version vectors: can distinguish, no total order. Total order broadcast ≡ consensus ≡ linearizable CAS. |
| 25 | Two-Phase Commit | Phase 1: prepare (votes). Phase 2: coordinator commits. In-doubt = blocking. 2PC ≠ 2PL. XA: poor, slow, SPOF coordinator. Alternative: idempotent + at-least-once. |
| 26 | Consensus | Agreement + Integrity + Validity + **Termination** (2PC lacks this). Epoch numbers + two overlapping majority votes. ZooKeeper (Zab) / etcd (Raft). Don't implement yourself. |
| 27 | Batch Processing | Immutable input → map → shuffle (bottleneck) → reduce → output. Data locality. Sort-merge join (both large) or broadcast hash join (one small). Bulk-load output; never write record-by-record from reducers. |
| 28 | Join Algorithms | Sort-merge join: shuffle both by join key. Broadcast hash: load small side to all mappers. Hot keys: spread across reducers. |
| 29 | Dataflow Engines | Spark/Flink: keep intermediate state in memory, not HDFS. DAG of operators, pipelining, no barrier between every stage. Spark: lineage recomputation. Flink: checkpointing. Use DataFrame API for Catalyst optimizer. |
| 30 | Messaging & Kafka | Traditional broker: delete on ack, per-message load balance, no replay. Kafka: log partition + offsets + retention + fan-out for free. At-least-once → idempotent consumers. Monitor consumer lag. |
| 31 | CDC & Event Sourcing | CDC: tap WAL → Kafka → derived followers. No dual-write race. Log compaction = full state without snapshot. Event sourcing: events are facts; state is projection; commands must be validated before becoming events. |
| 32 | Stream Processing | Event time ≠ processing time. Watermarks → close windows → stragglers (drop or correct). Stream-stream: windowed join both sides. Stream-table: local state + CDC. Table-table: materialized view. Bound state stores with TTL. |
| 33 | Stream Fault Tolerance | Microbatching (Spark): slice + batch retry, seconds latency. Checkpointing (Flink): barrier snapshots, milliseconds. Idempotent writes: simplest, usually sufficient. Commit offset AFTER writing output. Operators must be deterministic. |
| 34 | Data Integration | Single system of record → Kafka CDC → derived systems. No race conditions. Log ordering = state machine replication. Batch (rebuild) + stream (keep current) are complementary. |
| 35 | Correctness | End-to-end argument: TCP dedup and DB transactions are insufficient; need end-to-end idempotency keys. Timeliness (eventual) vs integrity (correctness) — integrity is far more important. Uniqueness without 2PC: partition by unique key, single-threaded stream processor per partition. |
| 36 | Ethics | Predictive models encode bias and create feedback loops. Data collection ≠ data use rights. Engineers bear responsibility. Data minimisation, purpose limitation, transparency. |

---

## The "Always Remember" Rules

**Storage:**
- LSM-tree = fast writes, amortised compaction. B-tree = in-place updates, WAL for safety.
- Column storage wins for analytics because it only reads the columns you need.
- Every index speeds up reads and slows down writes. Don't index everything.

**Replication:**
- Asynchronous replication can lose committed writes on failover. Know your mode.
- Failover is hard: can't detect failure (timeout = guess), split-brain, discarded writes.
- Logical (row-based) replication enables CDC and rolling upgrades; WAL shipping doesn't.

**Partitioning:**
- Never `hash(key) mod N` — changing N reshuffles almost everything.
- Hot key problem: hashing doesn't help if it's the *same* key every time. Split it with a random suffix.

**Transactions:**
- Default isolation is usually NOT serializable. Write skew slips through snapshot isolation.
- The level *name* lies. Oracle's "serializable" is snapshot isolation. MySQL InnoDB repeatable-read doesn't detect lost updates; Postgres does.
- 2PL ≠ 2PC. Two completely different mechanisms.

**Distributed systems:**
- A timeout cannot tell you if a request succeeded. Make operations idempotent.
- Clocks lie: LWW with clock skew silently drops data. Use logical clocks for ordering.
- Truth = majority vote. Fencing token = monotonic ID that resources check.
- CAP "partition tolerance" is not optional. "Consistent OR Available when Partitioned."
- Consensus ≡ total order broadcast ≡ linearizable CAS. Don't implement consensus yourself.

**Streaming:**
- Event time ≠ processing time. Use event time; accept late events.
- At-least-once + idempotent writes = effectively-once (usually sufficient).
- Commit Kafka offset AFTER writing output, not before.

**Data integration:**
- Single system of record → CDC → derived followers. No dual-write race conditions.
- End-to-end idempotency key: generated at the client, passed through every hop.
- Integrity (correctness) >> timeliness (freshness). Don't sacrifice the former for the latter.

---

## The "Staff-Level" Differentiators

These are the answers that separate senior from staff-level candidates:

1. **Weak isolation and write skew.** Most candidates know read committed. Staff candidates can explain write skew (different rows, same read premise), why snapshot isolation misses it, and that SSI detects it by tracking stale premises.

2. **CAP framing.** Don't say "we're CP." Say: "Partitions aren't optional. When one occurs, we trade availability for consistency — the minority side stops responding to preserve the linearizable guarantee. The bigger cost is that linearizability adds latency proportional to network delay *all the time*, not just during partitions."

3. **Why 2PC is "not a very good consensus algorithm."** 2PC blocks on coordinator failure because it requires all participants, not just a majority. Raft/Paxos use majority quorums and elect new leaders — they satisfy termination; 2PC doesn't.

4. **Leaderless quorums don't guarantee linearizability.** The `w + r > n` rule is a probability knob, not a hard guarantee. Sloppy quorums, concurrent writes, and partial failures all break the overlap. Don't call Cassandra "CP."

5. **The read-modify-write hazard in schema evolution.** When old code reads a record containing a field written by newer code, fully decodes it, and writes it back — the unknown field is silently dropped. Real data loss, no error.

6. **Timeliness vs integrity.** Violations of timeliness = eventual consistency (annoying). Violations of integrity = perpetual inconsistency (catastrophic). Most systems can sacrifice the former; none can sacrifice the latter.

7. **End-to-end idempotency.** TCP dedup and database transactions operate within their own layer. A lost commit acknowledgment at the network level can cause the client to retry a transaction that already succeeded. The fix is an end-to-end idempotency key passed from the client all the way to the database.

8. **Why replication log format matters.** WAL shipping ties you to one database version (no rolling upgrades). Logical (row-based) replication decouples format from storage — enables rolling upgrades and CDC. This is why logical replication is the modern default.

---

## Revision Plans

### 10-Minute Revision (right before walking in)

1. **Write skew:** two txns read same thing → write *different* objects → invariant broken. Snapshot isolation misses it. SSI catches it.
2. **CAP reframe:** partitions happen. Consistent OR Available when partitioned. "CP/AP" is a poor shorthand.
3. **Kafka:** log partition + offset. At-least-once → idempotent. Consumer lag = the metric.
4. **2PC ≠ 2PL.** 2PC blocks. Alternative: idempotent + at-least-once.
5. **Consensus ≡ TOB ≡ linearizable CAS.** ZooKeeper = don't implement yourself.

---

### 30-Minute Revision (morning of interview)

Read the Quick Revision box at the bottom of each of these high-priority files:

1. `02-distributed-data/08-transactions-acid.md`
2. `02-distributed-data/09-weak-isolation.md`
3. `02-distributed-data/11-unreliable-networks.md`
4. `02-distributed-data/14-linearizability.md`
5. `02-distributed-data/17-consensus.md`
6. `03-derived-data/04-messaging-and-logs.md`
7. `03-derived-data/05-cdc-and-event-sourcing.md`

Then scan this cheatsheet's "Always Remember" section.

---

### 1-Hour Revision (day before)

**Focus on the highest-yield interview topics:**

- [20 min] Part II transactions: ACID (Topic 17), write skew (Topic 18), SSI (Topic 19)
- [10 min] Networks + clocks + fencing (Topics 20, 21, 22)
- [10 min] Linearizability + CAP + consensus (Topics 23, 26)
- [10 min] Kafka + CDC (Topics 30, 31)
- [10 min] End-to-end correctness (Topic 35)

Work through the 30-second interview answers in each file.

---

### 1-Day Revision (2–3 days before)

**Read in this order, which builds understanding sequentially:**

1. `01-foundations/` README + Topics 6, 7 (storage engines — foundation for everything)
2. `02-distributed-data/` Topics 10, 11 (replication — the default model and its costs)
3. Topics 13, 14 (leaderless, partitioning — where conflicts and hot spots live)
4. Topics 17, 18, 19 (transactions, isolation, serializability — the most-asked area)
5. Topics 20, 21, 22, 23, 26 (the network/clock/consensus arc — the intellectual climax)
6. `03-derived-data/` Topics 30, 31, 32, 33, 35 (Kafka, CDC, streaming, correctness)

After each section, test yourself with the interview Q&A in the files.

---

## Common Design Interview Anchors

These questions appear frequently. Here's the DDIA-grounded answer structure:

**"Design a distributed key-value store"**
→ Partitioning (Topic 14), replication (Topics 10 or 13), quorum (Topic 13), consistency model (Topic 23), read-your-writes (Topic 11).

**"How does Kafka work? / Design a message queue"**
→ Log partitioning + offsets + consumer groups + retention (Topic 30). At-least-once + idempotent consumers (Topic 33). CDC use case (Topic 31).

**"How do you keep a search index in sync with a database?"**
→ CDC via Debezium → Kafka → Elasticsearch consumer group (Topic 31). No dual writes (race condition). Log compaction for bootstrap. Eventual consistency accepted.

**"Design a rate limiter / counter"**
→ Single node: atomic increment. Distributed: partition by user/key; each partition handles its own counter (no cross-partition coordination). For approximate: CRDT counter merged at read time.

**"How do you implement distributed locking?"**
→ ZooKeeper or etcd (consensus-backed, linearizable) with a fencing token. Never a leaderless store. The fencing token is checked by the resource, not by the client (Topic 22).

**"Explain eventual consistency vs strong consistency"**
→ Timeliness vs integrity (Topic 35). Eventual consistency is a timeliness trade-off. Integrity can still be maintained. Linearizability = strong; causal consistency is the cheapest strong-enough model.

**"What happens when a database leader fails?"**
→ Failover: timeout-based detection (guess), quorum election, promote follower. Split-brain risk → fencing. Async replication → may lose committed writes. The failover timeout trade-off (Topic 10).

**"How do you scale writes beyond one machine?"**
→ Partitioning (Topic 14). Choose partition key to spread load AND keep related data together. Compound key for hot spots + range queries.

**"What is the CAP theorem?"**
→ Name the three properties, explain that partitions aren't optional, reframe as "Consistent OR Available when Partitioned," note that CAP only covers linearizability + partitions and misses the performance cost of linearizability (Topic 23).
