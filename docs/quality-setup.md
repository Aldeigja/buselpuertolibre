# Quality setup

Inspection date: 2026-11-04. Paths below are relative to the repository root unless noted.

`.cursor/`, `scripts/`, `.vscode/`, `.editorconfig`, and `AGENTS.md` are kept on the maintainer machine and omitted from the public history. The published security job does not depend on `scripts/`. Machine-specific JDK, SDK, and path inventory lives in gitignored `local/maintainer/quality-host.md`.

## 1. Repository findings

`buselpuertolibre/` contains the Gradle project: one app module, a wrapper, Kotlin, Compose, Room, and host unit tests. `transit.db` and the municipal PMTiles are gitignored and copied in at build time. They are not in the public tree.

Absent, so nothing was preserved or duplicated from a prior tree:

- Git history inside an unrelated ancestor directory
- Lint, detekt, Spotless, Android CI workflows beyond repository-security, and Gradle hooks
- A selected AGP, Kotlin, KSP, Gradle, or Android SDK version in build files

## 2. Selected tool versions

Versions below are the ones this repository executes in CI. The Android compiler versions live in the Gradle project and are not run by the security workflow.

| Tool | Pin | Why this one |
|---|---|---|
| pre-commit | 4.7.0 | Current PyPI release on the inspection date. CI installs via pip; maintainers use `.venv`. |
| pre-commit-hooks | `3e8a8703264a2f4a69428a0aa4dcb512790b2c8c` (tag v6.0.0) | Commit returned by the GitHub commits API for that tag. |
| gitleaks | 8.30.1 | Latest release on the inspection date. CI and local runs use the release binary after SHA-256 verification, not `go install`. |
| actionlint | 1.7.12 | Latest release on the inspection date. Same binary-pin approach. |
| actions/checkout | `3d3c42e5aac5ba805825da76410c181273ba90b1` (v7.0.1) | Commit for that tag. `contents: read` and `persist-credentials: false`. |
| actions/setup-python | `6d4fd22b20465ccc0f7354476588e8df7c9ca0e0` (v7.1.0) | Commit for that tag. CI Python is 3.12. |
| publish-boundary | `.github/check_publish_boundary.py` | Tracked allowlist and content heuristics; runs via pre-commit on every clone. |
| Spotless / ktlint | Not selected | No Kotlin sources. |
| detekt | Not selected | No Kotlin sources. Type-resolved detekt is neither configured nor described as active. |
| Android Lint | Not selected | No Android module. |
| Kotlin warnings-as-errors | Not selected | No Kotlin compilation. |
| Gradle dependency lock and verification | Not selected | No configurations to lock. |
| OSV-Scanner | Not selected | No Gradle lockfile to scan. |
| Gradle License Report | Not selected | No release runtime classpath. |
| JDK for Gradle | JDK 21 (when the project exists) | No toolchain is pinned in a build file yet. Do not publish install paths. |
| Android SDK | Not pinned in-repo | Required only after the Gradle project exists. Do not publish install paths. |

Linux x64 checksums for CI downloads are in `.github/workflows/repository-security.yml`. All platform checksums for local runs are in gitignored `scripts/run_repository_security.py`. A mismatch stops extraction.

Gitleaks trust boundary: the default rules inside the pinned 8.30.1 binary are extended, not replaced. The allowlist skips gitignored maintainer trees, Gradle `.gradle/` and `build/` outputs, and `local.properties`. A custom rule flags absolute user profile paths in files that are actually scanned. Upstream default path skips still drop images, fonts, Gradle wrapper scripts, and `verification-metadata.xml` from the scan. No finding has been waived without review.

## 3. Planned checks and module coverage

Module coverage today: none. `buselpuertolibre` has no variants, no `src/`, and no tests.

| Gate | State | Command (maintainer) | Command (CI / clone) |
|---|---|---|---|
| Publish boundary allowlist | Implemented | `python3 scripts/run_repository_security.py` or `python3 .github/check_publish_boundary.py` | `pre-commit run --all-files` (hook `publish-boundary`) |
| Merge-conflict markers | Implemented | same maintainer runner | same pre-commit run |
| YAML and TOML validation | Implemented | same | same |
| Large files, 5 MiB | Implemented, with `--enforce-all` | same | same |
| Secret scan | Implemented. Whole working tree in maintainer run; `--no-git` in hook | same | same |
| Workflow lint | Implemented | same | same |
| `qualityFormatCheck` | Blocked | Future `./gradlew qualityFormatCheck` | |
| `qualityStaticCheck` | Blocked | Future detekt plus variant Android Lint | |
| `qualityUnitTest` | Blocked | Future host tests | |
| `qualityBuild` | Blocked | Future debug build | |
| `qualityCheck` | Blocked | Future aggregate | |
| `qualityAndroidTest` | Blocked | No instrumented tests | |
| RapTOR, GTFS, Room asset tests | Blocked | Production code absent | |
| Dependency lock, verification, wrapper checksum | Blocked | No wrapper | |
| OSV-Scanner, including schedule | Blocked | No lockfile | |
| License policy | Blocked | No runtime configuration | |

