# Study Guide — Distributed Systems, System Design & Agentic AI

A teaching repository for senior/staff backend interview prep. It is **three courses in one**, ordered as a single curriculum.

> **Start with [`00-start-here/00-how-to-use-this-guide.md`](00-start-here/00-how-to-use-this-guide.md)** — it's the map: the three tracks, how they relate, and which learning path fits your goal.

---

## The three tracks

| Track | Folders | What it teaches |
|-------|---------|-----------------|
| **1 · Foundations** (the *why*) | `01-foundations`, `02-distributed-data`, `03-derived-data` | Built on *Designing Data-Intensive Applications*. Storage engines, replication, partitioning, transactions, consistency, consensus, batch & stream processing. |
| **2 · System Design** (the *what*) | `04-book-vol-1`, `05-book-vol-2`, `06-concepts-bytebytego`, `07-design-questions`, `08-edge-cases` | Apply the theory: canonical book chapters, 42 fully worked design problems, and the failure-mode reasoning that separates senior from staff. |
| **3 · Agentic AI** (specialty) | `09-agentic-ai` | A self-contained course for AI/agent roles. |

---

## Folder layout

```
files/
├── 00-start-here/          ← read this first: the map + learning paths
│
├── 01-foundations/         ┐
├── 02-distributed-data/    │  Track 1 — distributed-systems theory (DDIA)
├── 03-derived-data/        ┘
│
├── 04-book-vol-1/          ┐
├── 05-book-vol-2/          │
├── 06-concepts-bytebytego/ │  Track 2 — system-design interview
├── 07-design-questions/    │     (07 = 42 worked problems; 00-index.md inside)
├── 08-edge-cases/          ┘     (08 = 6 resilience deep-dive tiers)
│
└── 09-agentic-ai/          ← Track 3 — agentic AI course (00-README-index.md inside)
```

Each track folder has its own overview/index file (`00-*`) with a topic table and reading order.

---

## The house style

Every file is teaching-first: each concept starts from **the failure that forced it to exist**, then the mechanism, then the trade-offs, then the interview answer. Real Mermaid diagrams (not ASCII), trade-off tables, failure scenarios, 3-level interview Q&A, a 30-second spoken answer, and a compressed mental model.

The gold-standard reference for the format is [`07-design-questions/05-rate-limiter.md`](07-design-questions/05-rate-limiter.md).

---

## What Track 1 is actually arguing

Most people read DDIA as a catalogue of technologies. It isn't — it's an argument:

1. There is no one-size-fits-all data system.
2. Every storage design trades read cost, write cost, and space — you can't optimize all three.
3. The hard part of distribution isn't scale; it's that **partial failure** is now possible and undetectable.
4. Strong guarantees (linearizability, serializability, distributed transactions) are purchasable but expensive — most of the time you can get what you actually need more cheaply.
5. The most robust systems are built from **immutable event logs plus derived views**, because that structure survives bugs, schema changes, and reprocessing.

If you can defend points 3 and 5 with concrete examples, you've understood the foundations.
