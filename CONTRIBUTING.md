# Contributing

This repository is the home of El Puerto Bus. `buselpuertolibre/` is the Gradle project. Commands below are split into checks that exist today and checks that stay blocked.

`.cursor/`, `scripts/`, `.vscode/`, `.editorconfig`, and `AGENTS.md` are gitignored. They are not in the public repository. A clone does not receive `scripts/run_repository_security.py`.

**Public CI and fresh clones** run `.pre-commit-config.yaml` (including `.github/check_publish_boundary.py`) from `.github/workflows/repository-security.yml`. **Maintainers** use gitignored `scripts/run_repository_security.py` to install pinned tools, run the same hooks on the full working tree, and register a custom pre-push boundary hook.

Do not commit, push, or change GitHub settings as part of ordinary development unless the maintainer asked for that.

## Requirements

| Requirement | Today |
|---|---|
| Git | Repository root must be this project. Do not commit from an ancestor directory. |
| Python | 3.12 for repository-security checks. Android compilation does not use Python. |
| JDK | JDK 21 for Gradle. Do not write install paths into tracked files. |
| Android SDK | Required to compile the app. Do not write SDK paths into tracked files. |
| Network | First repository-security run downloads pinned tools and the pre-commit hook environment. Later runs reuse `.tools/`, `.venv`, and the pre-commit cache. |

Machine-specific JDK and SDK inventory belongs in gitignored `local/maintainer/quality-host.md` on the maintainer disk, not in this file.

## First-time setup

From the repository root (maintainer machine only, after clone):

```bash
python3 scripts/run_repository_security.py --install-hooks
```

Windows PowerShell:

```powershell
py -3 scripts/run_repository_security.py --install-hooks
```

`--install-hooks` registers:

- a **pre-commit** hook via `pre-commit install --hook-type pre-commit --install-hooks`
- a **pre-push** hook that runs `scripts/check_publish_boundary.py --pre-push`

Do **not** run `pre-commit install --hook-type pre-push`; it would overwrite the pre-push hook. pre-commit’s pre-push hook does not receive ref pairs the boundary check needs.

Copying `.pre-commit-config.yaml` into the tree does not install hooks.

## Checks that run today

**On GitHub and any clone** (no `scripts/` required):

```bash
pre-commit run --all-files --show-diff-on-failure
```

CI also puts pinned gitleaks and actionlint on `PATH` before that command. See `.github/workflows/repository-security.yml`.

**On the maintainer machine** (full tree, tool install, boundary re-check):

```bash
python3 scripts/run_repository_security.py
```

Windows:

```powershell
py -3 scripts/run_repository_security.py
```

The maintainer runner installs, when missing:

- pre-commit 4.7.0 into `.venv`
- gitleaks 8.30.1 into `.tools/bin`, after verifying the published SHA-256
- actionlint 1.7.12 into `.tools/bin`, after verifying the published SHA-256

It runs pre-commit on every tracked and untracked file that Git does not ignore, then runs `.github/check_publish_boundary.py`. An empty file list fails the script. The installed pre-commit hook still receives only staged files for that commit; use the maintainer runner before push for a full-tree scan.

There is no formatter to apply yet. When Spotless is added with the Android project, local formatting will be `./gradlew spotlessApply` and CI will run `spotlessCheck` without rewriting the checkout.

## Checks blocked on the Android project

When `buselpuertolibre` contains the real Gradle build, the normal pre-push command becomes:

```bash
./gradlew qualityCheck
```

Windows:

```powershell
.\gradlew.bat qualityCheck
```

That aggregate task is not implemented. The wrapper is in `buselpuertolibre/`. Local checks that exist today are `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` from that directory. The intended split is:

| Task | Role |
|---|---|
| `qualityFormatCheck` | Spotless `spotlessCheck` |
| `qualityStaticCheck` | detekt and Android Lint |
| `qualityUnitTest` | Host-side unit tests for every module that has them |
| `qualityBuild` | Debug build |
| `qualityCheck` | The four tasks above. No emulator. |
| `qualityAndroidTest` | Connected tests, only when real instrumented tests exist |

Do not add these tasks as empty successes.

Emulator images are not part of CI. `transit.db` and the municipal PMTiles stay on the maintainer machine and are copied into the app at build time. RapTOR is not in this tree.

## Security, dependencies, and licenses

Current secret and publish-boundary scans:

- **CI / clone:** pre-commit (`gitleaks`, actionlint, publish-boundary, merge-conflict, YAML/TOML, large files)
- **Maintainer:** `python3 scripts/run_repository_security.py` (same hooks plus explicit boundary pass)

Gitleaks reads `.gitleaks.toml`, which extends the default rules shipped in the pinned binary. The local allowlist paths are `.tools/` and `.venv`. Default upstream path skips still apply, including images, fonts, the Gradle wrapper, and `verification-metadata.xml`. Those skips are a scanner limitation, not an approval to hide secrets in those files.

There is no dependency inventory yet, so these commands are not available:

- Gradle dependency lock and `gradle/verification-metadata.xml`
- OSV-Scanner against Gradle lockfiles
- Gradle License Report, or an allowed-license policy

When they are added, dependency metadata still does not establish rights to transit data, map tiles, fonts, or other bundled content.

Vulnerability exceptions, once a scanner is wired in, must name the advisory, dependency, reason, owner, and expiry, and the scanner must actually read that file. Do not add an exceptions file that nothing executes.

Initial dependency-verification metadata needs human review. Do not bulk-approve checksums.

## Reports

| Check | Output |
|---|---|
| pre-commit | Terminal output. No report file. |
| gitleaks | Terminal output, secrets redacted. |
| actionlint | Terminal output. |
| publish-boundary | Terminal output. |
| Future Gradle reports | To be documented with the tasks that create them. HTML/XML reports must not be committed. |

## Updating pinned tools

1. Read the upstream release notes and checksum file.
2. Update the version, URL, and SHA-256 in gitignored `scripts/run_repository_security.py` (all platforms).
3. Update Linux x64 checksums in `.github/workflows/repository-security.yml` for CI.
4. Update the hook `rev` in `.pre-commit-config.yaml` when the hook repository changes, using the commit the release tag points at.
5. Update third-party Actions in `.github/workflows/repository-security.yml` to the full commit SHA of the reviewed release.
6. Re-run `python3 scripts/run_repository_security.py` on the maintainer machine.
7. Record the review in the pull request. Do not copy an example SHA.

## Network

| Action | Network |
|---|---|
| `pre-commit run --all-files` on a warm cache | Usually not required |
| First maintainer security run, or a version bump | GitHub release downloads and PyPI |
| Future `./gradlew qualityCheck` | Gradle distributions, Maven dependencies, and wrapper validation need network unless the cache is already populated |
| Future OSV-Scanner | Network, for the vulnerability database, including on a schedule |
| Application runtime | Must stay offline. Build-time downloads do not change that |

## GitHub Actions

The only workflow is `repository-security`. It runs on pull requests, pushes to `main`, and manual dispatch. It does not run on a schedule. A scheduled dependency scan waits for lockfiles.

Required-check settings are documented in `docs/quality-setup.md`. They are not enabled from this repository.
