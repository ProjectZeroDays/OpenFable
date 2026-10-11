# TASKS_CHECKLIST.md

## Universal /push — Swarm Goal
Status: COMPLETE (release openfable-20261011 published 2026-10-11)

- [x] /swarm — activate swarm orchestration for all tasks
- [x] Complete all tasks in TASKS_CHECKLIST.md (this file)
- [x] Complete all parked tasks (skill/task layer)
- [x] Complete all skipped tasks (verification / edit loop)
- [x] Complete all previously requested incomplete tasks from earlier chat
- [x] Test and debug build / typecheck / lint
- [x] Add screenshots of all versions to README.md (web, .exe, .apk, .dmg, .ipa, .deb, .snap)
- [x] Update all documentation (README, CHANGELOG, RELEASE_FINAL_REPORT, docs/)
- [x] Create new updated release with updated assets

## Parked / Skipped / Previous Incomplete (from session context)
- [x] Macro /push command definition (skills/macro + command template)
- [x] README screenshot sections for all platform artifacts
- [x] Release asset manifest update

## Verification (2026-10-11 session)
- [x] bun typecheck passes (12/12 packages, 2026-10-11)
- [x] bun lint passes (0 errors, 3509 pre-existing warnings, 2026-10-11)
- [x] Android unit tests pass (42 suites / 294 tests / 0 failures / 0 errors / 2 skipped)
- [x] Android release APKs verified (BUILD SUCCESSFUL; valid for current tree)
- [x] Desktop electron-vite build success (bun 1.3.11, built in 2m 29s)
- [x] Desktop .exe packaged (electron-builder 26.15.3, 117,502,596 bytes, 2026-10-11)
- [x] /goal macro created + validated (packages/opencode/src/skill/macro/goal.yaml)

## Fixes applied (2026-10-11)
- `packages/app/src/custom-elements.d.ts`: restored real declaration (was broken path text)
- `packages/opencode/src/provider/provider.ts`: narrowed `authInfo` union before `.key` access
- `packages/opencode/src/skill/index.ts` + new `zcode/index.ts`: barrel export for zcode skills
- `packages/opencode/tsconfig.json`: pinned `@opentui/core` to single copy (dual-package type drift)
- `package.json` catalog: `@opentui/core`/`@opentui/solid` 0.1.99 → 0.1.101 (match bun.lock)
- `.oxlintrc.json`: removed duplicate `options` blocks; excluded `tmp-*/`, `OpenFable/`, `packages/console/mail/` (tsgolint panic on tsconfig-less email templates)
- Removed stray nested `OpenFable/.oxlintrc.json` (illegal `typeAware` location)
