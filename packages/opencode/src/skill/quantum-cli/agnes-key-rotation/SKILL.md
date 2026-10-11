---
name: agnes-key-rotation
description: Rotates Agnes AI API keys across multiple custom providers to manage rate limiting. Use when API calls are hitting rate limits and you need to distribute load across the full key pool.
version: "1.0.0"
author: "Quantum C2"
tags: ["api-keys", "rate-limiting", "rotation", "agnes", "custom-provider"]
trigger_patterns:
  - "rotate api keys"
  - "rate limit"
  - "api key rotation"
  - "key circulation"
  - "distribute keys"
  - "agnes rotation"
  - "key pool"
---

# Agnes Key Rotation

## Purpose

This skill manages a pool of API keys across multiple Agnes AI custom
providers so that calls are distributed in round-robin order, preventing any
single key from hitting the rate limit.  The rotation index is persisted to
`.hermes/agnes_rotation_index.json` so it survives restarts.

## How It Works

### Custom Providers

Eight Agnes keys from the shared rotation pool are registered as individual
custom providers in `config.yaml`:

| Provider            | Env Var              |
|---------------------|----------------------|
| `custom:agnes-rot-0`  | `AGNES_ROTATION_KEY_0` |
| `custom:agnes-rot-1`  | `AGNES_ROTATION_KEY_1` |
| `custom:agnes-rot-2`  | `AGNES_ROTATION_KEY_2` |
| `custom:agnes-rot-3`  | `AGNES_ROTATION_KEY_3` |
| `custom:agnes-rot-4`  | `AGNES_ROTATION_KEY_4` |
| `custom:agnes-rot-5`  | `AGNES_ROTATION_KEY_5` |
| `custom:agnes-rot-6`  | `AGNES_ROTATION_KEY_6` |
| `custom:agnes-rot-7`  | `AGNES_ROTATION_KEY_7` |

Each provider points to `https://apihub.agnes-ai.com/v1` with
`default_model: agnes-2.0-flash`.

### Rotation Mechanics

The script `scripts/agnes_key_rotate.py` maintains:

```json
{"current": 0, "last_used": "2026-09-16T...", "total_requests": 42}
```

State file: `.hermes/agnes_rotation_index.json` (project root).

Commands:

```bash
# Print the current provider (agnes-rot-N) — use this before setting model.provider
python skills/agnes-key-rotation/scripts/agnes_key_rotate.py get-provider

# Advance to the next provider in rotation and persist the new index
python skills/agnes-key-rotation/scripts/agnes_key_rotate.py advance

# Show full rotation statistics
python skills/agnes-key-rotation/scripts/agnes_key_rotate.py status

# Force a specific provider index (0-7)
python skills/agnes-key-rotation/scripts/agnes_key_rotate.py set-provider 3
```

## Usage Patterns

### Pattern 1: Per-request rotation (recommended)

Before each API call, advance the rotation and set the provider:

```cmd
cd "C:\Projects\Quantum C2"
for /f %i in ('python skills\agnes-key-rotation\scripts\agnes_key_rotate.py advance') do set "PROV=%i"
hermes config set model.provider "custom:%PROV%"
hermes ask "your prompt here"
```

### Pattern 2: On rate-limit (429) — failover

When a request returns HTTP 429, advance to the next provider and retry:

```cmd
cd "C:\Projects\Quantum C2" && for /f %i in ('python skills\agnes-key-rotation\scripts\agnes_key_rotate.py advance') do hermes config set model.provider "custom:%i"
```

### Pattern 3: Cron-driven rotation (auto)

A scheduled cron job rotates keys every 30 minutes to proactively manage
rate limits.  The runner script is at:

```
C:\Users\Project Zero\AppData\Local\hermes\scripts\rotate_agnes_keys.bat
```

Cron job ID: `d30db3d9473c` (schedule: `*/30 * * * *`)

### Pattern 3b: Manual batch rotation

```cmd
cd "C:\Projects\Quantum C2\skills\agnes-key-rotation"
rotate_agnes.bat           REM advance + set provider
rotate_agnes.bat advance   REM same as above
rotate_agnes.bat get       REM print current provider only
rotate_agnes.bat status    REM show rotation state
rotate_agnes.bat set 3     REM force provider index 3
```

### Current Rotation State

```cmd
cd "C:\Projects\Quantum C2" && python skills\agnes-key-rotation\scripts\agnes_key_rotate.py status
```

## Venice Admin Key

The Venice admin key is registered as `custom:venice` (env var `VENICE_ADMIN_KEY`)
and is used independently — it is not part of the Agnes rotation pool.

```cmd
hermes config set model.provider custom:venice
```

## Rate Limit Strategy

- **8 keys** × typical per-key limits provide ~8x headroom
- Round-robin distribution ensures even usage across all keys
- The `hermes config check` output will show which keys are currently set
- When a key starts returning 429, the skill automatically advances to the next
- State file records `last_used` timestamp for monitoring key freshness

## Key Management

Keys are stored in the hermes `.env` file:
- `C:\Users\Project Zero\AppData\Local\hermes\.env`
- Environment variables: `AGNES_ROTATION_KEY_0` through `AGNES_ROTATION_KEY_7`
- Venice: `VENICE_ADMIN_KEY`

To add a new key to the pool, append `AGNES_ROTATION_KEY_8=<new-key>` to `.env`,
update `AGNES_ROTATION_PROVIDER_COUNT=9`, and add a matching provider block in
`config.yaml` under the `providers:` section.
