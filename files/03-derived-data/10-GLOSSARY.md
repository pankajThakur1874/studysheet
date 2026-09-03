# GLOSSARY

*Key terms from Designing Data-Intensive Applications, with brief definitions and topic references. Use this as a quick lookup during revision — every term here appears in one or more topic files.*

---

## A

**ACID** — Atomicity, Consistency, Isolation, Durability: the four properties of database transactions. C is the odd one out (it's the application's job); A is better understood as abortability. [Topic 17]

**Anti-entropy** — A background process in leaderless databases that scans for differences between replicas and copies missing data. Complements read repair. [Topic 13]

**Asynchronous replication** — The leader doesn't wait for followers to confirm a write before reporting success. Fast but risks losing committed writes on leader failure. [Topic 10]

**Atomic commit** — An operation that either fully completes or fully undoes across multiple nodes. Two-phase commit (2PC) is the standard algorithm. [Topic 25]

**Atomicity (ACID)** — All writes in a transaction take effect, or none do. Better named *abortability* — the ability to abort and retry cleanly. [Topic 17]

---

## B

**B-tree** — The most common index structure: a balanced tree of fixed-size pages. Good for both point reads and range scans. Most OLTP databases use B-trees. [Topic 7]

**Backpressure** — A flow control mechanism where a consumer signals to a producer to slow down when overwhelmed. Unix pipes use it; Kafka doesn't (it buffers on disk). [Topic 30]

**Backward compatibility** — New code can read data written by old code. Usually easy (you know the old format). [Topic 9]

**Batch processing** — Computing over large, bounded datasets offline. Input is immutable; output is produced once. High throughput, not latency-sensitive. [Topic 27]

**Byzantine fault** — A node that fails by sending incorrect, arbitrary, or malicious messages. Much harder to tolerate than crash-stop faults. [Topic 22]

---

## C

**CAP theorem** — A distributed system can't be Consistent (linearizable), Available, and Partition-tolerant all at once. Better phrased: "Consistent OR Available when Partitioned." Partitions aren't optional. [Topic 23]

