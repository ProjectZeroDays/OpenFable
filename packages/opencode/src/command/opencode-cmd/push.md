---
description: Full release pipeline — error-correct, refactor, test, debug, build assets, merge main, tag + release, push
---

Run the complete SpyTV release pipeline for the repository at `C:\Projects\SpyTV`.
Work through every phase in order, fix anything that fails, and report the result
of each phase at the end. Optional argument: $ARGUMENTS (a target tag like
`v1.2.0`; if empty, use the next semantic version after the latest GitHub
release).

## Phase 1 — Error-correct & refactor
- `git status` / `git diff` review of all pending and recent changes.
- Fix obvious errors: broken imports, dead code, deprecated API usage,
  duplicated logic, inconsistent naming (SpyTV branding — never CAP-13X/cap13).
- Keep refactors behavior-preserving; do not redesign features.

## Phase 2 — Test all code
- `python -m pytest -q` — every test must pass.
- `python -m compileall -q` (or `py_compile` over all `.py` files).
- Syntax-check dashboard/JS assets (`node --check` on extracted
  `c2_dashboard.html` script block and `tvreaper.js`).
- Parse all YAML configs; verify `.env.example`/`.gitignore` consistency.

## Phase 3 — Debug
- Any failure from Phase 2 must be fixed and re-run until green.
- Watch for known traps: non-reentrant `threading.Lock` re-acquisition
  (deadlocks), stale processes holding port 8443, Windows PowerShell `;`
  vs `&&`.

## Phase 4 — Generate release assets
- `python create_apk.py` → `SpyTV.apk`.
- Generate `SHA256SUMS.txt` covering every asset to be attached.

## Phase 5 — Push everything
- Commit all Phase 1–4 work with a clear message and `git push`.

## Phase 6 — Merge with main
- Ensure a `main` branch exists containing all current work (create it from
  the working branch if missing).
- If the merge is blocked by conflicts/issues, fix them, re-run Phase 2
  tests, then complete the merge.
- Make `main` the default branch on GitHub (`gh repo edit --default-branch
  main`), then delete superseded branches (local and remote, e.g. `master`).

## Phase 7 — Tag & release
- Create the next tag (default: bump patch from the latest release) on `main`
  and push it.
- `gh release create <tag>` titled `SpyTV <tag> — TV Security Assessment
  Platform`, with release notes summarizing the changes, and attach **all**
  generated assets from Phase 4.
- Verify with `gh release view <tag>`.

Finish with a concise phase-by-phase report (tests run, fixes made, assets,
branches merged/deleted, tag + release URL).
