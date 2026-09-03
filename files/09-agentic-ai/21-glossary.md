# 21 — Glossary

> Every term in this course, in plain English, with a pointer to the chapter that covers it. Use it as a quick reference or a self-quiz (cover the definitions and define each term yourself).

---

## Core Concepts

- **Agent** — an LLM running in a loop, using tools, to pursue a goal with autonomy over the steps. (Ch 1, 3)
- **Agentic AI** — software where an LLM dynamically directs its own process and tool use to accomplish a task. (Ch 1)
- **Workflow** — LLM calls wired together in code *you* control (fixed path), as opposed to an agent that chooses its own path. (Ch 9)
- **Autonomy** — how much the LLM (vs. your code) decides what happens next; the axis from plain call → workflow → agent. (Ch 1)
- **Agent loop** — reason → act (tool call) → observe (result) → repeat, until done. The core of every agent. (Ch 3)
- **ReAct** — "Reasoning + Acting": interleaving thinking with tool calls; the pattern behind the agent loop. (Ch 3, 7)

## LLM Fundamentals

- **LLM (Large Language Model)** — a model that predicts the next chunk of text; the reasoning engine of an agent. (Ch 2)
- **Token** — a sub-word chunk of text (~¾ of a word); the unit of cost, context size, and latency. (Ch 2)
- **Context window** — the max tokens a model can consider at once (prompt + output); the model's *only* memory for one call. (Ch 2, 5)
- **Autoregressive** — generating output one token at a time, each conditioned on the previous. (Ch 2)
- **Temperature** — randomness of token sampling; low = consistent (use for agents), high = creative. (Ch 2)
- **Hallucination** — fluent, confident, false output; a core failure mode to design around. (Ch 2)
- **Knowledge cutoff** — the model only knows data up to its training date; fixed via tools/RAG. (Ch 2)
- **Non-determinism** — same input can give different output; why you evaluate rather than exact-test. (Ch 2, 14)
- **Structured output** — forcing the model to return machine-readable data (usually JSON matching a schema). (Ch 2, 8)
- **System / user / assistant** — message roles: standing instructions / human input / the model's replies. (Ch 2, 8)
- **Effort** — a control for how much a model reasons/spends per request; match to task difficulty. (Ch 7, 17)
- **Lost in the middle** — models attend best to the start/end of a long context; middle info gets ignored. (Ch 2, 19)

## Tools & Actions

- **Tool / function calling** — giving the model functions it can request; *your code* executes them and returns results. (Ch 4)
- **Tool definition** — name + description (when to use) + input schema (JSON Schema of arguments). (Ch 4)
- **tool_use / tool_result** — the model's structured request to call a tool, and the result you send back (matched by an id). (Ch 4)
- **Parallel tool use** — multiple tool calls in one turn; return all results in one message. (Ch 4)
- **Server-side tool** — a tool the provider hosts and runs (e.g. web search, code execution); you just declare it. (Ch 4)
- **Client-side tool** — a tool you define and execute in your own code. (Ch 4)
- **tool_choice** — controls whether/which tool the model must use (auto / any / specific / none). (Ch 4)
- **Sandbox** — an isolated environment where tool code runs with no access to prod/secrets/network. (Ch 16)

## Memory & Retrieval

- **Short-term memory** — the conversation/work carried in the context window (resent each call). (Ch 5)
- **Long-term memory** — durable info stored outside the model and retrieved back into context. (Ch 5)
- **Episodic / semantic / procedural memory** — past events / facts / how-to knowledge. (Ch 5)
- **RAG (Retrieval-Augmented Generation)** — retrieve relevant text → add to prompt → generate a grounded answer. (Ch 6)
- **Embedding** — a vector capturing text meaning; nearby vectors = similar meaning. (Ch 6)
- **Vector database** — stores embeddings and returns nearest neighbors for semantic search. (Ch 6)
- **Chunking** — splitting documents into passages for embedding/retrieval; size and overlap matter. (Ch 6)
- **Reranking** — re-scoring retrieved candidates with a more precise model to keep the best few. (Ch 6)
- **Grounding** — answering from provided/retrieved facts rather than training memory. (Ch 6, 8)
- **Compaction** — summarizing old context into a shorter form to free tokens. (Ch 5, 19)
- **Context editing / clearing** — pruning stale tool results/thinking from context (delete, not summarize). (Ch 5, 19)
- **Fine-tuning** — adjusting model weights on your data; teaches behavior/style, not live facts (contrast RAG). (Ch 6)