**Causal consistency** — The system respects causal order (effects can't appear before their causes). The strongest consistency model that doesn't incur network-delay latency costs. [Topic 24]

**Change data capture (CDC)** — Capturing all writes to a database and publishing them as an ordered event stream, so downstream systems can stay in sync. Usually reads the database WAL. [Topic 31]

**Column-oriented storage** — Stores all values of a column together (rather than rows). Very efficient for analytical queries that access many rows but few columns. [Topic 8]

**Compaction** — Merging and garbage-collecting old log segments or SSTables to reclaim disk space. Also refers to Kafka's log compaction (keep only latest value per key). [Topics 6, 30]

**Compare-and-set (CAS)** — An atomic operation: "write the new value only if the current value matches the expected value." Requires linearizability. [Topics 18, 23]

**Consensus** — Getting multiple nodes to agree on a value, fault-tolerantly. Properties: Agreement, Integrity, Validity, Termination. Algorithms: Raft, Paxos, Zab. Equivalent to total order broadcast and linearizable CAS. [Topic 26]

**Consistent prefix reads** — A guarantee that if you see the effect, you also see its cause. Prevents answers appearing before questions. [Topic 11]

**Consumer group** — A set of Kafka consumers sharing the work of consuming a topic. Each partition is assigned to exactly one consumer in the group. Different groups are fully independent. [Topic 30]

**CRDT (Conflict-free Replicated Data Type)** — A data structure designed to be merged automatically and sensibly across concurrent writes. Used in leaderless systems. [Topics 12, 13]

**Cursor stability** — A property that prevents read-modify-write cycles from being interrupted by other writers. Prevents lost updates for single-object atomic operations. [Topic 18]

---

## D

**Data locality** — Running computation close to the data that computation reads. MapReduce's scheduler tries to run each map task on the node storing the input block. [Topic 27]

**Dataflow engine** — A batch/stream processing framework (Spark, Flink, Tez) that models computation as a DAG of operators and passes intermediate data in memory rather than materialising to HDFS. [Topic 29]

**Dead letter queue** — A special queue where messages go if they repeatedly fail processing. Prevents a bad message from blocking the whole queue forever.

**Derived data** — Data computed from a system of record. If lost, it can be recomputed. Examples: search index, cache, materialized view. [Part III README]

**Dirty read** — Reading another transaction's uncommitted write. Prevented by Read Committed isolation. [Topic 18]

**Dirty write** — Overwriting another transaction's uncommitted write. Prevented by row-level locks in Read Committed. [Topic 18]

**Dynamic partitioning** — Partitions automatically split when they exceed a size threshold and merge when they shrink. Used in HBase, RethinkDB, MongoDB. [Topic 16]

---

## E

**Encoding / serialization** — Converting in-memory data structures to a byte sequence for storage or transmission. [Topic 9]

**Epoch number** — A monotonically increasing number assigned to each leadership term in consensus algorithms. A leader with a higher epoch number always wins over a lower one. [Topics 22, 26]

**Ephemeral node** — A ZooKeeper node that is automatically deleted when the creating session ends. Used for failure detection and distributed locking. [Topic 26]

**ETL (Extract-Transform-Load)** — The classic batch process for moving data from a source database into a data warehouse. [Topic 8]

**Event sourcing** — An application design where the primary representation of state is an immutable log of domain events. Current state is a derived projection. [Topic 31]

**Event time** — The timestamp embedded in an event recording when the event actually occurred. Contrast with processing time. [Topic 32]

**Exactly-once semantics** — Each event's effect appears once in the output, regardless of retries. Requires idempotency or transactional commits. Also called effectively-once. [Topics 33, 35]

---

## F

**Fan-out** — Delivering a message to multiple consumers independently. Kafka does this at zero marginal cost via consumer groups. [Topics 12, 30]

**Fencing token** — A monotonically increasing number issued with a lock/lease. The protected resource checks tokens and rejects writes with lower tokens than it has already seen. Prevents stale actors from causing damage. [Topic 22]

**Follower** — In single-leader replication, a replica that receives a stream of changes from the leader and applies them. Handles read requests. [Topic 10]

**Forward compatibility** — Old code can read data written by new code. Harder than backward compatibility; requires gracefully ignoring unknown fields. [Topic 9]

**Full table scan** — Reading every record in a table, rather than using an index. Expensive for OLTP; reasonable for analytics with many rows. [Topic 27]

---

## G

**GC pause (garbage collection pause)** — A stop-the-world pause during which a managed language runtime (Java, Go) freezes all application threads. Can last seconds. Makes a node appear unresponsive without being dead. [Topic 21]

**Global index** — A secondary index partitioned by the indexed term (not by the document's partition). Efficient reads (query one partition); writes update multiple index partitions (often asynchronously). [Topic 15]

---

## H

**Happens-before** — A causal relationship: operation A happened before B if B could have observed A. If neither A happened-before B nor B happened-before A, they are concurrent. [Topic 13, 24]

**Hash index** — An in-memory hash map pointing to byte offsets in a log file. Fast point reads; not good for range queries. [Topic 6]

**HDFS (Hadoop Distributed File System)** — A distributed filesystem for storing large files across many machines. The input/output layer for MapReduce and other batch processing frameworks. [Topic 27]

**Head-of-line blocking** — A slow message in a queue blocks all messages behind it. Within a Kafka partition, a slow-processing message delays subsequent messages in that partition. [Topic 30]

**Hinted handoff** — In Dynamo-style databases with sloppy quorums, a write that couldn't reach a home replica is stored temporarily on another reachable node, which then forwards it when the home node recovers. [Topic 13]

**Hot spot** — A partition receiving disproportionately high load. The primary enemy of good partitioning. [Topics 2, 14]

---

## I

**Idempotent** — An operation that produces the same result whether applied once or many times. Enables safe retries. [Topics 9, 17, 33, 35]

**Immutable event log** — An append-only record of events. State can be derived by replaying. Enables audit trails, time travel, and multiple projections from one source. [Topics 31, 35]

**In-doubt transaction** — A 2PC transaction where the participant has voted "yes" but hasn't received the coordinator's decision. Must wait, holding locks. [Topic 25]

**Index** — A data structure that speeds up reads at the cost of slower writes and extra storage. [Topics 6, 7]

**Isolation (ACID)** — Concurrent transactions don't interfere with each other. Ideal: serializable. In practice: Read Committed or Snapshot Isolation. [Topics 17, 18, 19]

---

## J

**Join** — Combining records from two datasets on a matching key. Batch: sort-merge or broadcast hash. Stream: stream-stream, stream-table, or table-table. [Topics 27, 28, 32]

---

## L

**Lag (replication lag)** — The delay between a write being applied on the leader and appearing on a follower. Causes read anomalies. [Topic 11]

**Lamport timestamp** — A logical clock that assigns monotonically increasing numbers to events. Ensures causality: if A → B, then timestamp(A) < timestamp(B). Cannot distinguish concurrent from causal events. [Topic 24]

**Last-write-wins (LWW)** — A conflict resolution strategy that keeps the write with the highest timestamp. Simple but silently loses data. Only safe for write-once immutable keys. [Topics 12, 13, 21]

**Leader** — In single-leader replication, the one node that accepts all writes. Also: the elected coordinator in consensus algorithms. [Topics 10, 26]

**Linearizability** — The strongest single-object consistency guarantee: all operations appear to take effect at a single instant in real time. Once any client reads a new value, all subsequent reads see it. Equivalent to consensus and total order broadcast. [Topics 23, 24, 26]

**Local index** — A secondary index where each partition indexes only its own documents. Efficient writes; scatter/gather reads. Also called document-partitioned index. [Topic 15]

**Log compaction** — In Kafka (or LSM-tree context): keeping only the most recent event per key, discarding older ones. Allows reading current state from offset 0 without a separate snapshot. [Topics 6, 30, 31]

**Log-structured storage** — Treats all writes as sequential appends to a log; reads use an in-memory index (LSM-tree). Optimises for sequential writes. [Topic 6]

**LSM-tree (Log-Structured Merge-tree)** — The index structure underlying log-structured storage engines: in-memory memtable, immutable SSTables on disk, periodic merging. [Topic 6]

---

## M

**MapReduce** — A programming model for distributed batch processing. Mapper extracts key-value pairs; shuffle routes by key; reducer aggregates. [Topic 27]

**Materialized view** — A precomputed, stored result of a query. Updated when the underlying data changes. Trades storage for query speed. [Topics 8, 32]

**Memtable** — The in-memory write buffer in an LSM-tree engine. Flushed to an SSTable when full. [Topic 6]

**Monotonic reads** — A guarantee that a user will never see data "go backwards" across reads. Fixed by pinning each user to one replica. [Topic 11]

**MVCC (Multi-Version Concurrency Control)** — Keeping multiple committed versions of a row so that readers see a consistent snapshot without blocking writers. [Topics 18, 19]

---

## N

**Network partition** — A network failure that splits the cluster into two or more groups that can't communicate. Partitions will happen; systems must have a defined behaviour for them. [Topic 20]

---

## O

**Offset** — In Kafka: a monotonically increasing sequence number per message within a partition. A consumer group tracks its current offset to know where to read from. Analogous to a database replication LSN. [Topic 30]

**OLAP (Online Analytical Processing)** — Read-heavy, aggregation-focused workloads on large datasets. Typically uses column-oriented storage, data warehouses. [Topic 8]

**OLTP (Online Transaction Processing)** — Low-latency, user-facing queries that read/write small numbers of records. Typically uses B-tree indexes. [Topic 8]

**Operational transformation (OT)** — An algorithm for merging concurrent edits to a shared document (e.g., Google Docs). Handles collaborative text editing. [Topic 12]

---

## P

**Partition (shard)** — A subset of a dataset stored on one node. Different from replication (same data, multiple nodes). [Topic 14]

**Phantom** — A write in one transaction that changes the result set of a search query in another. Prevented by serializable isolation (predicate locks or SSI). [Topic 18]

**Point-in-time snapshot** — A consistent view of the database as it appeared at a particular moment. MVCC enables these without locking. [Topics 8, 18]

**Predicate lock** — A lock on all rows matching a search condition, including rows that don't yet exist. Used in 2PL to prevent phantoms. [Topic 19]

**Processing time** — The wall-clock time at which a stream processor processes an event. Distinct from event time. Using processing time for windowed analytics produces incorrect results under backlog. [Topic 32]

---

## Q

**Quorum** — A minimum number of nodes that must agree for an operation to succeed. With n replicas, a common choice is (n+1)/2 (a majority). w + r > n guarantees overlap in leaderless systems. [Topics 13, 22, 26]

---

## R

**Read Committed** — The most common default isolation level. Prevents dirty reads and dirty writes. Still allows read skew, lost updates, and write skew. [Topic 18]

**Read repair** — In leaderless databases, when a client reads from multiple replicas and detects a stale value, it writes the newer value back to the stale replica. [Topic 13]

**Read skew** — Reading inconsistent data because different parts of the database are read at different points in time. Prevented by snapshot isolation. [Topic 18]

**Read-after-write consistency** — A guarantee that a user always sees their own recent writes, even when reading from a follower. [Topic 11]

**Rebalancing** — Moving partitions between nodes when the cluster size changes. Should be minimally disruptive and keep the cluster balanced. [Topic 16]

**Repeatable read** — Often means snapshot isolation (PostgreSQL) or something weaker (MySQL InnoDB). The SQL standard's definition is ambiguous. [Topic 18]

**Replica** — A copy of the data on another node. [Topics 10–13]

**Replication lag** — See Lag. [Topic 11]

**RDD (Resilient Distributed Dataset)** — Spark's abstraction for a distributed, immutable collection with lineage tracking. Enables fault recovery by recomputation. [Topic 29]

---

## S

**Scalability** — A system's ability to handle increased load by adding resources. Load parameters (throughput, latency, concurrent users) define the load to scale. [Topic 2]

**Scatter/gather** — Reading from all partitions in parallel and combining results. Required for secondary index queries with a local (document-partitioned) index. [Topic 15]

**Secondary index** — An index on a non-primary-key attribute. In partitioned systems, must be partitioned as either local (scatter/gather reads) or global (term-partitioned, efficient reads). [Topic 15]

**Serializable isolation** — The strongest isolation level. Results equivalent to some serial execution of all transactions. Three implementations: actual serial execution, 2PL, SSI. [Topic 19]

**Serializable Snapshot Isolation (SSI)** — An optimistic concurrency control mechanism: runs on snapshot isolation, detects serialization conflicts at commit time, and aborts if a transaction acted on an outdated premise. [Topic 19]

**Session window** — A stream processing window that groups events separated by a gap smaller than a threshold. Variable-length; good for user sessions. [Topic 32]

**Shuffle** — The MapReduce step between map and reduce: route all key-value pairs by hash(key) to the correct reducer, sort, and merge. The bottleneck of a MapReduce job. [Topic 27]

**Sibling** — In leaderless or multi-leader systems, two values for the same key that resulted from concurrent writes and have not yet been resolved. [Topics 12, 13]

**Skew** — Uneven distribution of load or data across partitions. Hot spots are the extreme case. Also refers to clock skew (clocks on different machines disagree). [Topics 2, 14, 21]

**Sliding window** — A stream processing window of fixed length that moves forward over time, overlapping with previous windows. Events may appear in multiple windows. [Topic 32]

**Snapshot isolation** — Each transaction reads from a consistent snapshot of the database as of the transaction's start time. Prevents read skew. Doesn't prevent write skew. [Topics 18, 19]

**Sloppy quorum** — In Dynamo-style databases, accepting writes on any reachable nodes (not just the designated home nodes) when home nodes are unavailable. Increases write availability at the cost of weaker freshness guarantees. [Topic 13]

**Sort-merge join** — A batch join algorithm: both datasets are keyed by the join key, shuffled to the same reducer, and merged in sorted order. Works for two large datasets. [Topics 27, 28]

**Split brain** — Two nodes both believe they are the leader and both accept writes. Causes conflicting, potentially irreconcilable data. Prevented by fencing and quorum-based election. [Topics 10, 22]

**SSTable (Sorted String Table)** — An immutable, sorted key-value file on disk. The building block of LSM-tree storage engines. Multiple SSTables are merged into larger ones via compaction. [Topic 6]

**Stale read** — Reading an outdated value from a replica that hasn't received a recent write. Caused by replication lag. [Topic 11]

**Straggler** — In stream processing: an event that arrives after the watermark has already closed the window it belongs to. Must be handled explicitly (drop or emit correction). [Topic 32]

**Streaming** — See Stream processing. [Topic 32]

**Synchronous replication** — The leader waits for a follower to confirm before reporting success. Follower is guaranteed current, but writes stall if the follower is slow. [Topic 10]

**System of record** — The authoritative source of truth for a piece of data. Other representations are derived from it. [Part III README]

---

## T

**Tail latency** — The latency experienced by the slowest requests (e.g., p99 or p999). Often disproportionately important in distributed systems because any multi-request operation is as slow as the slowest sub-request. [Topic 2]

**Term number** — Raft's name for epoch number. [Topic 26]

**Throughput** — The number of operations a system can perform per unit time. [Topic 2]

**Time-of-day clock** — The wall-clock calendar time on a machine. Can jump backward (NTP resets). Unsuitable for measuring durations or ordering events across machines. [Topic 21]

**Tombstone** — A deletion marker in LSM-tree storage or Kafka compacted topics. Indicates a key has been deleted; carries through compaction before being removed. [Topics 6, 13, 31]

**Total order broadcast** — A protocol where all nodes deliver the same messages in the same order, with no gaps. Equivalent to consensus and linearizable CAS. Used in ZooKeeper (Zab) and etcd (Raft). [Topics 24, 26]

**Transaction** — A group of reads and writes treated as a single unit: all commit or all abort. The database's mechanism for reducing many failure modes to one retryable outcome. [Topic 17]

**Tumbling window** — A stream processing window of fixed size that doesn't overlap. A new window starts where the previous one ended. [Topic 32]

**Two-Phase Commit (2PC)** — A protocol for atomic commit across multiple nodes. Phase 1: collect "yes" votes. Phase 2: coordinator writes commit and tells all to commit. Blocking on coordinator failure. Not the same as 2PL. [Topic 25]

**Two-Phase Locking (2PL)** — A serializability algorithm: acquire locks before reads/writes, hold all until commit. Readers block writers and vice versa. Causes deadlocks. Not the same as 2PC. [Topic 19]

---

## U

**Unbundled database** — The architectural pattern of replacing a monolithic database with specialised tools connected by event logs: OLTP database as system of record + CDC stream → search index, cache, analytics store. [Topic 34]

---

## V

**Version vector** — A collection of version numbers, one per replica, that tracks causal relationships across replicas. Can distinguish concurrent from causally-related writes. More informative than Lamport timestamps. [Topics 12, 13, 24]

---

## W

**WAL (Write-Ahead Log)** — An append-only file to which a database writes every change before applying it to the main data structures. Enables crash recovery and is the basis for CDC. [Topics 6, 7, 10, 31]

**Watermark** — In stream processing: a progress signal that says "I'm confident no more events with an event time before T will arrive." Used to close and emit time windows. [Topic 32]

**Write skew** — Two transactions each read the same data, then write to *different* objects based on it, and together violate an invariant. Prevented only by serializable isolation. The staff-level concurrency trap. [Topic 18]

**Write-ahead log** — See WAL.

---

## X

**XA transactions** — The cross-database standard for distributed transactions (heterogeneous 2PC). Poor performance, coordinator often a SPOF, lowest-common-denominator semantics. [Topic 25]

---

## Z

**ZooKeeper** — A coordination service implementing Zab (ZooKeeper Atomic Broadcast), a total order broadcast protocol. Provides linearizable atomic operations, ephemeral nodes, watches, and service discovery. The canonical consensus-as-a-service system. [Topic 26]

**Zxid** — ZooKeeper's transaction ID: a monotonically increasing number assigned to every write. Functions as both a total-order position and a fencing token. [Topics 22, 26]
