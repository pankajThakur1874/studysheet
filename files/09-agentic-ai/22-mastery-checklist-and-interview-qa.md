# 22 — Mastery Checklist & Interview Q&A

> The final chapter. Two things: a **checklist** to test whether you can *explain* and *implement* every concept, and a bank of **interview-style Q&A** (beginner → staff) with model answers. If you can tick the boxes and answer these out loud, you've mastered the course.

---

## 1. Can You Explain It? (concept checklist)

Tick each only if you can explain it in plain English *without looking*:

**Foundations**
- [ ] The difference between a plain LLM call, a workflow, and an agent — and when to use each.
- [ ] Why the model is stateless and what the context window actually is.
- [ ] What a token is, and how it drives cost, context size, and latency.
- [ ] The four LLM failure modes (hallucination, cutoff, non-determinism, weak exactness) and a mitigation for each.
- [ ] The agent loop (reason → act → observe → repeat → stop) and why a bound is mandatory.

**Building blocks**
- [ ] How tool use works — who defines, who executes, and the tool_use/tool_result round-trip.
- [ ] Short-term vs long-term memory, and how "remembering" actually works.
- [ ] RAG end to end (chunk → embed → store → retrieve → augment → generate) and RAG vs fine-tuning vs long context.
- [ ] Chain-of-thought, ReAct, plan-and-execute, reflection, tree-of-thoughts — and when each pays off.
- [ ] What belongs in an agent system prompt and why capable models overtrigger on aggressive language.

**Architecture**
- [ ] The five workflow patterns and how they compose.
- [ ] When multi-agent helps vs. when it just multiplies cost; the orchestrator/sub-agent shape.
- [ ] What MCP is and how it maps onto plain tool use.

**Production**
- [ ] How to evaluate a non-deterministic agent (evals, LLM-as-judge, regression suites).
- [ ] What to trace (traces/spans) and the metrics that matter, especially cost.
- [ ] Prompt injection (esp. indirect) and the layered defenses (guardrails, least privilege, sandbox, HITL).
- [ ] The main cost/latency levers (caching, routing, streaming, batching, effort, context).
- [ ] Deploying an agent: stateless design, queues, retries+idempotency, bounds, evals-in-CI.
- [ ] Context engineering and how sub-agents isolate context for long-horizon tasks.

## 2. Can You Build It? (implementation checklist)

Tick each only if you've actually *done* it (Chapter 13 + Chapter 20 projects):
- [ ] Written an agent loop from scratch with the raw SDK (no framework).
- [ ] Defined tools with schemas and executed them safely (sandboxed paths, validated inputs).
- [ ] Correctly appended tool_use + tool_result and handled tool errors as results.
- [ ] Added a `max_steps`/budget bound and seen it stop a runaway.
- [ ] Built a mini-RAG (chunk, embed, retrieve, cite) that answers only from your data.
- [ ] Implemented long-term memory that persists across sessions.
- [ ] Composed a workflow pattern (routing or evaluator-optimizer) in code.
- [ ] Built (or designed) a multi-agent orchestrator with context-isolated sub-agents.
- [ ] Written an eval set + LLM-as-judge and compared two prompt/model versions.
- [ ] Added tracing and read a trace to find a bad step.
- [ ] Put a guardrail + human-in-the-loop gate on a risky action.
- [ ] Cut cost with prompt caching and/or model routing.

---

## 3. Interview Q&A

### Beginner

**Q: What is an AI agent, and how is it different from ChatGPT answering a question?**
An agent is an LLM running in a loop with tools, pursuing a goal with some autonomy over the steps. A single ChatGPT answer is one turn — prompt in, text out. An agent can take actions (via tools), see the results, and decide the next step, repeating until the goal is met. The difference is the *loop* and the *tools*, not the model.

**Q: The model can't run code or browse — so how does an agent "do" things?**
Through tools. You give the model a menu of functions; when it needs one it emits a structured request naming the tool and arguments. *Your code* executes the real function and feeds the result back. The model never runs anything itself — it only asks.

**Q: Why do agents hallucinate, and what do you do about it?**
Because an LLM predicts plausible text, and plausible isn't always true — plus it only knows data up to its training cutoff. You mitigate by grounding it in real data (RAG, tools that fetch truth), requiring verifiable citations, using structured output you can validate, and offloading exact work (math, lookups) to deterministic tools.

### Intermediate

**Q: Walk me through the agent loop.**
You send the model the goal plus the tool list. If it responds with a tool request (`stop_reason: tool_use`), you append that assistant turn to the history, run each requested tool, and append the results as a user message with matching tool_use_ids. You loop — the model now sees the results and decides the next step — until it responds with a final answer instead of a tool call, or you hit a max-steps bound. The message list is the agent's short-term memory; you resend it each call because the model is stateless.

**Q: When would you build a workflow instead of an agent?**
Whenever I can draw the flowchart of steps in advance. If the path is knowable — extract → validate → format, or classify-then-route — a workflow is cheaper, testable, and predictable. I reserve a full agent for open-ended tasks where the steps depend on what the model discovers at runtime, and where errors are recoverable and the value justifies the cost.

**Q: How does RAG work, and when would you use fine-tuning instead?**
RAG retrieves relevant passages from a knowledge source (via embeddings + vector search), pastes them into the prompt, and has the model answer from them — great for knowledge that's large, private, or changing, and it gives citations. Fine-tuning adjusts the model's weights to teach *behavior or style*, not live facts, and it's static (retrain to update). So: RAG for knowledge, fine-tuning for behavior. They combine.

