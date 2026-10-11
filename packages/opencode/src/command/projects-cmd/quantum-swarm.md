---
name: quantum-swarm
description: Rapid task-queue driver — works a list of tasks sequentially with verification checkpoints between each (not actual parallel subagents)
---

/swarm $ARGUMENTS

Run this task queue with maximum throughput and honest verification.

## How you must work

1. Parse the arguments into a task list. If no list is given, use the
   pending-work document or open TODOs in the repo.
2. Work tasks one at a time, in the order given unless a dependency
   forces reordering (say so when you reorder).
3. After EVERY task: compile/test the smallest unit that proves it works,
   then mark it in the progress table. A task without a passing check is
   NOT done — never mark it done.
4. If a task fails verification, fix it once; if it fails twice, park it
   in the parked list with the exact error and move on. Do not stall the
   queue on one item.
5. Never claim parallelism you don't have. This harness runs in the
   foreground; tasks execute sequentially. The speed comes from tight
   loops (edit → verify → next), not from fake concurrency.

## Progress table format (update after every task)

| # | Task | Status | Verified by |
|---|------|--------|-------------|

Status values: done / parked / skipped.

## Final report

- done: N
- parked: N (with reasons)
- skipped: N (with reasons)
- next highest-value item
