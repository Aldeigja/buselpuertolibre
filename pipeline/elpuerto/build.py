from __future__ import annotations

import csv
import json
import sqlite3
from dataclasses import dataclass
from pathlib import Path

from elpuerto.bind import bind_columns
from elpuerto.itinerary_pdf import parse_itinerary_pdf
from elpuerto.osm_routes import (
    detour_places,
    load_route_stops,
    map_path,
    review_from_checked_map,
    routes_path,
)
from elpuerto.junta import (
    ATTRIBUTION,
    fetch_holidays,
    load_holidays_snapshot,
    save_holidays_snapshot,
    validate_holiday_snapshot,
)
from elpuerto.models import Departure, ItineraryStop, ServiceDay
from elpuerto.osm import (
    fetch_osm_bus_stops,
    load_osm_snapshot,
    match_stop_name,
    save_osm_snapshot,
)
from elpuerto.overrides import load_overrides, unknown_override_ids
from elpuerto.route_lines import route_lines_from_review
from elpuerto.winter_pdf import fold_column, parse_winter_pdf

SEASON_START = "2026-08-31"
SEASON_END = "2027-08-30"


def local_pipeline_dir() -> Path:
    """Gitignored working directory for PDFs, OSM extracts, and the database."""
    return Path(__file__).resolve().parents[2] / "local" / "pipeline"

LINE_SERVICE_RULES: dict[int, dict[str, bool]] = {
    6: {"saturday": False, "sunday_festivo": False},
    7: {"sunday_festivo": False},
}


@dataclass
class PipelinePaths:
    root: Path

    @property
    def data(self) -> Path:
        return self.root / "data"

    @property
    def output(self) -> Path:
        return self.root / "output"

    @property
    def curation(self) -> Path:
        return self.root / "curation"

    @property
    def winter_pdf(self) -> Path:
        return self.data / "Horarios_Invierno_2026.pdf"

    @property
    def itinerary_pdf(self) -> Path:
        return self.data / "itinerarios.pdf"

    @property
    def junta_snapshot(self) -> Path:
        return self.data / "junta_holidays.json"

    @property
    def osm_snapshot(self) -> Path:
        return self.data / "osm_bus_stops.json"

    @property
    def db_path(self) -> Path:
        return self.output / "transit.db"

    @property
    def review_geojson(self) -> Path:
        return self.curation / "stops_review.geojson"

    @property
    def unlocated_csv(self) -> Path:
        return self.curation / "stops_unlocated.csv"

    @property
    def overrides_geojson(self) -> Path:
        return self.curation / "stops_overrides.geojson"


@dataclass
class PipelineResult:
    warnings: list[str]
    stop_count: int
    departure_count: int
    unlocated_count: int
    unbound_column_count: int


