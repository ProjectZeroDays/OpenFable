---
name: compose:shell
hidden: true
description: shell
source_platform: openclaw
---

# Shell — Shell Scripting Reference

Quick-reference skill for Bash shell scripting, built-in features, and best practices.

## When to Use

- Writing reliable Bash scripts with proper error handling
- Understanding redirections, pipes, and process substitution
- Managing signals, traps, and background processes
- Debugging shell scripts with set options and tracing
- Choosing between shell features and external tools

## Commands

### `intro`

```bash
scripts/script.sh intro
```

Overview of shell scripting — Bash features, shebang, execution, portability.

### `variables`

```bash
scripts/script.sh variables
```

Variables — declaration, scope, arrays, parameter expansion, special variables.

### `control`

```bash
scripts/script.sh control
```

Control flow — if/else, case, for, while, until, select, functions.

### `redirections`

```bash
scripts/script.sh redirections
```

Redirections and pipes — stdin/stdout/stderr, heredocs, process substitution.

### `safety`

```bash
scripts/script.sh safety
```

Safety practices — set -euo pipefail, quoting, shellcheck, error handling.

### `tools`

```bash
scripts/script.sh tools
```

Essential tools — awk, sed, grep, find, xargs, cut, sort, jq patterns.

### `signals`

```bash
scripts/script.sh signals
```

Signals and traps — SIGTERM, SIGINT, cleanup handlers, background jobs.

### `checklist`

```bash
scripts/script.sh checklist
```

Shell script quality checklist.

### `help`

```bash
scripts/script.sh help
```

### `version`

```bash
scripts/script.sh version
```

## Configuration

| Variable | Description |
|----------|-------------|
| `SHELL_DIR` | Data directory (default: ~/.shell/) |

---

*Powered by BytesAgain | bytesagain.com | hello@bytesagain.com*
