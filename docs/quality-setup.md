# Quality setup

Inspection date: 2026-11-04. Repository root: `/Users/george/vibecoding/android`.

`.cursor/`, `scripts/`, `.vscode/`, `.editorconfig`, and `AGENTS.md` are kept on the maintainer machine and omitted from the public history. The published security job does not depend on `scripts/`.

## 1. Repository findings

The project root contained only an empty `buselpuertolibre/` directory. There is no Android application, Gradle wrapper, settings file, version catalog, convention plugin, Kotlin source, test, Room schema, or `app/src/main/assets/transit.db`.

Absent, so nothing was preserved or duplicated:

- `AGENTS.md` and `.cursor/rules/` (both added by this setup)
- Git history inside this directory
- Lint, detekt, Spotless, CI workflows, and Git hooks
- A selected AGP, Kotlin, KSP, Gradle, or Android SDK version

`git rev-parse --show-toplevel` from this directory resolved to an ancestor repository, not to this project. That ancestor is unrelated. This setup initialized a repository here with `git init -b main` and did not create a commit. Do not commit from the ancestor.

Tool inspection on this machine:

| Item | Result |
|---|---|
| `java` on `PATH` | macOS stub. "Unable to locate a Java Runtime." |
| Temurin 21 | `~/Library/Java/JavaVirtualMachines/temurin-21.jdk`, OpenJDK 21.0.13+9. This is the JDK on `PATH` via `JAVA_HOME`. |
| Temurin 27 | `/Library/Java/JavaVirtualMachines/temurin-27.jdk` remains installed and is not on `PATH`. |
| Android SDK | `ANDROID_HOME` and `ANDROID_SDK_ROOT` are `~/Library/Android/sdk`. cmdline-tools 23.0, platform `android-37.2`, build-tools `37.0.0`, platform-tools `37.0.1` (`adb` 1.0.41). No emulator. |
| `pre-commit`, `gitleaks`, `actionlint`, `osv-scanner` | Not installed on `PATH` before this setup. |
| Python | `python3` is CPython 3.12.7 via rye. |
| GitHub CLI | Not installed. No workflow run was queried. |

## 2. Selected tool versions

Versions below are the ones this repository executes. Android compiler versions are deliberately not selected; there is no build to stay compatible with, and guessing an AGP/Kotlin/Gradle triple would constrain a project that does not exist.

| Tool | Pin | Why this one |
|---|---|---|
| pre-commit | 4.7.0 | Current PyPI release on the inspection date. Installed into `.venv`. |
| pre-commit-hooks | `3e8a8703264a2f4a69428a0aa4dcb512790b2c8c` (tag v6.0.0) | Commit returned by the GitHub commits API for that tag. |
| gitleaks | 8.30.1, commit `83d9cd684c87d95d656c1458ef04895a7f1cbd8e` | Latest release on the inspection date. Local and CI run the release binary after SHA-256 verification, not `go install`. |
| actionlint | 1.7.12, commit `914e7df21a07ef503a81201c76d2b11c789d3fca` | Latest release on the inspection date. Same binary-pin approach. |
| actions/checkout | `3d3c42e5aac5ba805825da76410c181273ba90b1` (v7.0.1) | Commit for that tag. `contents: read` and `persist-credentials: false`. |
| actions/setup-python | `6d4fd22b20465ccc0f7354476588e8df7c9ca0e0` (v7.1.0) | Commit for that tag. CI Python is 3.12. |
| Spotless / ktlint | Not selected | No Kotlin sources. |
| detekt | Not selected | No Kotlin sources. Type-resolved detekt is neither configured nor described as active. |
| Android Lint | Not selected | No Android module. |
| Kotlin warnings-as-errors | Not selected | No Kotlin compilation. |
| Gradle dependency lock and verification | Not selected | No configurations to lock. A version catalog would not replace lockfiles. |
| OSV-Scanner | Not selected | No Gradle lockfile to scan. A scheduled workflow is not published. |
| Gradle License Report | Not selected | No release runtime classpath. |
| JDK for Gradle | Temurin 21.0.13+9 on this machine | The Gradle project does not exist yet, so no toolchain is pinned in a build file. Temurin 27 is not on `PATH`. |
| Android SDK | cmdline-tools 23.0, platform 37.2, build-tools 37.0.0, platform-tools 37.0.1 | Installed under `~/Library/Android/sdk`. These versions are the current stable packages from the SDK repository. The future Gradle project can target an older API without removing them. |