def run_pipeline(
    paths: PipelinePaths,
    *,
    use_network: bool = True,
) -> PipelineResult:
    paths.output.mkdir(parents=True, exist_ok=True)
    paths.curation.mkdir(parents=True, exist_ok=True)
    paths.data.mkdir(parents=True, exist_ok=True)

    warnings: list[str] = []

    if use_network:
        _download_if_missing(
            paths.winter_pdf,
            "https://bus.elpuertodesantamaria.es/pdf/2026/Horarios_Invierno_2026.pdf",
        )
        _download_if_missing(
            paths.itinerary_pdf,
            "https://www.elpuertodesantamaria.es/pub/mambiente/autobuses/itinerarios08.pdf",
        )
        holidays = fetch_holidays()
        save_holidays_snapshot(paths.junta_snapshot, holidays)
        osm_stops, query = fetch_osm_bus_stops()
        save_osm_snapshot(paths.osm_snapshot, osm_stops, query)
    else:
        holidays = load_holidays_snapshot(paths.junta_snapshot)
        osm_stops = load_osm_snapshot(paths.osm_snapshot)

    warnings.extend(validate_holiday_snapshot(holidays))

    route_file = routes_path(paths.curation)
    checked_map = map_path(paths.curation)
    use_osm_routes = route_file.is_file() and checked_map.is_file()
    if use_osm_routes:
        itinerary = load_route_stops(route_file) + detour_places()
        warnings.append(
            "Stop positions and route lines come from the checked OSM route extract."
        )
    else:
        itinerary = parse_itinerary_pdf(paths.itinerary_pdf)
    if not itinerary:
        raise RuntimeError("No stops loaded.")

    _columns, raw_times, withdrawals = parse_winter_pdf(paths.winter_pdf)
    column_labels = sorted(
        {(col.line, col.column_label) for col, _ in raw_times},
        key=lambda x: (x[0], fold_column(x[1])),
    )
    bindings, unbound = bind_columns(
        itinerary,
        column_labels,
    )

    overrides = load_overrides(paths.overrides_geojson)
    valid_ids = {s.stop_id for s in itinerary}
    bad_override_ids = unknown_override_ids(paths.overrides_geojson, valid_ids)
    if bad_override_ids:
        warnings.append(
            "Ignored override stop_id(s) with no itinerary row: "
            + ", ".join(sorted(bad_override_ids))
        )

    located: dict[str, tuple[float, float, str]] = {}
    unlocated_rows: list[ItineraryStop] = []
    for stop in itinerary:
        if stop.stop_id in overrides:
            lat, lon = overrides[stop.stop_id]
            located[stop.stop_id] = (lat, lon, "override")
            continue
        if stop.lat is not None and stop.lon is not None:
            located[stop.stop_id] = (
                stop.lat,
                stop.lon,
                stop.coord_source or "osm_route",
            )
            continue
        match = match_stop_name(stop.name, osm_stops)
        if match:
            located[stop.stop_id] = (match.lat, match.lon, "osm")
        else:
            unlocated_rows.append(stop)

    departures: list[Departure] = []
    for col, seconds in raw_times:
        key = (col.line, fold_column(col.column_label))
        stop_id = bindings.get(key)
        if not stop_id:
            continue
        departures.append(
            Departure(
                line=col.line,
                service_day=col.service_day,
                column_label=col.column_label,
                seconds_since_midnight=seconds,
                bound_stop_id=stop_id,
            )
        )

    _write_sqlite(
        paths.db_path,
        itinerary=itinerary,
        located=located,
        departures=departures,
        withdrawals=withdrawals,
        unbound=unbound,
        holidays=holidays,
    )
    if use_osm_routes:
        review = review_from_checked_map(checked_map, itinerary, located)
        paths.review_geojson.write_text(
            json.dumps(review, ensure_ascii=False),
            encoding="utf-8",
        )
    else:
        _write_review_geojson(paths.review_geojson, itinerary, located)
    _write_unlocated_csv(paths.unlocated_csv, unlocated_rows)
    if use_osm_routes:
        route_lines_from_review(paths.review_geojson, paths.db_path)

    lines_present = {s.line for s in itinerary}
    missing_lines = set(range(1, 8)) - lines_present
    if missing_lines:
        raise RuntimeError(f"Missing lines in itinerary: {sorted(missing_lines)}")

    return PipelineResult(
        warnings=warnings,
        stop_count=len(itinerary),
        departure_count=len(departures),
        unlocated_count=len(unlocated_rows),
        unbound_column_count=len(unbound),
    )


