---
name: freeai-dev-verify
description: Local development and verify cycle for the FreeAI AI Inference Workstation. Orchestrates the repeatable loop of local server checks, git operations, test runs, GitHub Actions/Issues/CodeScanning status, and commit-push cycles. Use when developing the FreeAI website or when the user wants to verify local changes before pushing. Triggers on: "verify the site", "check local dev", "run the dev loop", "test and push", "CI check", "deploy verification".
---

# FreeAI Dev Verify Cycle

This skill orchestrates the tight development loop used for the FreeAI AI Inference Workstation project at `C:\Users\Project Zero\FreeAI_AI-Inference-Workstation\`.

## Project Context

- **Repo**: `https://github.com/ProjectZeroDays/FreeAI_AI_Inference_Workstation`
- **Deployed URL**: `https://projectzerodays.github.io/FreeAI_AI_Inference_Workstation/`
- **Tech stack**: Next.js 16.3.4 App Router, Tailwind CSS v3, MkDocs Material (docs)
- **CI**: GitHub Actions on Python 3.11 (per `.github/workflows/ci.yml`)
- **Local dev server**: `http://localhost:3002`
- **Workspace**: `C:\Users\Project Zero\FreeAI_AI-Inference-Workstation\`

## The Dev Verify Loop

Run this loop whenever making changes to the FreeAI project:

### Step 1 — Local Site Verification

```python
# Check the local dev server is running and responding
import urllib.request

ENDPOINTS = [
    ('/', 'Root page'),
    ('/sitemap.xml', 'Sitemap'),
    ('/robots.txt', 'Robots'),
    ('/examples/demos/streaming_chat.py', 'Streaming demo'),
]

for path, label in ENDPOINTS:
    url = f'http://localhost:3002{path}'
    try:
        r = urllib.request.urlopen(url, timeout=5)
        size = len(r.read())
        print(f'  OK {label}: {r.status} {size} bytes')
    except Exception as e:
        print(f'  FAIL {label}: {e}')
```

Also verify specific content:
- Root page contains "FreeAI" (not "Applivery")
- `sitemap.xml` points to `projectzerodays.github.io`
- `robots.txt` allows crawling
- `streaming_chat.py` demo is syntactically valid

### Step 2 — Git Status & Log

```bash
cd "C:\Users\Project Zero\FreeAI_AI-Inference-Workstation"
git status --short
git log --oneline -5
# Check if examples/ has uncommitted changes
git status --short | findstr examples
```

**Rule**: Never use `git add -A`. Always stage specific files by name.

### Step 3 — Test Suite

```bash
cd "C:\Users\Project Zero\FreeAI_AI-Inference-Workstation"
# Run targeted tests first
python -m pytest tests/test_streaming.py -v --tb=short
python -m pytest tests/test_dashboard_api.py -v --tb=short
# Then run broader suite
python -m pytest tests/ --tb=no -q 2>&1
```

Report format: `<passed> passed, <failed> failed, <skipped> skipped`

### Step 4 — GitHub Status Check

```bash
# Check CI workflow status
gh run list --repo ProjectZeroDays/FreeAI_AI_Inference_Workstation --branch main --limit 3

# Check open issues
gh api repos/ProjectZeroDays/FreeAI_AI_Inference_Workstation/issues?state=open

# Check code scanning alerts
gh api "repos/ProjectZeroDays/FreeAI_AI_Inference_Workstation/code-scanning/alerts" --paginate
```

### Step 5 — Commit & Push (if changes are ready)

```bash
cd "C:\Users\Project Zero\FreeAI_AI-Inference-Workstation"
git add examples/demos/streaming_chat.py docs/README.md  # specific files only
git commit -m "feat: update streaming chat example"
git push origin main
```

Then re-run Steps 1–4 to verify the deployed state.

## Common Patterns to Remember

### Fixed CI Dependency Conflicts (from historical data)

| Package | Bad Pin | Fixed Range | Reason |
|---------|---------|-------------|--------|
| numpy | `==2.5.1` | `>=2.2.0,<2.5` | 2.5.1 requires Python >=3.12 |
| scipy | `==1.18.0` | `>=1.15.0,<1.18` | 1.18.0 requires Python >=3.12 |
| pandas | `==3.0.5` | `>=2.2.0,<2.3` | Version doesn't exist |
| matplotlib | `==3.11.1` | `>=3.9.0,<3.10` | Version doesn't exist |
| autopep8 | `==2.3.2` | `==2.0.4` | 2.3.2 conflicts with flake8's pycodestyle |
| pytest-asyncio | `==1.4.0` | `>=0.23,<0.24` | 1.4.0 requires pytest>=9; CI uses pytest 8.x |
| cryptography | `==50.0.1` | `>=49.0.0,<51` | Conflicts with vastai==1.5.5 which pins ==49.0.0 |

**Key rules:**
- CI installs `requirements-dev.txt` which includes `-r requirements.txt` — both files' constraints are combined
- All pinned versions must exist on PyPI for Python 3.11
- When fixing cascading conflicts, fix one at a time: commit, push, check CI, repeat
- Never pin to exact versions for packages with frequent releases; use ranges

### Website Color System Rules

- Use `bg-[#020617]`, `text-slate-*`, `bg-slate-800/60`, `border-white/10`
- Do NOT use `bg-navy-900`, `text-gray-*`, or `bg-primary`
- Homepage primary color: `blue-600` (#2563eb), NOT `primary` (#0241e3)
- Mobile breakpoint: 640px in globals.css

### SEO Configuration Rules

- robots.txt and sitemap.xml → `projectzerodays.github.io/FreeAI_AI_Inference_Workstation/`
- Layout head: JSON-LD Organization + SoftwareApplication + FAQPage via `<Script strategy="afterInteractive">`
- All pages: title template `%s | FreeAI`, meta description, OpenGraph, Twitter card
- Homepage branding: FreeAI only (no Applivery content)

### File Conventions

- Chat.tsx (170L), Header.tsx (109L), layout.tsx (121L), globals.css (@import at line 5), page.tsx (987L)
- `globals.css` has `@import` at line 5
- README.md: ~500 lines, inverted-pyramid structure

## When to Skip the Full Loop

- **Quick single-file fix**: Steps 1 → 3 → 5 (skip Step 4 unless pushing)
- **Documentation-only change**: Steps 2 → 5 (skip Step 3)
- **Large multi-file change**: Run full loop, but check CI after each logical unit of change
