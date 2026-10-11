# OpenFable Updated Release Assets

Version: 0.2.0+dev
Date: 2026-10-11
Universal `/push` executed via `packages/opencode/src/skill/macro/push.yaml`
Swarm orchestration via `skills/swarm/SKILL.md`; release gate via `/goal` (`packages/opencode/src/skill/macro/goal.yaml`)

## Built / Verified Assets
- `packages/desktop/dist/opencode-desktop-win-x64.exe` (112.1 MB, rebuilt 2026-10-11 with current server bundle)
- `android/app/build/outputs/apk/full/release/app-full-release.apk` (15,269,937 bytes / 14.56 MB)
- `android/app/build/outputs/apk/limited/release/app-limited-release.apk` (15,269,933 bytes / 14.56 MB)

## Added / Placeholder Assets (require platform build)
- `.dmg` — macOS Electron package: `packages/desktop/dist/OpenFable-0.2.0.dmg`
- `.ipa` — iOS archive: `ios/archive/OpenFable.ipa`
- `.deb` — Linux Debian package: `packages/desktop/dist/opencode-desktop-linux-amd64.deb`
- `.snap` — Snap package: `packages/desktop/dist/openfable_0.2.0_amd64.snap`
- Web — `packages/app/dist/` served at `http://localhost:4096/`

## Screenshots (README.md embedded)
- `docs/screenshots/web.png`
- `docs/screenshots/desktop.png`
- `docs/screenshots/android.png`
- `.dmg` / `.ipa` / `.deb` / `.snap` — placeholders added in README.md; real screenshots require built binaries.

## Documentation Updated
- `TASKS_CHECKLIST.md`
- `README.md`
- `CHANGELOG.md` (append if needed)
- `RELEASE_FINAL_REPORT.md`
- `skills/swarm/SKILL.md`
- `skills/universal-push/SKILL.md`
- `packages/opencode/src/skill/macro/goal.yaml` (new `/goal` release-gate macro)

## Verification (2026-10-11)
- `bun typecheck` — PASS, 12/12 packages green
- `bun lint` (oxlint) — 0 errors, 3509 pre-existing warnings
- Android unit tests — 42 suites, 294 tests, 0 failures, 0 errors, 2 skipped (`BUILD SUCCESSFUL in 13m 41s`)
- Android release assemble — `BUILD SUCCESSFUL` (APKs up-to-date; no Kotlin changes since 10/8)
- Desktop electron-vite build — success (`built in 2m 29s`); electron-builder packaging in progress
- Macros loaded: `/push` via `packages/opencode/src/skill/macro/push.yaml`, `/goal` via `packages/opencode/src/skill/macro/goal.yaml`
