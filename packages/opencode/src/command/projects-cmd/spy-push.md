---
name: spy-push
description: Full release pipeline — repo cleanup, vulnerability sweep, error-correct, test, debug, build assets, push, merge, tag + release
---

Run the complete release pipeline for this repository at.
Work through every phase in order, fix anything that fails, and report the result
of each phase at the end. Optional argument: $ARGUMENTS (a target tag like
`v1.2.0`; if empty, use the next semantic version after the latest GitHub
release).

## Phase 1 — Error-correct & refactor
- `git status` / `git diff` review of all pending and recent changes.
- Fix obvious errors: broken imports, dead code, deprecated API usage,
  duplicated logic, inconsistent naming (Current branding only — never any legacy pre-rename names).
- Keep refactors behavior-preserving; do not redesign features.

## Phase 2 — Repo cleanup
- Untrack and remove junk that must not be in the repo: agent/tool state
  (`.mimosa/`, `.v2c/`, `.video_agent/`, `.zcode/`, `.zcodeignore`),
  `__pycache__`, build outputs (`android/**/build`, `electron/dist`,
  `electron/node_modules`), stale logs — `git rm -r --cached` where tracked,
  delete from disk where untracked and stale.
- Extend `.gitignore` so every removed pattern stays out (verify by re-running
  `git status --ignored` afterwards — nothing new should appear).
- Confirm no secrets are tracked (`git ls-files` vs `*.key`, `*.pem`, `.env`,
  `ai_keys.json`); ignored-only is the rule.
- Delete dead/duplicate files ONLY after grepping that nothing references them.

## Phase 3 — Regenerate file_structure.txt
- Rebuild `file_structure.txt` from `git ls-files` as an indented tree with
  one-line comments on root folders. It must reflect the tree AFTER Phase 2.

## Phase 4 — Vulnerability sweep
- Python: `python -m pip_audit -r requirements.txt` (install `pip-audit` if
  missing). Upgrade vulnerable pins to the minimum fixed versions; re-run Phase 5.
- Node: `npm audit` inside `electron/` — resolve with `npm audit fix`,
  dependency bumps, and `overrides` for transitive packages (e.g. `tar`);
  never downgrade or ignore. Smoke `npx electron --version` after changes.
- GitHub: `gh api --paginate repos/ProjectZeroDays/[REPO_NAME]/dependabot/alerts` —
  the open count must drop; any alert that remains needs an explicit
  "accepted" rationale in the report (e.g. dev-only package with no patched
  release published).
- Grep the tree for hardcoded credentials, embedded tokens, and dead URLs.
- Record fixed-vs-accepted in the final phase report.

## Phase 5 — Test all code
- `python -m pytest -q` — every test must pass.
- `python -m compileall -q` (or `py_compile` over all `.py` files).
- Syntax-check dashboard/JS assets (`node --check` on extracted script blocks`).
- Parse all YAML configs; verify `.env.example`/`.gitignore` consistency.

## Phase 6 — Debug
- Any failure from Phases 1–5 must be fixed and re-run until green.
- Watch for known traps: non-reentrant `threading.Lock` re-acquisition
  (deadlocks), stale processes, Windows PowerShell `;`
  vs `&&`, and concurrent Gradle builds racing on `android/app/build`
  (check for other Gradle processes before a build; wipe `app/build` after
  an interrupted run).

## Phase 7 — Generate release assets
- `python create_apk.py` — builds the REAL Android APK from the Gradle
  project (`android/`, `assembleDebug`) and verifies it before writing
  `app_name.apk`: zip integrity, `apksigner verify`, `aapt dump badging`
  (package + launchable activity), sane size (>1 MB). It must exit nonzero
  and never emit a placeholder artifact on any failure.
- Generate `SHA256SUMS.txt` covering every asset to be attached.

## Phase 8 — Push everything
- Commit all Phase 1–7 work with a clear message and `git push`.

## Phase 9 — Merge with main
- Ensure a `main` branch exists containing all current work (create it from
  the working branch if missing).
- If the merge is blocked by conflicts/issues, fix them, re-run Phase 5
  tests, then complete the merge.
- Make `main` the default branch on GitHub (`gh repo edit --default-branch
  main`), then delete superseded branches (local and remote, e.g. `master`).

## Phase 10 — Tag & release
- Create the next tag (default: bump patch from the latest release) on `main`
  and push it.
- `gh release create <tag>` titled `Repo-Name <tag> — TV Security Assessment
  Platform`, with release notes summarizing the changes, and attach **all**
  generated assets from Phase 7.
- Verify with `gh release view <tag>`.

Finish with a concise phase-by-phase report (cleanup removed, vulnerabilities
fixed/accepted, tests run, fixes made, assets, branches merged/deleted,
tag + release URL).