**Q: How do you test something non-deterministic?**
Not with exact-match assertions. I build evals: a dataset of representative cases, a scorer (programmatic checks where possible, LLM-as-judge with a sharp rubric for subjective quality), and a metric I track against a baseline on every change. I evaluate outcome, trajectory, tool use, and cost — run each case multiple times for pass rates — and turn every production bug into a new eval case in a regression suite.

### Advanced / Staff

**Q: An agent works in the demo but is unreliable and expensive in production. How do you fix it?**
Measure first — add tracing (traces/spans) and dashboards for cost, latency, error rate, and steps per task, so I know *which* step is the problem. Then apply targeted fixes: prompt caching for the re-sent prefix (usually the biggest cost win), model routing so easy calls hit a cheap model, effort tuned to difficulty, and context management (compaction, retrieval, bounded tool results) to stop context bloat. For reliability: retries with backoff, idempotency on side-effecting steps, hard bounds on steps/tokens/cost, and a stateless design behind a queue so crashes don't lose work. I'd also add evals in CI so changes can't silently regress.

**Q: What's prompt injection and how do you defend against it?**
It's an attacker planting instructions in content the agent reads, hoping it obeys them — the dangerous form is *indirect*, hidden in third-party content the agent fetches (a web page, a document, a tool result), so the user is innocent but the data is poisoned. No single defense suffices, so I layer: the prompt tells the model to treat external text as data, not commands; external input/output guardrails catch what the prompt misses; least privilege and sandboxing cap the blast radius so a hijacked agent can't do much; human-in-the-loop gates irreversible actions; and monitoring detects attempts. The key mindset: tool/retrieved/web data is untrusted input, and secrets never enter the model's context.

**Q: When is multi-agent worth it, and what's the catch?**
When a single agent's context overflows with unrelated detail, when subtasks are independent enough to parallelize, or when the work needs genuinely distinct specializations/tools. The biggest real win is *context isolation* — each sub-agent works in a fresh window and returns a compact result, keeping the orchestrator lean. The catch is cost and complexity: multi-agent can be many times the tokens of a single agent, with more failure modes and harder debugging, and context is never shared automatically — the orchestrator must pass each sub-agent what it needs. So I only reach for it when a single agent genuinely struggles, and I justify it with traces.

**Q: What's "context engineering" and why does it matter for long-horizon agents?**
It's the discipline of curating exactly what's in the context window at each step — the minimum sufficient tokens for the next action, not the maximum. It matters because the window is finite and fills up, and stuffing it backfires (cost, latency, and "lost in the middle" where the model ignores buried facts). The techniques are compaction (summarize old turns), context clearing (prune stale tool results), externalize-and-retrieve (offload to memory/files, keep pointers), deliberate structure (pin the goal and plan), and — most powerfully — sub-agents that isolate a subtask's detail in a fresh window and hand back only a summary. For a multi-hour run I also give the full goal up front, maintain an explicit plan, persist resumable state, and verify progress periodically.

**Q: How would you decide between the raw API and a framework?**
I'd build the first agent on the raw SDK so I understand the loop — otherwise a framework is a black box I can't debug. Then I choose by dominant need: LangGraph for complex stateful control flow, LlamaIndex for retrieval-heavy apps, CrewAI/AutoGen for multi-agent, a managed platform to offload infrastructure — weighed against lock-in and my team's ecosystem. I keep the agent logic thin so I can swap, and I lean on MCP for integrations regardless of framework. And I remember the framework doesn't do evals, guardrails, observability, or cost control for me — those are still mine.

---

## 4. The 60-Second "What Is Agentic AI" Answer

> "An agent is a large language model running in a loop with tools, pursuing a goal with some autonomy over the steps. The model itself only reads and writes text — so you give it tools it can *request*, your code executes them and feeds the results back, and it keeps reasoning and acting until the goal is met or a bound stops it. Everything else is engineering around an unreliable, stateless, expensive component: memory (short-term in the context window, long-term in external stores you retrieve from), RAG to ground it in real data, workflow patterns to keep it predictable, evals because it's non-deterministic, guardrails and least privilege because it can take harmful actions, observability because it's multi-step, and context engineering because the window is finite. The craft is spending autonomy and context deliberately — use the least autonomous design that solves the problem, and put in the window only what the next step needs."

---

## 5. Where to Go Next

- **Build** the capstones (Chapter 20) — reading is not mastery.
- **Follow the primary sources** — Anthropic's *Building Effective Agents* (the workflow patterns), the ReAct paper, and your model provider's current docs (APIs and model behavior change fast — verify, don't trust year-old blog posts).
- **Re-read Chapter 19** (context engineering) once you've built something long-horizon — it lands differently after you've hit a context wall.
- **Keep a lessons file** (practice what Chapter 5 preaches): every agent bug you fix, write down the fix.

---

## Key Takeaways

- **Mastery = can explain + can build.** Use the two checklists honestly; the gaps are your study list.
- If you can answer the **staff-level Q&A out loud** — production reliability, prompt injection defense-in-depth, when multi-agent is worth it, context engineering — you understand agents at a senior level.
- The through-line of the whole course: **an agent is good engineering around an unreliable, stateless, costly component.** Spend autonomy and context deliberately; ground it, bound it, evaluate it, secure it, observe it.
- **Now go build one** — and keep a lessons file as you do.

*— End of the Agentic AI course. Start at [00 — README/Index](00-README-index.md) to review the map, or jump to any chapter.*
