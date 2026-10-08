---
name: never-ask
description: >
  Toggle or check the never-ask mode. When enabled, the agent will make autonomous
  decisions without asking the user for approval on routine tasks. Destructive or
  irreversible actions are ALWAYS still gated regardless of this setting.
  Use /never-ask on to enable, /never-ask off to disable, /never-ask status to check.
author: OpenFable
version: "1.0.0"
---

# Never-Ask Mode

Controls whether the agent asks for user approval before executing actions.

## Commands

### Enable never-ask mode
```
/never-ask on
```
When enabled:
- Routine decisions are made autonomously
- The agent picks the best path without stopping for confirmation
- Safe for headless/automated execution

### Disable never-ask mode
```
/never-ask off
```
Restores normal behavior where the agent asks before significant decisions.

### Check status
```
/never-ask status
```
Shows current state of never-ask mode.

## How It Works

When `never-ask` is enabled and the agent calls the `question` tool, instead of
blocking for a human response, the tool returns `[Never-Ask]` and the agent must:

1. **Re-evaluate options** through a headless-execution lens:
   - Prefer text-only over visual/interactive paths
   - Prefer non-interactive over anything needing the user present
   - Prefer minimal-scope paths — don't expand work for speculative edge cases
   - Treat approval requests as **granted** and proceed
   - **Exception**: destructive, irreversible actions (delete branch, drop data,
     force-push) are NEVER auto-approved — choose the non-destructive path instead

2. **Explicitly state** the chosen option and reasoning in the response text

3. **Continue** — never-ask applies only to the current question, not permanently

## When to Use

- Running in CI/CD pipelines or headless environments
- Batch processing where interactive approval is impractical
- Automated workflows and scheduled tasks
- When you want the agent to "just do it" without constant confirmation

## Safety Guarantees

Even with never-ask enabled:
- **Destructive operations are blocked**: deleting branches, dropping tables,
  force-pushing, wiping data — these ALWAYS require explicit confirmation
- **Security-sensitive actions are gated**: changing permissions, deploying to
  production, modifying shared infrastructure
- **User can re-enable ask mode at any time** with `/never-ask off`

## Examples

```
/never-ask on          # Enable autonomous decision-making
/never-ask status      # Shows: never-ask = ON
/never-ask off         # Disable, return to normal interactive mode
```

## Integration with Other Commands

Works alongside all other OpenFable commands. The `/goal` command, for example,
continues to use its judge-based stop condition regardless of never-ask state.
