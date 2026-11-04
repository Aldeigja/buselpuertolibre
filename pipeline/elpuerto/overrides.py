from __future__ import annotations

import json
from pathlib import Path


def load_overrides(path: Path) -> dict[str, tuple[float, float]]:
    if not path.is_file():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    out: dict[str, tuple[float, float]] = {}
    unknown: list[str] = []
    for feature in data.get("features", []):
        if feature.get("geometry", {}).get("type") != "Point":
            continue
        props = feature.get("properties") or {}
        stop_id = props.get("stop_id")
        if not stop_id:
            continue
        coords = feature["geometry"]["coordinates"]
        out[str(stop_id)] = (float(coords[1]), float(coords[0]))
    return out


def unknown_override_ids(
    path: Path, valid_stop_ids: set[str]
) -> list[str]:
    if not path.is_file():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    bad: list[str] = []
    for feature in data.get("features", []):
        if feature.get("geometry", {}).get("type") != "Point":
            continue
        stop_id = (feature.get("properties") or {}).get("stop_id")
        if stop_id and str(stop_id) not in valid_stop_ids:
            bad.append(str(stop_id))
    return bad
