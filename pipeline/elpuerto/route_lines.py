from __future__ import annotations

import json
import sqlite3
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class RouteLine:
    line: int
    direction: str
    geometry_type: str
    coordinates_json: str
    colour: str | None
    name: str | None
    osm_relation_id: int | None


def load_route_lines_from_review(review_geojson: Path) -> list[RouteLine]:
    payload = json.loads(review_geojson.read_text(encoding="utf-8"))
    lines: list[RouteLine] = []
    for feature in payload.get("features", []):
        geom = feature.get("geometry") or {}
        gtype = geom.get("type")
        if gtype not in {"LineString", "MultiLineString"}:
            continue
        props = feature.get("properties") or {}
        line_ref = props.get("line_ref")
        direction = props.get("direction")
        if line_ref is None or direction is None:
            continue
        lines.append(
            RouteLine(
                line=int(line_ref),
                direction=str(direction),
                geometry_type=gtype,
                coordinates_json=json.dumps(geom["coordinates"], separators=(",", ":")),
                colour=props.get("colour"),
                name=props.get("name"),
                osm_relation_id=int(props["osm_relation_id"])
                if props.get("osm_relation_id") is not None
                else None,
            )
        )
    return _validate_variants(lines)


def _validate_variants(lines: list[RouteLine]) -> list[RouteLine]:
    by_line: dict[int, set[str]] = {}
    for row in lines:
        by_line.setdefault(row.line, set()).add(row.direction)
    for line in range(1, 6):
        dirs = by_line.get(line, set())
        if dirs != {"outbound"}:
            raise RuntimeError(
                f"Line {line} must have outbound route geometry only, found {sorted(dirs)}"
            )
    for line in (6, 7):
        dirs = by_line.get(line, set())
        if dirs != {"outbound", "inbound"}:
            raise RuntimeError(
                f"Line {line} must have outbound and inbound geometries, found {sorted(dirs)}"
            )
    if len(lines) != 9:
        raise RuntimeError(f"Expected 9 route geometries, found {len(lines)}")
    return lines


def write_route_lines(conn: sqlite3.Connection, lines: list[RouteLine]) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS route_lines (
          line INTEGER NOT NULL,
          direction TEXT NOT NULL,
          geometry_type TEXT NOT NULL,
          coordinates_json TEXT NOT NULL,
          colour TEXT,
          name TEXT,
          osm_relation_id INTEGER,
          PRIMARY KEY (line, direction)
        );
        """
    )
    conn.execute("DELETE FROM route_lines")
    for row in lines:
        conn.execute(
            """
            INSERT INTO route_lines
            (line, direction, geometry_type, coordinates_json, colour, name, osm_relation_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                row.line,
                row.direction,
                row.geometry_type,
                row.coordinates_json,
                row.colour,
                row.name,
                row.osm_relation_id,
            ),
        )


def route_lines_from_review(review_geojson: Path, db_path: Path) -> list[RouteLine]:
    lines = load_route_lines_from_review(review_geojson)
    conn = sqlite3.connect(db_path)
    try:
        write_route_lines(conn, lines)
        conn.commit()
    finally:
        conn.close()
    return lines


def expected_route_lines(review_geojson: Path) -> list[RouteLine]:
    return load_route_lines_from_review(review_geojson)
