---
name: complete-the-project
description: |
  Autonomous project completion skill. Runs when a project reaches 90%+ completion.
  Performs self-healing, self-updating, self-scanning, self-debugging loops until
  all components are satisfied. Runs itself at 90%, 95%, and 100% milestones.
  Compatible with: mimicode, jcode, zcode, opencode, hermes, openclaw.
trigger: "complete-the-project", "[CTP=ON]", "finish the project", "make this complete"
---

# Complete The Project (CTP) — Autonomous Self-Healing Completion Skill

## Purpose
When loaded, this skill triggers an autonomous completion loop that iteratively
brings a project to 100% readiness. It runs at three milestones (90%, 95%, 100%),
each time performing self-healing, self-scanning, self-debugging, and self-updating
until no more improvements are recommended.

## Platforms
This skill is portable across: mimicode, jcode, zcode, opencode, hermes, openclaw.
Install into any platform's skill directory; the logic is platform-agnostic.

---

## Loop Protocol

### Phase 0 — Pre-flight
1. Read `tasks.md` or equivalent task ledger
2. Read `MEMORY.md` for project state
3. Compute completion percentage from checked vs total items
4. If < 90%: report gap, do NOT auto-loop (wait for human direction)
5. If >= 90%: enter autonomous completion loop

### Phase 1 — Self-Scan (run at every milestone)
- Scan all `.py` files for:
  - `"status": "simulated"` or `"status": "mock"` return values
  - Hardcoded fake credentials (`password123`, `admin123`, etc.)
  - Broken import paths (`parent.parent.parent / "agents"`)
  - Missing `__init__.py` in packages
  - Security leaks (`str(e)` in API responses, unescaped user input)
  - CodeQL/Bandit alerts
- Generate `scan_report.json` with findings

### Phase 2 — Self-Heal (fix all scan findings)
- Apply fixes using Edit tool (NOT subagent parallel edits — they don't persist)
- For each fixed file, re-run relevant tests
- Log fixes to `.learnings/HEALS.md` with `Pattern-Key` and `Recurrence-Count`
- If tests fail after fix: diagnose root cause, fix again, verify

### Phase 3 — Self-Debug (run test suites)
- Run: `pytest agents/specialized/ -v --tb=short`
- Run: `python -m py_compile` on all agent modules
- Fix any import errors, syntax errors, or test failures
- If a test framework is missing: install it, then re-run

### Phase 4 — Self-Update (if new dependencies needed)
- Check `requirements.txt` vs `import` statements
- Add missing dependencies
- Run `pip install -r requirements.txt` in project venv

### Phase 5 — Commit & Push
- Stage all changes: `git add agents/specialized/ .mimocode/`
- Commit with descriptive message
- Push to remote (retry up to 3x on timeout)
- Delete merged branches: `git branch --merged main | grep -v main | xargs git branch -d`

### Phase 6 — Report
- Output: completion %, issues found, issues fixed, tests passing
- If still < 100% but no more fixes possible: mark as `blocker_needed`
- If 100% and no blockers: mark as `COMPLETE`

---

## Autonomy Rules
- **Do NOT** ask the user for permission at each step — the skill IS the permission
- **Do NOT** commit secrets or API keys
- **DO** verify every fix before moving on (re-run relevant tests)
- **DO** stop the loop if a fix introduces a new failure that can't be resolved in 3 attempts
- **DO** record every action in `.learnings/CTP-log.md`

## Exit Conditions
Loop exits when ANY of these is true:
1. All tests pass AND no scan findings remain → `COMPLETE`
2. 3 consecutive iterations produce zero changes → `STALLED — manual review needed`
3. A blocker is found that requires human input → `BLOCKED`

---

## File Structure After Installation
```
<repo>/
├── agents/
│   └── specialized/
│       ├── __init__.py
│       ├── vulnscanneragent.py
│       ├── windowsexploitagent.py
│       ├── bruteforceagent.py
│       ├── apisnifferagent.py
│       ├── exploitationagent.py
│       ├── cookieharvesteragent.py
│       ├── payloadengineagent.py
│       ├── deserializationagent.py
│       ├── memorycorruptionagent.py
│       ├── messagingrceagent.py
│       ├── fileparseexploitagent.py
│       ├── mediaexploitagent.py
│       ├── androidexploitagent.py
│       ├── iosexploitagent.py
│       ├── linuxexploitagent.py
│       ├── osxexploitagent.py
│       ├── iotexploitagent.py
│       ├── nfcexploitagent.py
│       ├── bluetoothexploitagent.py
│       ├── automobileexploitagent.py
│       ├── securityscanneragent.py
│       ├── chained_zero_day.py
│       └── memory_primitives.py
├── .mimocode/
│   └── skills/
│       └── complete-the-project/
│           └── SKILL.md
└── tasks.md
```