Checksums live next to the download URLs in `scripts/run_repository_security.py`. They were copied from each project's published checksum file. A mismatch stops extraction.

Gitleaks trust boundary: the default rules inside the pinned 8.30.1 binary are extended, not replaced. The added allowlist paths are `.tools/` and `.venv`. Upstream default path skips still drop images, fonts, Gradle wrapper scripts, and `verification-metadata.xml` from the scan. That is an upstream limitation to remember when those files appear. No finding has been waived.

## 3. Planned checks and module coverage

Module coverage today: none. `buselpuertolibre` has no variants, no `src/`, and no tests.

| Gate | State | Command |
|---|---|---|
| Merge-conflict markers | Implemented | `python3 scripts/run_repository_security.py` |
| YAML and TOML validation | Implemented | same |
| Large files, 5 MiB | Implemented, with `--enforce-all`. No asset exception. | same |
| Secret scan | Implemented. Whole working tree, `--no-git`, redacted logs. | same |
| Workflow lint | Implemented | same |
| `qualityFormatCheck` | Blocked | Future `./gradlew qualityFormatCheck` |
| `qualityStaticCheck` | Blocked | Future detekt plus variant Android Lint. Warnings as errors. No blanket baseline. No `checkOnly` list. |
| `qualityUnitTest` | Blocked | Future host tests for every relevant module, with an empty-suite guard for required suites only. |
| `qualityBuild` | Blocked | Future debug build. |
| `qualityCheck` | Blocked | Future aggregate of the four tasks. No emulator. |
| `qualityAndroidTest` | Blocked | No instrumented tests. Not implemented as a passing no-op. |
| RapTOR, GTFS, Room asset tests | Blocked | The production code and `transit.db` are absent. Not fabricated. |
| Dependency lock, verification, wrapper checksum | Blocked | No wrapper. |
| OSV-Scanner, including schedule | Blocked | Would have nothing real to scan. |
| License policy | Blocked | No runtime configuration. Bundled transit data, tiles, and fonts need a separate rights review when they exist. |
| Coverage percentage | Not used | Optional reporting only, even after tests exist. |

CI job that exists:

- `repository-security`

CI jobs that must not be required until they exist, because a missing required check stays pending:

- `quality (format)`
- `quality (analysis)`
- `quality (tests-build)`
- `android-integration`

The workflow triggers are `pull_request`, `push` to `main`, and `workflow_dispatch`. It does not use `pull_request_target`, path filters, or a self-hosted runner. Token permission is `contents: read`. Concurrency cancels superseded runs. Timeout is 20 minutes. There is no Gradle cache because there is no Gradle build. There are no release secrets.

`main` is the intended default branch. If the GitHub default branch differs, update the push trigger before relying on it.

## 4. Environment limitations

- No emulator image or device. Instrumented Room tests cannot run.
- Temurin 21 and the Android SDK are installed. Gradle still cannot run because there is no wrapper or project.
- No project, so formatting, detekt, lint, unit tests, license, lock, and vulnerability gates were not executed. They are not configured either.
- GitHub-hosted CI has not run. Nothing here claims that `repository-security` is green on GitHub.
- Hook configuration is in-repo. `pre-commit install` runs only when `scripts/run_repository_security.py --install-hooks` is used.
- The first security run needs network access to GitHub and PyPI. The application runtime is still required to be offline; that requirement has no app to enforce yet.
- Gitleaks default rules skip some binary and wrapper paths. See section 2.
- actionlint runs without shellcheck, so shell-script rules that need shellcheck are not active.

## 5. Implementation and verification checklist

