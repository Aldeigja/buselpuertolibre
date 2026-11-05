"""OSM public-transport route variants the maintainer checked on geojson.io."""

from __future__ import annotations

import json
import math
from pathlib import Path

from elpuerto.models import ColumnSide, ItineraryStop

# A stop_position named from a nearby platform, then repeated after a multi-kilometre
# jump, is the same shelter already listed. Line 3's relation does this at the
# Fuentebravía roundabout, after Terminal Marítima.
_TELEPORT_HOP_M = 2_000.0
_SAME_SHELTER_M = 100.0

ROUTES_FILENAME = "elpuerto_bus_routes_OSM.json"
MAP_FILENAME = "elpuerto_bus_map_osm.geojson"


def detour_places() -> list[ItineraryStop]:
    """Stops the printed timetable names that are not on the checked route line.

    Cantarranas is the OSM neighbourhood node. No bus_stop with that name exists.
    Only the 07:30, 08:08 and 14:28 line 6 runs leave the road to serve it, so the
    point is not inserted into the route geometry.
    """
    return [
        ItineraryStop(
            stop_id="L6_detour_11125177414",
            line=6,
            column=ColumnSide.LEFT,
            sequence=0,
            name="Cantarranas",
            lat=36.6156629,
            lon=-6.2746233,
            osm_node_id=11125177414,
            direction="detour",
            role="place",
            coord_source="osm_place",
        )
    ]


def routes_path(curation: Path) -> Path:
    return curation / ROUTES_FILENAME


def map_path(curation: Path) -> Path:
    return curation / MAP_FILENAME


def load_route_stops(path: Path) -> list[ItineraryStop]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    stops: list[ItineraryStop] = []
    for route in payload.get("routes", []):
        line = int(route["line_ref"])
        direction = str(route.get("direction") or "outbound")
        column = ColumnSide.RIGHT if direction == "inbound" else ColumnSide.LEFT
        relation = int(route["osm_relation_id"])
        for stop in without_teleported_repeats(route.get("stops", [])):
            sequence = int(stop["stop_sequence"])
            node_id = int(stop["osm_node_id"])
            stops.append(
                ItineraryStop(
                    stop_id=f"L{line}_{direction}_{sequence:03d}_{node_id}",
                    line=line,
                    column=column,
                    sequence=sequence,
                    name=str(stop["name"]),
                    lat=float(stop["lat"]),
                    lon=float(stop["lon"]),
                    osm_node_id=node_id,
                    direction=direction,
                    role=str(stop.get("role") or ""),
                )
            )
            _ = relation
    if not stops:
        raise RuntimeError(f"No stops in {path}")
    lines = {s.line for s in stops}
    missing = set(range(1, 8)) - lines
    if missing:
        raise RuntimeError(f"OSM routes missing lines: {sorted(missing)}")
    return stops


def without_teleported_repeats(stops: list[dict]) -> list[dict]:
    """Drop a repeated shelter that the route member order jumps back to."""
    kept: list[dict] = []
    for stop in stops:
        if _is_teleported_repeat(stop, kept):
            continue
        kept.append(stop)
    return kept


def _is_teleported_repeat(stop: dict, earlier: list[dict]) -> bool:
    if stop.get("role") != "stop" or stop.get("name_source") != "nearby_platform":
        return False
    if not earlier:
        return False
    previous = earlier[-1]
    if _metres(previous, stop) < _TELEPORT_HOP_M:
        return False
    return any(
        other.get("name") == stop.get("name") and _metres(other, stop) <= _SAME_SHELTER_M
        for other in earlier
    )


def _metres(a: dict, b: dict) -> float:
    lat1 = math.radians(float(a["lat"]))
    lat2 = math.radians(float(b["lat"]))
    dlat = lat2 - lat1
    dlon = math.radians(float(b["lon"]) - float(a["lon"]))
    x = dlon * math.cos((lat1 + lat2) / 2)
    return math.hypot(x, dlat) * 6_371_000.0


def review_from_checked_map(
    map_geojson: Path,
    route_stops: list[ItineraryStop],
    located: dict[str, tuple[float, float, str]],
) -> dict:
    """Keep the street-aligned ways. Points follow route memberships."""
    checked = json.loads(map_geojson.read_text(encoding="utf-8"))
    features: list[dict] = []
    for stop in route_stops:
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
                    "osm_node_id": stop.osm_node_id,
                    "line": stop.line,
                    "direction": stop.direction,
                    "column": stop.column.value,
                    "sequence": stop.sequence,
                    "coord_source": source,
                },
            }
        )
    for feature in checked.get("features", []):
        geom = feature.get("geometry") or {}
        if geom.get("type") in {"LineString", "MultiLineString"}:
            features.append(feature)
    return {"type": "FeatureCollection", "features": features}