## Reasoning & Patterns

- **Chain-of-thought (CoT)** — having the model reason step by step before answering; boosts accuracy. (Ch 7)
- **Plan-and-execute** — write a plan first, then execute the steps (and re-plan when needed). (Ch 7)
- **Reflection / self-critique** — the model critiques and revises its own output. (Ch 7)
- **Evaluator-optimizer** — one component generates, another evaluates against criteria, generator revises in a loop. (Ch 7, 9)
- **Tree-of-thoughts** — exploring multiple reasoning paths with backtracking; powerful but expensive. (Ch 7)
- **Prompt chaining** — a fixed sequence of LLM calls, each using the last's output, with gates. (Ch 9)
- **Routing** — classify input, send it down a specialized path. (Ch 9)
- **Parallelization** — run LLM calls concurrently (sectioning or voting) and aggregate. (Ch 9)
- **Orchestrator-workers** — a central LLM dynamically splits a task, delegates to workers, synthesizes. (Ch 9, 10)

## Multi-Agent & Protocols

- **Multi-agent system** — multiple specialized agents collaborating on a larger goal. (Ch 10)
- **Orchestrator / sub-agent** — a coordinator that delegates subtasks to focused agents and integrates results. (Ch 10)
- **Handoff** — passing control from one agent to another based on the situation. (Ch 10)
- **Context isolation** — giving a sub-agent a fresh window so its detail doesn't pollute the orchestrator. (Ch 10, 19)
- **MCP (Model Context Protocol)** — an open standard ("USB-C for AI tools") to connect apps to tools/data via servers. (Ch 11)
- **MCP host / client / server** — the app / its per-server connector / the program exposing tools/resources/prompts. (Ch 11)
- **Resource (MCP)** — read-only data an MCP server exposes; **Prompt (MCP)** — a reusable template it provides. (Ch 11)

## Building, Ops & Safety

- **Prompt engineering** — writing the instructions/system prompt that shape agent behavior. (Ch 8)
- **Context engineering** — curating exactly what's in the context window at each step; the frontier skill. (Ch 19)
- **Few-shot** — including example input→output pairs in the prompt to lock in a behavior/format. (Ch 8)
- **Eval** — measuring output quality against criteria across many cases (the agent's "test suite"). (Ch 14)
- **LLM-as-judge** — using a model to score outputs against a rubric. (Ch 14)
- **Regression suite** — eval cases run on every change to catch what breaks (incl. every past bug). (Ch 14)
- **Observability** — making the agent's steps visible for debugging and monitoring. (Ch 15)
- **Trace / span** — one full run / one step within it (LLM call, tool run) with timing & metadata. (Ch 15)
- **Prompt injection** — malicious instructions planted in content the agent reads (direct or indirect). (Ch 16)
- **Jailbreak** — a prompt that tricks the model into bypassing its safety rules. (Ch 16)
- **Guardrails** — input/output checks around the model that block unsafe content or actions. (Ch 16)
- **Least privilege** — giving the agent only the tools/permissions it needs, to cap blast radius. (Ch 16)
- **Human-in-the-loop (HITL)** — requiring human approval before high-stakes/irreversible actions. (Ch 16)
- **Idempotency** — a step safe to run more than once without duplicate side effects (for retries). (Ch 18)
- **Prompt caching** — reusing a stable prompt prefix at reduced cost/latency; broken by any prefix change. (Ch 17)
- **Model routing** — sending each task to the cheapest model that can handle it. (Ch 17)
- **Streaming** — sending output tokens as generated to cut perceived latency and avoid timeouts. (Ch 17)
- **Batching** — processing many non-urgent requests together at a discount. (Ch 17)
- **Long-horizon task** — a job spanning many steps/hours where context management is the bottleneck. (Ch 19)

---

## Quick Self-Quiz

Cover the definitions above and define these from memory — if you can, you know the course:
`agent loop` · `context window` · `tool_use vs tool_result` · `RAG` · `embedding` · `short vs long-term memory` · `ReAct` · `orchestrator-workers` · `evaluator-optimizer` · `MCP` · `prompt injection (indirect)` · `least privilege` · `HITL` · `prompt caching` · `context engineering` · `LLM-as-judge` · `trace/span` · `idempotency`.

**Next:** *22 — Mastery Checklist & Interview Q&A* — prove you can explain and build it.
