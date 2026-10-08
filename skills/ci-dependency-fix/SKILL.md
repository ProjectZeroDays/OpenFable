---
name: ci-dependency-fix
description: Iterative resolution of cascading pip dependency conflicts in CI. Use when GitHub Actions CI fails due to requirements.txt version conflicts, ResolutionImpossible errors, or Python version incompatibilities. Covers the fix-loop pattern: identify conflict → fix one package → commit → push → re-check CI → repeat. Especially relevant for Python 3.11 CI environments.
---

# CI Dependency Conflict Fix

This skill documents the proven pattern for resolving cascading dependency conflicts in CI, as experienced in the FreeAI AI Inference Workstation project.

## The Fix Loop

When CI fails with a dependency resolution error:

1. **Read the CI failure** — Get the full error output from `gh run view <id> --log-failure`
2. **Identify the root conflict** — Find the package pair causing `ResolutionImpossible`
3. **Fix ONE conflict at a time** — Change pins to compatible ranges, commit, push
4. **Re-check CI** — Don't fix everything at once; each failure reveals the next conflict
5. **Repeat** until CI passes

**Never try to fix all conflicts in a single commit.** The cascade reveals itself one error at a time.

## Common Conflict Patterns

### Pattern 1: Non-existent Package Versions

Some exact pins reference versions that don't exist on PyPI:

```
numpy==2.5.1   → doesn't exist (requires Python >=3.12)
scipy==1.18.0  → doesn't exist (requires Python >=3.12)
pandas==3.0.5  → doesn't exist (current max ~2.2.x)
matplotlib==3.11.1 → doesn't exist (current max ~3.9.x)
```

**Fix**: Use ranges that cap below the non-existent version:
```
numpy>=2.2.0,<2.5
scipy>=1.15.0,<1.18
pandas>=2.2.0,<2.3
matplotlib>=3.9.0,<3.10
```

### Pattern 2: Transitive Dependency Conflicts

Package A pins `X==1.0` but Package B requires `X>=2.0`:

```
autopep8==2.3.2 requires pycodestyle>=2.12.0
flake8==6.0.0 requires pycodestyle<2.11.0
→ These are mutually exclusive
```

**Fix**: Downgrade to a compatible version:
```
autopep8==2.0.4  (compatible with flake8 6.0.0's pycodestyle<2.11.0)
```

Or unpinned where safe:
```
flake8        (unpinned — let pip resolve)
pycodestyle   (unpinned — let pip resolve)
```

### Pattern 3: Tool Version Split

Some packages have a version boundary that splits requirements:

```
pytest-asyncio<0.24  → requires pytest<9
pytest-asyncio>=0.24 → requires pytest>=9.0.3
```

**Fix**: Keep both in the same range band:
```
pytest>=8.0,<9
pytest-asyncio>=0.23,<0.24
```

### Pattern 4: Conflicting Exact Pins

Package A pins `cryptography==50.0.1`, Package B pins `cryptography==49.0.0`:

```
cryptography==50.0.1  (project direct pin)
vastai==1.5.5         (pins cryptography==49.0.0 transitively)
```

**Fix**: Use a range that satisfies both:
```
cryptography>=49.0.0,<51
```

## Files to Check

CI installs `requirements-dev.txt` which includes `-r requirements.txt`. Both files' constraints are combined by pip. Always keep version pins consistent across both files.

```
requirements.txt       # Main dependencies
requirements-dev.txt   # Dev dependencies (includes -r requirements.txt)
```

## Python 3.11 Compatibility Rules

The FreeAI CI runs on Python 3.11. Common incompatibilities:

| Package | Version | Minimum Python | Fix |
|---------|---------|---------------|-----|
| numpy | >=2.5.0 | >=3.12 | Use <2.5 |
| scipy | >=1.18.0 | >=3.12 | Use <1.18 |
| pandas | >=3.0.0 | >=3.12 | Use <2.3 |
| matplotlib | >=3.11.0 | >=3.12 | Use <3.10 |
| pytest-asyncio | >=0.24 | pytest>=9 | Stay in 0.23.x / pytest 8.x |

## Diagnostic Commands

```bash
# Check what CI is installing
gh run list --repo ProjectZeroDays/FreeAI_AI_Inference_Workstation --branch main --limit 3
gh run view <run_id> --log --job <job_id> 2>&1 | Select-String -Pattern "error|Error|ERROR|ResolutionImpossible|conflict"

# Simulate pip resolution locally
cd "C:\Users\Project Zero\FreeAI_AI-Inference-Workstation"
pip install -r requirements.txt --dry-run 2>&1
pip install -r requirements-dev.txt --dry-run 2>&1

# Check if a version exists on PyPI
pip index versions numpy 2>&1
```

## Commit Etiquette

- Never use `git add -A` — stage specific files to avoid committing large/generated assets
- Use descriptive commit messages: `fix: loosen autopep8/flake8 pins to resolve CI conflict`
- After each fix commit, push and check CI before making the next change
