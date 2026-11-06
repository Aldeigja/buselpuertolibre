#!/usr/bin/env python3
"""Fail if tracked publish paths or content violate the public allowlist.

Runs on a fresh clone (stdlib only). GitHub Actions invokes this via pre-commit.
Maintainer pre-push uses scripts/check_publish_boundary.py as a wrapper.
"""

from __future__ import annotations

import argparse
import fnmatch
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

DEFAULT_ROOT = Path(__file__).resolve().parent.parent


def get_root() -> Path:
    override = os.environ.get("PUBLISH_BOUNDARY_ROOT")
    if override:
        return Path(override)
    return DEFAULT_ROOT
FIXTURE_JSON_PREFIX = "pipeline/tests/fixtures/"
FIXTURE_JSON_MAX_BYTES = 64 * 1024

ROOT_ALLOWLIST = frozenset(
    {
        ".gitignore",
        ".gitattributes",
        ".gitleaks.toml",
        ".pre-commit-config.yaml",
        ".cursorignore",
        "CONTRIBUTING.md",
        "README.md",
    }
)

DENY_BASENAMES = frozenset(
    {
        "itinerarios.pdf",
        "overpass_raw.json",
        "overpass_bus_routes_raw.json",
        "junta_holidays.json",
        "osm_bus_stops.json",
        "transit.db",
        "stops_review.geojson",
        "stops_unlocated.csv",
        "elpuerto_bus_map_osm.geojson",
        "elpuerto_bus_routes_OSM.json",
        "local.properties",
        "google-services.json",
    }
)

DENY_BASENAME_GLOBS = ("Horarios_*.pdf", "*.bak")

DENY_SUFFIXES = frozenset(
    {
        ".pdf",
        ".db",
        ".sqlite",
        ".geojson",
        ".csv",
        ".keystore",
        ".jks",
        ".pem",
        ".key",
        ".p12",
        ".pfx",
    }
)

CONTENT_SKIP = frozenset({".gitignore", ".gitattributes"})


def _needles() -> tuple[list[str], re.Pattern[str]]:
    paths = [
        "/" + "Users" + "/",
        "/" + "home" + "/",
        "C:" + "\\" + "Users" + "\\",
        "~" + "/" + "Library" + "/",
        "/" + "Library" + "/" + "Java" + "/",
    ]
    pem = re.compile(
        r"-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----",
        re.MULTILINE,
    )
    return paths, pem