def _write_sqlite(
    db_path: Path,
    *,
    itinerary: list[ItineraryStop],
    located: dict[str, tuple[float, float, str]],
    departures: list[Departure],
    withdrawals,
    unbound,
    holidays,
) -> None:
    if db_path.exists():
        db_path.unlink()
    conn = sqlite3.connect(db_path)
    try:
        conn.executescript(
            """
            PRAGMA foreign_keys = ON;
            CREATE TABLE meta (
              key TEXT NOT NULL PRIMARY KEY,
              value TEXT NOT NULL
            );
            CREATE TABLE itinerary_stops (
              stop_id TEXT NOT NULL PRIMARY KEY,
              line INTEGER NOT NULL,
              column_side TEXT NOT NULL,
              sequence INTEGER NOT NULL,
              stop_name TEXT NOT NULL,
              lat REAL,
              lon REAL,
              coord_source TEXT
            );
            CREATE TABLE departures (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              line INTEGER NOT NULL,
              service_day TEXT NOT NULL,
              column_label TEXT NOT NULL,
              seconds_since_midnight INTEGER NOT NULL,
              bound_stop_id TEXT NOT NULL,
              FOREIGN KEY (bound_stop_id) REFERENCES itinerary_stops(stop_id)
            );
            CREATE TABLE withdrawals (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              line INTEGER NOT NULL,
              service_day TEXT NOT NULL,
              column_label TEXT NOT NULL,
              seconds_since_midnight INTEGER,
              raw TEXT NOT NULL
            );
            CREATE TABLE unbound_columns (
              line INTEGER NOT NULL,
              column_label TEXT NOT NULL,
              candidate_stop_ids TEXT NOT NULL,
              PRIMARY KEY (line, column_label)
            );
            CREATE TABLE holidays (
              date_iso TEXT NOT NULL PRIMARY KEY,
              year INTEGER NOT NULL,
              description TEXT NOT NULL,
              service_profile TEXT NOT NULL DEFAULT 'festivos'
            );
            CREATE TABLE line_service_rules (
              line INTEGER NOT NULL PRIMARY KEY,
              rules_json TEXT NOT NULL
            );
            CREATE INDEX idx_departures_stop ON departures(bound_stop_id, seconds_since_midnight);
            """
        )
        conn.execute(
            "INSERT INTO meta VALUES (?, ?), (?, ?), (?, ?)",
            (
                "season_start",
                SEASON_START,
                "season_end",
                SEASON_END,
                "holiday_attribution",
                ATTRIBUTION,
            ),
        )
        for stop in itinerary:
            lat = lon = None
            source = None
            if stop.stop_id in located:
                lat, lon, source = located[stop.stop_id]
            conn.execute(
                """
                INSERT INTO itinerary_stops
                (stop_id, line, column_side, sequence, stop_name, lat, lon, coord_source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    stop.stop_id,
                    stop.line,
                    stop.column.value,
                    stop.sequence,
                    stop.name,
                    lat,
                    lon,
                    source,
                ),
            )
        for dep in departures:
            conn.execute(
                """
                INSERT INTO departures
                (line, service_day, column_label, seconds_since_midnight, bound_stop_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                (
                    dep.line,
                    dep.service_day.value,
                    dep.column_label,
                    dep.seconds_since_midnight,
                    dep.bound_stop_id,
                ),
            )
        for w in withdrawals:
            conn.execute(
                """
                INSERT INTO withdrawals
                (line, service_day, column_label, seconds_since_midnight, raw)
                VALUES (?, ?, ?, ?, ?)
                """,
                (
                    w.line,
                    w.service_day.value,
                    w.column_label,
                    w.seconds_since_midnight,
                    w.raw,
                ),
            )
        for row in unbound:
            conn.execute(
                """
                INSERT INTO unbound_columns (line, column_label, candidate_stop_ids)
                VALUES (?, ?, ?)
                """,
                (
                    row.line,
                    row.column_label,
                    json.dumps(list(row.candidate_stop_ids)),
                ),
            )
        for h in holidays:
            conn.execute(
                """
                INSERT INTO holidays (date_iso, year, description, service_profile)
                VALUES (?, ?, ?, 'festivos')
                """,
                (h.date_iso, h.year, h.description),
            )
        for line, rules in LINE_SERVICE_RULES.items():
            conn.execute(
                "INSERT INTO line_service_rules (line, rules_json) VALUES (?, ?)",
                (line, json.dumps(rules)),
            )
        conn.commit()
    finally:
        conn.close()


def _write_review_geojson(
    path: Path,
    itinerary: list[ItineraryStop],
    located: dict[str, tuple[float, float, str]],
) -> None:
    features: list[dict] = []
    for stop in itinerary:
        if stop.stop_id not in located:
            continue
        lat, lon, source = located[stop.stop_id]
        features.append(
            {
                "type": "Feature",
                "geometry": {"type": "Point", "coordinates": [lon, lat]},
                "properties": {
                    "stop_id": stop.stop_id,
                    "stop_name": stop.name,
                    "line": stop.line,
                    "column": stop.column.value,
                    "sequence": stop.sequence,
                    "coord_source": source,
                },
            }
        )

    by_column: dict[tuple[int, str], list[ItineraryStop]] = {}
    for stop in itinerary:
        key = (stop.line, stop.column.value)
        by_column.setdefault(key, []).append(stop)
    for (line, column), stops in sorted(by_column.items()):
        coords = []
        for s in sorted(stops, key=lambda x: x.sequence):
            if s.stop_id not in located:
                continue
            lat, lon, _ = located[s.stop_id]
            coords.append([lon, lat])
        if len(coords) >= 2:
            features.append(
                {
                    "type": "Feature",
                    "geometry": {"type": "LineString", "coordinates": coords},
                    "properties": {
                        "kind": "column_path",
                        "line": line,
                        "column": column,
                    },
                }
            )

    path.write_text(
        json.dumps({"type": "FeatureCollection", "features": features}, indent=2),
        encoding="utf-8",
    )


def _write_unlocated_csv(path: Path, stops: list[ItineraryStop]) -> None:
    with path.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh)
        writer.writerow(["stop_id", "line", "column", "sequence", "stop_name"])
        for stop in stops:
            writer.writerow(
                [
                    stop.stop_id,
                    stop.line,
                    stop.column.value,
                    stop.sequence,
                    stop.name,
                ]
            )


def _download_if_missing(path: Path, url: str) -> None:
    if path.is_file():
        return
    import ssl
    from urllib.request import Request, urlopen

    try:
        import certifi
    except ImportError:
        certifi = None  # type: ignore[assignment]

    ctx = ssl.create_default_context(cafile=certifi.where()) if certifi else None
    req = Request(url, headers={"User-Agent": "elpuerto-bus-pipeline/0.1"})
    with urlopen(req, timeout=120, context=ctx) as resp:
        path.write_bytes(resp.read())
