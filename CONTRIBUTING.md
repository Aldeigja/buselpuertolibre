# Contributing

**Bus El Puerto Libre** — timetable pipeline in [`pipeline/`](pipeline/), Android app in [`buselpuertolibre/`](buselpuertolibre/).

## Before a pull request

From the repository root:

```bash
pre-commit run --all-files --show-diff-on-failure
```

GitHub Actions runs the same hooks on every push and PR ([`repository-security`](.github/workflows/repository-security.yml): publish-boundary allowlist, gitleaks, actionlint, YAML/TOML, merge conflicts, 5 MiB file limit).

Do not commit signing keys, `local.properties`, timetable PDFs, OSM dumps, generated databases, or map tiles. Those belong under gitignored [`local/`](pipeline/README.md) or stay out of the tree entirely.

## Build and test

| Area | Where to look |
| --- | --- |
| Android (Gradle, assets, APK) | [`buselpuertolibre/README.md`](buselpuertolibre/README.md) |
| Pipeline (Python, `transit.db`) | [`pipeline/README.md`](pipeline/README.md) |
| Tool versions, future `qualityCheck`, CI policy | [`docs/quality-setup.md`](docs/quality-setup.md) |

Today the app is checked with `./gradlew testDebugUnitTest assembleDebug` from `buselpuertolibre/`. Spotless, detekt, and aggregate `qualityCheck` are planned, not required in CI yet.

## Maintainers only

Gitignored on disk (not in public clones): `.cursor/`, `scripts/`, `AGENTS.md`, maintainer notes under `local/maintainer/`. Optional full-tree scan and hook install: `python3 scripts/run_repository_security.py --install-hooks`.

Do not push or change GitHub repository settings unless the maintainer asked for that.
