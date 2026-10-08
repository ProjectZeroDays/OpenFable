---
name: "provider-rotation"
description: "Rotates AI providers/API keys proactively when rate limits are about to hit: detects 429/quota signals, pre-checks connectivity of the next candidate, switches, and logs the rotation. Uses the 7 Agnes accounts as the primary rotation pool. Activate on /provider-rotate, on rate-limit errors, or whenever the user invokes /auto model rotation."
version: "1.0.0"
author: "Project Zero"
tags: ["providers", "rate-limits", "failover", "agnes", "connectivity"]
trigger_patterns:
  - "/provider-rotate"
  - "/auto"
  - "rate limit"
  - "429"
  - "quota"
  - "switch provider"
  - "rotate api key"
---

# Provider Rotation — rate-limit-aware AI provider switching

You are the **rotation controller**. Your job: keep AI calls flowing by switching
provider/key **before or at** rate-limit imposition, never after a hard failure cascade.
Keys are NEVER in this file — they live in env vars loaded from a secret file.

## 0. Load keys (always first step)

```bash
set -a; . "$HOME/.zcode/provider-keys.env"; set +a
```

If that file is missing, fall back to the active repo's gitignored `.env`.
Never echo key values into output, logs, or files. Print only provider names + HTTP codes.

## 1. Detection — when to rotate

Rotate when ANY of these appears:
- HTTP 429, or body contains `rate_limit` / `quota` / `insufficient_quota`
- Provider warns of impending limit (headers `x-ratelimit-remaining` ≤ 10% of limit,
  or `retry-after` present)
- Repeated 5xx / timeouts from the current provider (2 consecutive)

**Proactive mode** (the default for `/auto`): before each long batch of AI work, check
`x-ratelimit-remaining` on the current provider; if ≤ 20%, rotate BEFORE starting.

## 2. Rotation chain (ordered; verified live 2026-10-02)

| # | Provider | Env key | Base URL env | Models endpoint status |
|---|----------|---------|--------------|------------------------|
| 1 | agnes-2 | `AGNES_API_KEY_2` | `AGNES_BASE_URL` | 200 (12 models) |
| 2 | agnes-3 | `AGNES_API_KEY_3` | `AGNES_BASE_URL` | 200 |
| 3 | agnes-4 | `AGNES_API_KEY_4` | `AGNES_BASE_URL` | 200 |
| 4 | agnes-5 | `AGNES_API_KEY_5` | `AGNES_BASE_URL` | 200 |
| 5 | agnes-6 | `AGNES_API_KEY_6` | `AGNES_BASE_URL` | 200 |
| 6 | agnes-7 | `AGNES_API_KEY_7` | `AGNES_BASE_URL` | 200 |
| 7 | agnes-1 | `AGNES_API_KEY_1` | `AGNES_BASE_URL` | **401 — key rejected, repair before relying on it** |
| 8 | abliteration | `ABLITERATION_API_KEY` | `ABLITERATION_BASE_URL` | 200 (model ID is `abliterated-model-large-v2`) |
| 9 | openrouter | `OPENROUTER_API_KEY` | `OPENROUTER_BASE_URL` | 200 (464 models) |
| 10 | venice | `VENICE_INFERENCE_KEY` | `VENICE_BASE_URL` | 200 (128 models) |
| 11 | opencode-zen | `OPENCODE_ZEN_API_KEY` | `OPENCODE_ZEN_BASE_URL` | 200 (85 models) |
| 12 | xiaomi | `XIAOMI_API_KEY` | `XIAOMI_ANTHROPIC_BASE_URL` (fallback from OpenAI endpoint, which 401s) | needs repair |
| 13 | hf-endpoint | `HF_TOKEN` | `HF_ENDPOINT_BASE_URL` | 503 = paused endpoint; resume in HF console before use |
| 14 | lmstudio (local) | `LMSTUDIO_API_KEY` | `LMSTUDIO_BASE_URL` | 000 = not running; last resort only |

Agnes model ID to request: `AGNES_MODEL` (`Agnes-2.6-flash` — confirm against the
account's `/models` list at rotation time; the catalog also exposes
`agnes-3.0-flash` / `agnes-2.5-pro-alpha`).

## 3. Pre-switch connectivity check (MANDATORY before every switch)

```bash
check_provider() {  # $1=name $2=key $3=url
  code=$(curl -s -o /dev/null -m 8 -w "%{http_code}" -H "Authorization: Bearer $2" "$3/models")
  echo "$1 $code"
}
```

- `200` → switch to it.
- `429` → skip (rate-limited), try next.
- `401/403` → mark dead in the rotation log, skip, try next.
- `000/5xx` → unreachable, skip, try next.
Never switch blind — the whole point is landing on a working provider.

## 4. Applying the switch

- **Repo app (`src/quantum` AI Supervisor):** edit the gitignored `.env` — set the active
  provider's `*_API_KEY`/`*_BASE_URL` vars (e.g., make `AGNES_API_KEY_5` the active
  `AGNES_API_KEY` alias if the app expects a single var). Restart the supervisor process.
- **Shell scripts / agents:** `set -a; . ~/.zcode/provider-keys.env; set +a` then export the
  chosen provider's vars as the canonical names the script consumes.
- **ZCode itself:** the session model is user-controlled; for subagents pass the rotated
  provider via `subagent_model` if it is a registered model, otherwise run provider-bound
  work through scripts that consume the env vars above.
- **opencode / mimocode:** per-tool config is tracked as a task in `TASKS_CHECKLIST.md`
  (Phase 7); until configured, rotate them manually per `~/provider-cheatsheet.md`.

## 5. Logging & hygiene

- Append every rotation to `~/.zcode/logs/provider-rotation.log`:
  `UTC-timestamp | from | to | reason(429/proactive/5xx) | pre-check code`.
- If a provider 401s, do NOT retry it for the rest of the session; flag for key repair.
- Never place key values in the log, the skill, or any git-tracked file — env file and
  gitignored `.env` only. Rotate keys in BOTH files together.
- The old Abliteration key (`ABLITERATION_KEY` in the repo `.env`) is legacy — cutover to
  `ABLITERATION_API_KEY` then remove it.
