---
name: api-key-rotation
description: Manages multiple custom AI provider API keys with automatic round-robin rotation for rate-limit management. Configures hermes custom providers, auto-circulates keys across requests, and provides status/monitoring endpoints.
metadata:
  short-description: Multi-provider API key rotation with auto-circulation for rate limiting
  providers: [agnes]
  models: [agnes-2.0-flash, agnes-pro, agnes-standard, agnes-lite]
  rate-limiting: true
  rotation: true
version: 1.0.0
author: Quantum C2
tags: [ai, providers, rate-limiting, rotation, keys, hermes]
trigger_patterns:
  - "rotate api keys"
  - "api key rotation"
  - "rate limit"
  - "circulate keys"
  - "provider rotation"
  - "key management"
---

# API Key Rotation — Multi-Provider Auto-Circulation

Manages multiple custom AI provider API keys with automatic round-robin rotation to prevent rate-limit exhaustion. Configures hermes custom providers, auto-circulates keys across requests, and provides status/monitoring endpoints.

## Overview

Each API key becomes its own hermes custom provider. A rotation script auto-circulates through them — when one key approaches its rate limit, the system switches to the next, distributing load evenly.

## Hermes CLI Configuration

The skill configures hermes custom providers using:
```bash
hermes config set model.provider custom
hermes config set model.baseurl https://apihub.agnes-ai.com/v1
hermes config set model.apikey <key>
hermes config set model.default agnes-2.0-flash
```

### Provider Base URLs

| Provider | Base URL | Models |
|----------|----------|--------|
| Agnes AI | `https://apihub.agnes-ai.com/v1` | `agnes-2.0-flash`, `agnes-pro`, `agnes-standard`, `agnes-lite` |
| Venice AI | `https://api.venice.ai/api/v1` | `llama-3.3-70b`, `llama-3.1-405b`, `llama-3.1-8b`, `deepseek-r1-671b`, `mistral-7b`, `qwen-2.5-72b`, `gemma-2-9b` |

## Supported API Keys

### Agnes AI (8 keys)

| Env Var | Key (masked) |
|---------|-------------|
| `AGNES_API_KEY_1` | `sk-4qBlB...` |
| `AGNES_API_KEY_2` | `sk-DavUf...` |
| `AGNES_API_KEY_3` | `sk-gE940p...` |
| `AGNES_API_KEY_4` | `Sk-gE940p...` (alt-case prefix) |
| `AGNES_API_KEY_5` | `sk-qxfme7...` |
| `AGNES_API_KEY_6` | `sk-zI6D3b...` |
| `AGNES_API_KEY_7` | `sk-Po1GN9...` |
| `AGNES_API_KEY_8` | `e3867409-...` (UUID format) |

### Venice AI (multi-key supported)

| Env Var | Key |
|---------|-----|
| `VENICE_API_KEY` | Primary Venice key (legacy) |
| `VENICE_API_KEY_1` | Venice key slot 1 |
| `VENICE_API_KEY_2` | Venice key slot 2 |
| `VENICE_API_KEY_3` | Venice key slot 3 |
| `VENICE_API_KEY_4` | Venice key slot 4 |

## Auto-Circulation Mechanism

The rotation uses **round-robin** with time-based intervals:

- **Default rotation interval**: 30 seconds (`KEY_ROTATE_INTERVAL`)
- Each provider maintains its own rotation index
- On rotation, the key index advances to the next key
- After the last key, it wraps to index 0
- When a 429 (rate limited) response is received, the system immediately rotates to the next key

## Scripts

### `scripts/setup_providers.py`

Configures all custom providers in hermes CLI:

```bash
# Configure Agnes providers (8 keys)
python scripts/setup_providers.py --provider agnes

# Configure Venice providers
python scripts/setup_providers.py --provider venice

# Configure all providers
python scripts/setup_providers.py --all
```

### `scripts/rotate_keys.py`

Auto-circulates API keys for rate-limit management:

```bash
# Auto-circulate all provider keys
python scripts/rotate_keys.py --all

# Rotate a specific provider
python scripts/rotate_keys.py --provider agnes

# Continuous rotation loop (background)
python scripts/rotate_keys.py --daemon --interval 30
```

### `scripts/key_status.py`

Check current rotation status:

```bash
# Show status of all providers
python scripts/key_status.py

# Show status for a specific provider
python scripts/key_status.py --provider agnes
```

## Backend Integration

The Quantum C2 backend already implements the rotation pattern:

- **`backend/app/services/quantum_framework/agnes_ai_chatbot.py`**: `AgnesAIProvider` class with `_get_next_key()` round-robin method
- **`backend/app/routers/chatbot.py`**: `_agnes_keys` list with `_get_agnes_key()` / `_rotate_agnes_key()` functions, plus `rotate_provider_key()` and `auto_circulate_keys()` for generic provider rotation
- **`backend/app/routers/chatbot.py`**: API endpoints:
  - `POST /api-key/rotate-key` — rotate Agnes key
  - `POST /api-key/rotate-key/{provider}` — rotate specific provider key
  - `POST /api-key/auto-circulate` — auto-circulate all keys
  - `GET /api-key/status` — show key availability
  - `GET /rate-limit/status` — show rate limit status

## Environment Variables

```bash
# AI Provider Keys — Agnes (8 keys)
AGNES_API_KEY=          # primary / legacy single key
AGNES_API_KEY_1=        # key 1
AGNES_API_KEY_2=        # key 2
AGNES_API_KEY_3=        # key 3
AGNES_API_KEY_4=        # key 4
AGNES_API_KEY_5=        # key 5
AGNES_API_KEY_6=        # key 6
AGNES_API_KEY_7=        # key 7
AGNES_API_KEY_8=        # key 8 (UUID format)

# Venice AI (multi-key)
VENICE_API_KEY=
VENICE_API_KEY_1=
VENICE_API_KEY_2=
VENICE_API_KEY_3=
VENICE_API_KEY_4=

# Rotation interval (seconds)
KEY_ROTATE_INTERVAL=30
```

## Rate-Limiting Strategy

1. Each key gets its own rate-limit budget (60 requests/minute per key)
2. Round-robin distributes requests evenly across all keys
3. On 429 response, immediately rotate to next key
4. Cooldown period of 1 second between requests per key
5. Failed keys are temporarily blacklisted (skipped for 60s)