def run_git(args: list[str], *, cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(
        ["git", *args],
        cwd=cwd or get_root(),
        check=False,
        capture_output=True,
        text=True,
    )
    return completed


def require_git_ok(completed: subprocess.CompletedProcess[str], label: str) -> str:
    if completed.returncode != 0:
        err = (completed.stderr or completed.stdout or "").strip()
        raise SystemExit(f"{label} failed ({completed.returncode}): {err}")
    return (completed.stdout or "").strip()


def list_index_paths() -> list[str]:
    completed = run_git(["ls-files", "-z"])
    require_git_ok(completed, "git ls-files")
    raw = completed.stdout or ""
    paths = [p for p in raw.split("\0") if p]
    if not paths:
        raise SystemExit("publish-boundary: no tracked files to scan.")
    return sorted(paths)


def list_tree_paths(rev: str) -> list[str]:
    completed = run_git(["ls-tree", "-r", "--name-only", "-z", rev])
    require_git_ok(completed, f"git ls-tree {rev}")
    raw = completed.stdout or ""
    paths = [p for p in raw.split("\0") if p]
    if not paths:
        raise SystemExit(f"publish-boundary: commit {rev} has no tree entries.")
    return sorted(paths)


def _normalize_rel(rel: str) -> str:
    rel = rel.replace("\\", "/")
    while rel.startswith("./"):
        rel = rel[2:]
    return rel


def path_allowlisted(rel: str) -> bool:
    rel = _normalize_rel(rel)
    if rel in ROOT_ALLOWLIST:
        return True
    if rel in (".github/check_publish_boundary.py", "buselpuertolibre/README.md"):
        return True
    if rel.startswith("pipeline/"):
        return True
    if rel.startswith("buselpuertolibre/"):
        return True
    if rel.startswith("docs/"):
        return True
    if rel.startswith(".github/workflows/"):
        return True
    return False


def path_denied(rel: str) -> str | None:
    rel = _normalize_rel(rel)
    name = Path(rel).name
    if name in DENY_BASENAMES:
        return f"denylisted basename: {name}"
    for pattern in DENY_BASENAME_GLOBS:
        if fnmatch.fnmatch(name, pattern):
            return f"denylisted name pattern {pattern!r}: {rel}"
    lower = rel.lower()
    if lower.endswith(".json"):
        if rel in {
            "buselpuertolibre/app/src/main/assets/style.json",
            "buselpuertolibre/app/src/main/assets/sprite/sprite.json",
            "buselpuertolibre/app/src/main/assets/sprite/sprite@2x.json",
        }:
            return None
        if not rel.startswith(FIXTURE_JSON_PREFIX):
            return f"JSON outside {FIXTURE_JSON_PREFIX}: {rel}"
        full = get_root() / rel
        if full.is_file():
            size = full.stat().st_size
            if size > FIXTURE_JSON_MAX_BYTES:
                return f"fixture JSON exceeds {FIXTURE_JSON_MAX_BYTES} bytes: {rel}"
        return None
    suffix = Path(rel).suffix.lower()
    if suffix in DENY_SUFFIXES:
        return f"denylisted suffix {suffix}: {rel}"
    return None


def check_paths(paths: list[str]) -> list[str]:
    errors: list[str] = []
    for rel in paths:
        if not path_allowlisted(rel):
            errors.append(f"tracked path outside allowlist: {rel}")
            continue
        reason = path_denied(rel)
        if reason:
            errors.append(reason)
    return errors


def read_worktree_bytes(rel: str) -> bytes | None:
    full = get_root() / rel
    if not full.is_file():
        return None
    return full.read_bytes()


def read_tree_bytes(rev: str, rel: str) -> bytes | None:
    completed = run_git(["show", f"{rev}:{rel}"])
    if completed.returncode != 0:
        return None
    return completed.stdout.encode()


def scan_content(paths: list[str], rev: str | None) -> list[str]:
    path_needles, pem = _needles()
    errors: list[str] = []
    for rel in paths:
        if rel in CONTENT_SKIP:
            continue
        if rev:
            payload = read_tree_bytes(rev, rel)
        else:
            payload = read_worktree_bytes(rel)
        if payload is None:
            continue
        try:
            text = payload.decode("utf-8")
        except UnicodeDecodeError:
            continue
        for needle in path_needles:
            if needle in text:
                errors.append(f"machine path needle in {rel}")
                break
        if pem.search(text):
            errors.append(f"PEM private key marker in {rel}")
    return errors


def scan(*, rev: str | None) -> list[str]:
    paths = list_tree_paths(rev) if rev else list_index_paths()
    errors = check_paths(paths)
    errors.extend(scan_content(paths, rev))
    return errors


def run_self_test() -> int:
    path_needles, _ = _needles()
    sample_path = path_needles[0] + "x" + "/bad.txt"
    checker = DEFAULT_ROOT / ".github/check_publish_boundary.py"
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        subprocess.check_call(["git", "init", "-b", "main"], cwd=tmp_path, stdout=subprocess.DEVNULL)
        leak = tmp_path / "docs" / "leak.md"
        leak.parent.mkdir(parents=True)
        leak.write_text(sample_path + "\n", encoding="utf-8")
        subprocess.check_call(["git", "add", "docs/leak.md"], cwd=tmp_path)
        env = {**os.environ, "PUBLISH_BOUNDARY_ROOT": str(tmp_path)}
        completed = subprocess.run(
            [sys.executable, str(checker)],
            env=env,
            capture_output=True,
            text=True,
        )
        if completed.returncode == 0:
            raise SystemExit("self-test: expected failure on machine path in tracked file")
    errors = scan(rev=None)
    if errors:
        raise SystemExit("self-test: clean repository failed:\n" + "\n".join(errors))
    print("publish-boundary self-test passed", flush=True)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--tree",
        metavar="SHA",
        help="Scan git tree at commit instead of the index/worktree.",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run negative control in a temp repo, then verify this repository.",
    )
    args = parser.parse_args()
    if args.self_test:
        return run_self_test()
    errors = scan(rev=args.tree)
    if errors:
        print("publish-boundary violations:", file=sys.stderr)
        for item in errors:
            print(f"  - {item}", file=sys.stderr)
        return 1
    count = len(list_tree_paths(args.tree) if args.tree else list_index_paths())
    print(f"publish-boundary ok ({count} paths)", flush=True)
    return 0


if __name__ == "__main__":
    os.chdir(get_root())
    sys.exit(main())