- [x] Inspect the tree, Git discovery, and local tools.
- [x] Add `.cursor/rules/quality-gates.mdc` and a pointer in `AGENTS.md`.
- [x] Add `.gitignore`, `.gitattributes`, and `.editorconfig` without a Spotless integration.
- [x] Add pinned pre-commit, Gitleaks, and actionlint checks.
- [x] Add `.github/workflows/repository-security.yml`.
- [x] Document JDK/SDK, hooks, and the future `./gradlew qualityCheck` command.
- [x] Run the repository-security command and the negative controls that this tree can support.
- [ ] Spotless, detekt, Android Lint, Kotlin warnings-as-errors. Blocked.
- [ ] Aggregate Gradle tasks and empty-suite detection. Blocked.
- [ ] RapTOR, GTFS, and Room asset tests. Blocked.
- [ ] Dependency locking, Gradle verification, OSV-Scanner, license report. Blocked.
- [ ] `android-integration` workflow. Blocked.
- [ ] GitHub branch protection. Documented below. Not applied.

### Verification record

Local run on 2026-11-04, after the empty-scan fix. Labels: configured, executed successfully, executed and failed, not executed.

| Check | Status |
|---|---|
| `python3 scripts/run_repository_security.py` | Executed successfully on the final tree. pre-commit 4.7.0, gitleaks 8.30.1, actionlint 1.7.12. All six hooks passed. |
| First `--all-files` attempt | Executed and failed open: with no commit, every hook reported "no files to check" and the process exited 0. The runner no longer uses `--all-files`. An empty file list is now a hard failure. |
| Formatting violation | Not executed. No formatter is configured. |
| detekt or Android Lint violation | Not executed. No static-analysis toolchain. |
| Failing unit assertion | Not executed. No test suite. Test count for required suites: none exist. |
| Synthetic Gitleaks fixture | Executed and failed, as required. A public AWS example key was not detected. A synthetic PEM header that is not a real key was detected (`leaks found: 1`), then the file was deleted. |
| Invalid YAML | Executed and failed. |
| Conflict markers | Executed and failed only after `--assume-in-merge`. Without that argument the hook exited 0. |
| 6 MiB file | Executed and failed only after `--enforce-all` (`6144 KB` exceeds `5120 KB`). Without that argument the hook exited 0 because the file was not a staged addition. |
| Invalid workflow key | Executed and failed: actionlint `syntax-check` on `not_a_real_key`. |
| Empty scan | Executed and failed: `repository-security found no files to scan and will not pass an empty run.` |
| Unexpectedly empty Gradle test suite | Not executed. No required test task exists. |
| Git hook installation | Not executed. `.git/hooks/pre-commit` is absent. |
| GitHub Actions run | Not executed. No host run has been observed. |

The restored tree was scanned again after the fixtures were removed. No `negative-control*` file remains. No commit was created.

## GitHub settings the maintainer must apply

These settings were not changed. They are not active unless the maintainer confirms them in GitHub.

- Merge through pull requests. Allow the maintainer's chosen merge style. Direct pushes to the default branch should be disabled by the protection below.
- Required status check, exact name: `repository-security`.
- Do not require `quality (format)`, `quality (analysis)`, `quality (tests-build)`, or `android-integration` until those jobs exist.
- Require the branch to be up to date before merging.
- Disable force-push and branch deletion on the default branch.
- Do not use administrative bypass as a routine path. Include administrators in the restrictions.
- Do not require a second approving review while there is a single maintainer.
- Private-repository enforcement depends on the plan. GitHub Free enforces branch protection and rulesets on public repositories; private repositories generally need GitHub Pro, Team, or Enterprise before those rules are enforced. Confirm the plan in the repository settings. A public repository is not required by this setup.
- No paid scanner, Google API, or self-hosted runner is required for the check that exists.

## Maintainer decisions still open

- Initialize review and push policy on GitHub (above). This setup did not.
- Choose the AGP, Kotlin, KSP, Gradle, and JDK combination with the first real build, then add the blocked gates on that combination.
- Approve any future large-file exception per asset, not per directory.
- Approve any future vulnerability or license exception individually.
- Review the first `verification-metadata.xml` by hand when dependency verification is introduced.
- Confirm license and redistribution rights for GTFS data, map tiles, and fonts separately from Gradle license metadata.
