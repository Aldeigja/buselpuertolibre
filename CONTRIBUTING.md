# Contributing

This repository is the home of El Puerto Bus. The application directory `buselpuertolibre/` does not contain a Gradle project yet. Commands below are split into checks that exist today and checks that stay blocked until that project exists.

`.cursor/`, `scripts/`, `.vscode/`, `.editorconfig`, and `AGENTS.md` are gitignored. They are not in the public repository. A clone does not receive `scripts/run_repository_security.py`; GitHub Actions runs the same hooks from `.github/workflows/repository-security.yml`.

Do not commit, push, or change GitHub settings as part of ordinary development unless the maintainer asked for that.

## Requirements

| Requirement | Today |
|---|---|
| Git | Initialize this directory with `git init -b main` if `.git` is missing. Do not commit from an ancestor directory. |
| Python | 3.12. Repository checks use `.venv`. Android compilation does not. |
| JDK | Temurin 21.0.13+9 for future Gradle builds. `JAVA_HOME` points at `~/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`. Temurin 27 remains installed and is not on `PATH`. The Gradle project still does not exist, so it has not selected a toolchain of its own. |
| Android SDK | `ANDROID_HOME` and `ANDROID_SDK_ROOT` are `~/Library/Android/sdk`. Installed: cmdline-tools 23.0, platform `android-37.2`, build-tools `37.0.0`, platform-tools `37.0.1`. No emulator image. |
| Network | First repository-security run downloads pinned tools and the pre-commit hook environment. Later runs reuse `.tools/`, `.venv`, and the pre-commit cache. |

## First-time setup

From the repository root:

```bash
git init -b main
python3 scripts/run_repository_security.py --install-hooks
```

Windows PowerShell:

```powershell
git init -b main
py -3 scripts/run_repository_security.py --install-hooks
```

`--install-hooks` registers the Git hook. Copying `.pre-commit-config.yaml` into the tree does not install it. The hook runs the same pre-commit checks as CI, but only after this command.

The hook is intentionally lightweight: merge-conflict markers, YAML, TOML, files larger than 5 MiB, Gitleaks, and actionlint. Conflict markers are scanned on every run (`--assume-in-merge`); the upstream hook otherwise exits 0 unless a merge is already in progress. The size check uses `--enforce-all`, so it inspects the files the runner passes in rather than only the staged diff. This is not a substitute for the Android quality gate.

## Checks that run today

```bash
python3 scripts/run_repository_security.py
```

Windows:

```powershell
py -3 scripts/run_repository_security.py
```

This is the current pre-push command. It installs, when missing:

- pre-commit 4.7.0 into `.venv`
- gitleaks 8.30.1 into `.tools/bin`, after verifying the published SHA-256
- actionlint 1.7.12 into `.tools/bin`, after verifying the published SHA-256

It then runs `pre-commit run --show-diff-on-failure --files …` on every tracked and untracked file that Git does not ignore. An empty file list fails the script. `pre-commit run --all-files` is not used: with no commit yet it sees nothing and exits 0.

CI calls the same script. The checks do not rewrite files. A failing hook fails the run. The installed Git hook still receives only the staged files for that commit, which is the pre-commit framework's own behavior.

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

That aggregate task is not implemented. Running `./gradlew` today fails because there is no wrapper. The intended split, once modules and variants exist, is:

| Task | Role |
|---|---|
| `qualityFormatCheck` | Spotless `spotlessCheck` |
| `qualityStaticCheck` | detekt and Android Lint |
| `qualityUnitTest` | Host-side unit tests for every module that has them |
| `qualityBuild` | Debug build |
| `qualityCheck` | The four tasks above. No emulator. |
| `qualityAndroidTest` | Connected tests, only when real instrumented tests exist |

Do not add these tasks as empty successes.

Emulator or device setup for `qualityAndroidTest` is also blocked. One pinned emulator configuration belongs in CI only after Room asset tests or other instrumented tests exist. There is no `transit.db`, Room builder, RapTOR package, or GTFS parser to test.

## Security, dependencies, and licenses

Current secret scan:

```bash
python3 scripts/run_repository_security.py
```

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
| Future Gradle reports | To be documented with the tasks that create them. HTML/XML reports must not be committed. |

## Updating pinned tools

1. Read the upstream release notes and checksum file.
2. Update the version, URL, and SHA-256 in `scripts/run_repository_security.py`.
3. Update the hook `rev` in `.pre-commit-config.yaml` when the hook repository changes, using the commit the release tag points at.
4. Update third-party Actions in `.github/workflows/repository-security.yml` to the full commit SHA of the reviewed release.
5. Re-run `python3 scripts/run_repository_security.py`.
6. Record the review in the pull request. Do not copy an example SHA.

## Network

| Action | Network |
|---|---|
| `python3 scripts/run_repository_security.py` on a warm `.tools/`, `.venv`, and pre-commit cache | Not required |
| First run, or a version bump | GitHub release downloads and PyPI |
| Future `./gradlew qualityCheck` | Gradle distributions, Maven dependencies, and wrapper validation need network unless the cache is already populated |
| Future OSV-Scanner | Network, for the vulnerability database, including on a schedule |
| Application runtime | Must stay offline. Build-time downloads do not change that |

## GitHub Actions

The only workflow is `repository-security`. It runs on pull requests, pushes to `main`, and manual dispatch. It does not run on a schedule. A scheduled dependency scan waits for lockfiles.

Required-check settings are documented in `docs/quality-setup.md`. They are not enabled from this repository.