CI job that exists:

- `repository-security`

CI jobs that must not be required until they exist:

- `quality (format)`
- `quality (analysis)`
- `quality (tests-build)`
- `android-integration`

The workflow triggers are `pull_request`, `push` to `main`, and `workflow_dispatch`. It does not use `pull_request_target`, path filters, or a self-hosted runner. Token permission is `contents: read`. Concurrency cancels superseded runs. Timeout is 20 minutes. There are no release secrets.

`main` is the intended default branch. If the GitHub default branch differs, update the push trigger before relying on it.

## 4. Environment limitations

- No emulator image in the default CI image. The security workflow does not run Gradle.
- The Gradle wrapper is in `buselpuertolibre/`. Local host tests and `assembleDebug` are separate from this security job.
- Formatting, detekt, lint, license, lock, and vulnerability gates are not configured. The security workflow does not run them.
- GitHub-hosted CI status for `repository-security` depends on the next push after boundary hardening lands.
- Hook configuration is in-repo. Maintainers run `python3 scripts/run_repository_security.py --install-hooks` to register pre-commit and pre-push hooks.
- The first security run needs network access to GitHub and PyPI. The application runtime is still required to be offline once the app exists.
- Gitleaks default rules skip some binary and wrapper paths. See section 2.
- actionlint runs without shellcheck, so shell-script rules that need shellcheck are not active.

## 5. Implementation and verification checklist

- [x] Inspect the tree and local tools (inventory in `local/maintainer/quality-host.md`).
- [x] Add `.cursor/rules/quality-gates.mdc` and a pointer in gitignored `AGENTS.md`.
- [x] Add `.gitignore`, `.gitattributes`, and `.editorconfig` without a Spotless integration.
- [x] Add pinned pre-commit, Gitleaks, and actionlint checks.
- [x] Add `.github/workflows/repository-security.yml`.
- [x] Add tracked publish-boundary checker and `.cursorignore`.
- [ ] Spotless, detekt, Android Lint, Kotlin warnings-as-errors. Blocked.
- [ ] Aggregate Gradle tasks and empty-suite detection. Blocked.
- [ ] RapTOR, GTFS, and Room asset tests. Blocked.
- [ ] Dependency locking, Gradle verification, OSV-Scanner, license report. Blocked.
- [ ] `android-integration` workflow. Blocked.
- [ ] GitHub branch protection. Documented below. Not applied.

### Verification record

Local maintainer runs use `python3 scripts/run_repository_security.py`. CI uses `pre-commit run --all-files` with pinned gitleaks and actionlint. Labels: configured, executed successfully, executed and failed, not executed.

| Check | Status |
|---|---|
| publish-boundary allowlist | Configured. Maintainer and pre-commit hook. |
| pre-commit hooks (six plus boundary) | Configured. CI runs all via `--all-files`. |
| Synthetic Gitleaks fixture | Previously executed and failed as required on maintainer machine. |
| Invalid YAML / workflow / conflict / large file | Previously executed and failed as required. |
| Empty scan | Maintainer runner fails closed on empty file list. |
| Git hook installation | Optional via `--install-hooks`. |
| GitHub Actions run | Depends on merge of boundary hardening. |

## GitHub settings the maintainer must apply

These settings were not changed. They are not active unless the maintainer confirms them in GitHub.

- Merge through pull requests. Allow the maintainer's chosen merge style. Direct pushes to the default branch should be disabled by the protection below.
- Required status check, exact name: `repository-security`.
- Do not require `quality (format)`, `quality (analysis)`, `quality (tests-build)`, or `android-integration` until those jobs exist.
- Require the branch to be up to date before merging.
- Disable force-push and branch deletion on the default branch.
- Do not use administrative bypass as a routine path. Include administrators in the restrictions.
- Do not require a second approving review while there is a single maintainer.
- Private-repository enforcement depends on the plan. Confirm the plan in the repository settings.
- No paid scanner, Google API, or self-hosted runner is required for the check that exists.

## Maintainer decisions still open

- Initialize review and push policy on GitHub (above). This setup did not.
- Choose the AGP, Kotlin, KSP, Gradle, and JDK combination with the first real build, then add the blocked gates on that combination.
- Approve any future large-file exception per asset, not per directory.
- Approve any future vulnerability or license exception individually.
- Review the first `verification-metadata.xml` by hand when dependency verification is introduced.
- Confirm license and redistribution rights for GTFS data, map tiles, and fonts separately from Gradle license metadata.
- Decide whether to rewrite history to remove an old absolute path line in commit `5c9aa2b` (documented in `local/maintainer/quality-host.md`).
