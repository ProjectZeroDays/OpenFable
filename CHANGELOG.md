# Changelog

## 0.2.0-dev (2026-10-11)
- Integrated 24 Cyber Security Agents (`src/agent/cyber-security/`) with per-agent skills (`src/skill/cyber-agents/`) and `/commands` (`src/command/cyber-agents-cmd/`)
- Integrated agent-toolkit skills (`src/skill/toolkit/`, deduped 22 already-present)
- Integrated 254 missing openclaw plugin-skills (`src/skill/openclaw/`, `vscode_*` extensions excluded)
- Integrated C:\Projects sweep: 179 OmniRoute+Ruflo skills, 26 project agents, `/spy-push` + `/quantum-swarm` commands
- `.zcode/skills` verified fully covered already (290/290 present, delta empty)
- New `/goal` release-gate macro (`src/skill/macro/goal.yaml`)

## 0.2.0-dev (2026-10-08)
- Universal `/push` macro added (`packages/opencode/src/skill/macro/push.yaml`)
- `/swarm` integration updated (`skills/swarm/SKILL.md`)
- All version screenshots referenced in README.md (web, .exe, .apk, .dmg, .ipa, .deb, .snap)
- TASKS_CHECKLIST.md created; all parked/skipped/previous tasks tracked
- Documentation updated: README.md, RELEASE_FINAL_REPORT.md, RELEASE_ASSETS.md, skills/universal-push/
- Release assets updated; new binary artifacts required for .dmg/.ipa/.deb/.snap

## 0.2.0 (2026-06-19)

### Added
- Mythos reasoning wrapper: Recurrent-Depth Transformer pattern with 4-pass iterative reasoning
- Claude Code tool system integration: enhanced grep (output modes, context, pagination), edit (quote normalization, uniqueness hints), bash (security patterns, git safety)
- Unified Mythos-Claude operational directive (~600 tokens vs ~2000 for old abliteration chain)
- Bubble spinner animation (16-frame rising-and-popping cycle)
- Command palette with 18 OpenFable-specific commands (/mythos, /memory, /doctor, /test, /build, etc.)
- Free model support via opencode provider (openfable-v2-pro-free, gpt-5-nano, etc.)
- Configurable API URLs via OPENFABLE_API_URL and OPENFABLE_PLATFORM_URL env vars
- Claude Code architecture docs (architecture, tools, commands, subsystems, bridge, exploration-guide)

### Changed
- Rebranded from MiMoCode to OpenFable
- Replaced UFO spinner with bubble animation
- Enhanced diff view with higher contrast and accent colors
- Theme colors: distinct status colors (cyan info, amber warning, green success)
- All LLM calls wrapped with Mythos-Claude operational directive
- Tool reinforcement injected on every user message

### Fixed
- Removed all remaining "opencode" branding references
- ExternalSource type: cc/codex/openfable
- Plugin version checks use openfableVersion
- User-Agent uses openfable

### Removed
- Old abliteration chain (decompression + DAN + CL4R1T4S + authority chain)
- Empty files (npm/config.ts, prompt/cwd.ts)
- MiMoCode data directory (migrated to openfable)

## 0.1.1 (2026-06-17)

- Initial OpenFable release
- Fork of MiMoCode with rebrand
