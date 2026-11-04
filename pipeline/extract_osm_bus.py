#!/usr/bin/env python3
"""Fetch and parse El Puerto urban bus routes (OSM lines 1–7) via Overpass."""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import shutil
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal

import requests

USER_AGENT = "ElPuertoBus/1.0 (+https://github.com/el-puerto-bus; transit-data)"
DEFAULT_OVERPASS_URL = "https://overpass-api.de/api/interpreter"
MUNICIPALITY_NAME = "El Puerto de Santa María"
URBAN_LINE_MIN = 1
URBAN_LINE_MAX = 7

STOP_MEMBER_ROLES = frozenset(
    {"stop", "platform", "stop_entry_only", "stop_exit_only"}
)
WAY_MEMBER_ROLES = frozenset({"", "street", "forward", "backward"})

LINE_REF_RE = re.compile(
    r"^(?:l(?:ínea|inea)?[\s\-]?|l[\-\s]?)?(\d+)\s*$",
    re.IGNORECASE,
)

COORD_EPSILON = 1e-6
NEARBY_PLATFORM_MAX_M = 40.0

LON_MIN, LON_MAX = -6.30, -6.10
LAT_MIN, LAT_MAX = 36.55, 36.65

EXPECTED_STOP_COUNTS = (41, 36, 54, 41, 62, 12, 14, 14, 13)

AREA_QUERY = """
[out:json][timeout:90];
area["name"="El Puerto de Santa María"]["admin_level"="8"]->.a;
relation["type"="route"]["route"="bus"](area.a)->.routes;
(.routes; >;);
out body;
""".strip()

BBOX_QUERY = """
[out:json][timeout:90];
(
  relation["type"="route"]["route"="bus"](36.57,-6.27,36.64,-6.17);
);
(. _; >;);
out body;
""".strip()

NameSource = Literal["own", "nearby_platform", "none"]


@dataclass(frozen=True)
class RouteStop:
    stop_sequence: int
    sequence_index: int
    osm_node_id: int
    name: str | None
    name_source: NameSource
    ref: str | None
    osm_node_type: Literal["platform", "stop_position"]
    role: str
    lat: float
    lon: float
    tags: dict[str, str]


@dataclass
class BusRoute:
    line_ref: str
    osm_relation_id: int
    name: str
    direction: str
    from_terminal: str | None
    to_terminal: str | None
    operator: str | None
    colour: str | None
    network: str | None
    stops: list[RouteStop] = field(default_factory=list)
    geometry: dict[str, Any] = field(default_factory=dict)


def parse_line_ref(ref: str | None) -> str | None:
    if ref is None:
        return None
    text = ref.strip()
    if not text:
        return None
    match = LINE_REF_RE.match(text)
    if match:
        number = int(match.group(1))
    elif text.isdigit():
        number = int(text)
    else:
        return None
    if URBAN_LINE_MIN <= number <= URBAN_LINE_MAX:
        return str(number)
    return None


def overpass_url() -> str:
    custom = os.environ.get("OVERPASS_URL", "").strip()
    return custom or DEFAULT_OVERPASS_URL


def fetch_overpass(
    query: str,
    *,
    session: requests.Session | None = None,
    max_retries: int = 5,
) -> dict[str, Any]:
    http = session or requests.Session()
    url = overpass_url()
    delay = 1.0
    last_error: Exception | None = None
    for attempt in range(max_retries):
        try:
            response = http.post(
                url,
                data={"data": query},
                headers={"User-Agent": USER_AGENT},
                timeout=200,
            )
            if response.status_code in (429, 504):
                if attempt + 1 >= max_retries:
                    raise requests.HTTPError(
                        f"{response.status_code} from Overpass", response=response
                    )
                time.sleep(delay)
                delay = min(delay * 2, 60.0)
                continue
            response.raise_for_status()
            return response.json()
        except (requests.RequestException, json.JSONDecodeError) as exc:
            last_error = exc
            if attempt + 1 >= max_retries:
                break
            time.sleep(delay)
            delay = min(delay * 2, 60.0)
    raise RuntimeError("Overpass request failed") from last_error


def fetch_bus_overpass(*, session: requests.Session | None = None) -> dict[str, Any]:
    payload = fetch_overpass(AREA_QUERY, session=session)
    relations = [
        el
        for el in payload.get("elements", [])
        if el.get("type") == "relation" and el.get("tags", {}).get("route") == "bus"
    ]
    if not relations:
        payload = fetch_overpass(BBOX_QUERY, session=session)
    return payload


def load_overpass_dump(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _index_elements(
    elements: list[dict[str, Any]],
) -> tuple[dict[int, dict[str, Any]], dict[int, dict[str, Any]], list[dict[str, Any]]]:
    nodes: dict[int, dict[str, Any]] = {}
    ways: dict[int, dict[str, Any]] = {}
    relations: list[dict[str, Any]] = []
    for element in elements:
        kind = element.get("type")
        element_id = int(element["id"])
        if kind == "node":
            nodes[element_id] = element
        elif kind == "way":
            ways[element_id] = element
        elif kind == "relation":
            relations.append(element)
    return nodes, ways, relations


def _node_tags(node: dict[str, Any]) -> dict[str, str]:
    return {str(k): str(v) for k, v in node.get("tags", {}).items()}


def _infer_osm_node_type(role: str, tags: dict[str, str]) -> Literal["platform", "stop_position"]:
    pt = tags.get("public_transport", "")
    if role == "platform" or pt == "platform":
        return "platform"
    return "stop_position"


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = (
        math.sin(dphi / 2) ** 2
        + math.cos(p1) * math.cos(p2) * math.sin(dlambda / 2) ** 2
    )
    return 2 * r * math.asin(min(1.0, math.sqrt(a)))


def _relation_platform_nodes(
    relation: dict[str, Any], nodes: dict[int, dict[str, Any]]
) -> list[dict[str, Any]]:
    platforms: list[dict[str, Any]] = []
    for member in relation.get("members", []):
        if member.get("type") != "node":
            continue
        role = member.get("role", "")
        node = nodes.get(int(member["ref"]))
        if not node or "lat" not in node:
            continue
        tags = _node_tags(node)
        if role == "platform" or tags.get("public_transport") == "platform":
            platforms.append(node)
    return platforms


def _resolve_name(
    node: dict[str, Any],
    role: str,
    osm_node_type: Literal["platform", "stop_position"],
    platforms: list[dict[str, Any]],
) -> tuple[str | None, NameSource]:
    tags = _node_tags(node)
    own = tags.get("name")
    if own:
        return own, "own"
    if osm_node_type == "stop_position" or role == "stop":
        lat, lon = float(node["lat"]), float(node["lon"])
        best_name: str | None = None
        best_dist = NEARBY_PLATFORM_MAX_M + 1
        for platform in platforms:
            ptags = _node_tags(platform)
            pname = ptags.get("name")
            if not pname:
                continue
            dist = haversine_m(lat, lon, float(platform["lat"]), float(platform["lon"]))
            if dist <= NEARBY_PLATFORM_MAX_M and dist < best_dist:
                best_dist = dist
                best_name = pname
        if best_name:
            return best_name, "nearby_platform"
    return None, "none"


def _coords_close(a: tuple[float, float], b: tuple[float, float]) -> bool:
    return abs(a[0] - b[0]) <= COORD_EPSILON and abs(a[1] - b[1]) <= COORD_EPSILON


def _way_line_coords(
    way: dict[str, Any],
    nodes: dict[int, dict[str, Any]],
    *,
    reverse: bool = False,
) -> list[tuple[float, float]]:
    node_ids = list(way.get("nodes", []))
    if reverse:
        node_ids.reverse()
    coords: list[tuple[float, float]] = []
    for node_id in node_ids:
        node = nodes.get(int(node_id))
        if not node or "lat" not in node or "lon" not in node:
            continue
        coords.append((float(node["lon"]), float(node["lat"])))
    return coords


def _member_way_reversed(role: str) -> bool:
    return role.lower() in {"backward", "reverse", "backward/reverse"}


def stitch_way_geometries(segments: list[list[tuple[float, float]]]) -> dict[str, Any]:
    if not segments:
        return {"type": "LineString", "coordinates": []}
    polylines: list[list[tuple[float, float]]] = []
    current = list(segments[0])
    for segment in segments[1:]:
        if not segment:
            continue
        if _coords_close(current[-1], segment[0]):
            current.extend(segment[1:])
            continue
        if _coords_close(current[-1], segment[-1]):
            reversed_seg = list(reversed(segment))
            current.extend(reversed_seg[1:])
            continue
        polylines.append(current)
        current = list(segment)
    polylines.append(current)
    if len(polylines) == 1:
        return {"type": "LineString", "coordinates": polylines[0]}
    return {"type": "MultiLineString", "coordinates": polylines}


def _infer_direction(
    tags: dict[str, str],
    *,
    variant_index: int,
    variant_count: int,
) -> str:
    direction = tags.get("direction", "").strip().lower()
    if direction in {"forward", "outbound"}:
        return "outbound"
    if direction in {"backward", "inbound"}:
        return "inbound"
    if tags.get("roundtrip") == "yes" and variant_count == 1:
        return "outbound"
    if variant_count <= 1:
        return "outbound"
    return "outbound" if variant_index == 0 else "inbound"


def _stop_to_dict(stop: RouteStop) -> dict[str, Any]:
    return {
        "stop_sequence": stop.stop_sequence,
        "sequence_index": stop.sequence_index,
        "osm_node_id": stop.osm_node_id,
        "name": stop.name,
        "name_source": stop.name_source,
        "ref": stop.ref,
        "osm_node_type": stop.osm_node_type,
        "role": stop.role,
        "lat": stop.lat,
        "lon": stop.lon,
        "tags": stop.tags,
    }


def _parse_relation_route(
    relation: dict[str, Any],
    nodes: dict[int, dict[str, Any]],
    ways: dict[int, dict[str, Any]],
    *,
    line_ref: str,
    direction: str,
) -> BusRoute:
    tags = relation.get("tags", {})
    platforms = _relation_platform_nodes(relation, nodes)
    stops: list[RouteStop] = []
    way_segments: list[list[tuple[float, float]]] = []
    stop_sequence = 0

    for sequence_index, member in enumerate(relation.get("members", [])):
        member_type = member.get("type")
        role = member.get("role", "")
        ref = int(member["ref"])

        if member_type == "node" and role in STOP_MEMBER_ROLES:
            node = nodes.get(ref)
            if not node or "lat" not in node or "lon" not in node:
                continue
            node_tags = _node_tags(node)
            osm_node_type = _infer_osm_node_type(role, node_tags)
            name, name_source = _resolve_name(node, role, osm_node_type, platforms)
            stop_sequence += 1
            stops.append(
                RouteStop(
                    stop_sequence=stop_sequence,
                    sequence_index=sequence_index,
                    osm_node_id=ref,
                    name=name,
                    name_source=name_source,
                    ref=node_tags.get("ref"),
                    osm_node_type=osm_node_type,
                    role=role,
                    lat=float(node["lat"]),
                    lon=float(node["lon"]),
                    tags=node_tags,
                )
            )
        elif member_type == "way" and (
            role in WAY_MEMBER_ROLES or role.lower() in {"forward", "backward"}
        ):
            way = ways.get(ref)
            if not way:
                continue
            segment = _way_line_coords(
                way, nodes, reverse=_member_way_reversed(role)
            )
            if segment:
                way_segments.append(segment)

    return BusRoute(
        line_ref=line_ref,
        osm_relation_id=int(relation["id"]),
        name=tags.get("name", ""),
        direction=direction,
        from_terminal=tags.get("from"),
        to_terminal=tags.get("to"),
        operator=tags.get("operator"),
        colour=tags.get("colour"),
        network=tags.get("network"),
        stops=stops,
        geometry=stitch_way_geometries(way_segments),
    )


def parse_bus_routes(
    overpass_payload: dict[str, Any],
    *,
    line_filter: set[str] | None = None,
) -> list[BusRoute]:
    nodes, ways, relations = _index_elements(overpass_payload.get("elements", []))
    by_line: dict[str, list[dict[str, Any]]] = {}

    for relation in relations:
        rtags = relation.get("tags", {})
        if rtags.get("route") != "bus":
            continue
        line_ref = parse_line_ref(rtags.get("ref"))
        if line_ref is None:
            continue
        if line_filter is not None and line_ref not in line_filter:
            continue
        by_line.setdefault(line_ref, []).append(relation)

    routes: list[BusRoute] = []
    for line_ref in sorted(by_line, key=int):
        variants = sorted(by_line[line_ref], key=lambda rel: int(rel["id"]))
        for index, relation in enumerate(variants):
            direction = _infer_direction(
                relation.get("tags", {}),
                variant_index=index,
                variant_count=len(variants),
            )
            routes.append(
                _parse_relation_route(
                    relation,
                    nodes,
                    ways,
                    line_ref=line_ref,
                    direction=direction,
                )
            )
    return routes


def routes_to_transit_document(routes: list[BusRoute]) -> dict[str, Any]:
    return {
        "updated_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "municipality": MUNICIPALITY_NAME,
        "routes": [
            {
                "line_ref": route.line_ref,
                "osm_relation_id": route.osm_relation_id,
                "name": route.name,
                "direction": route.direction,
                "from": route.from_terminal,
                "to": route.to_terminal,
                "operator": route.operator,
                "colour": route.colour,
                "network": route.network,
                "stops_count": len(route.stops),
                "stops": [_stop_to_dict(stop) for stop in route.stops],
            }
            for route in routes
        ],
    }


def routes_to_geojson(routes: list[BusRoute]) -> dict[str, Any]:
    stop_index: dict[int, dict[str, Any]] = {}

    for route in routes:
        for stop in route.stops:
            entry = stop_index.setdefault(
                stop.osm_node_id,
                {
                    "stop_id": stop.osm_node_id,
                    "name": stop.name,
                    "ref": stop.ref,
                    "name_source": stop.name_source,
                    "line_refs": set(),
                    "sequences": [],
                    "lat": stop.lat,
                    "lon": stop.lon,
                },
            )
            if stop.name and not entry["name"]:
                entry["name"] = stop.name
                entry["name_source"] = stop.name_source
            if stop.ref and not entry["ref"]:
                entry["ref"] = stop.ref
            entry["line_refs"].add(route.line_ref)
            entry["sequences"].append(
                {
                    "line_ref": route.line_ref,
                    "direction": route.direction,
                    "sequence": stop.stop_sequence,
                }
            )

    features: list[dict[str, Any]] = []
    for entry in stop_index.values():
        features.append(
            {
                "type": "Feature",
                "geometry": {
                    "type": "Point",
                    "coordinates": [entry["lon"], entry["lat"]],
                },
                "properties": {
                    "stop_id": entry["stop_id"],
                    "name": entry["name"],
                    "ref": entry["ref"],
                    "line_refs": sorted(entry["line_refs"], key=int),
                    "sequence": entry["sequences"],
                    "name_source": entry["name_source"],
                },
            }
        )

    for route in routes:
        if not route.geometry.get("coordinates"):
            continue
        features.append(
            {
                "type": "Feature",
                "geometry": route.geometry,
                "properties": {
                    "line_ref": route.line_ref,
                    "name": route.name,
                    "direction": route.direction,
                    "colour": route.colour,
                    "osm_relation_id": route.osm_relation_id,
                },
            }
        )

    return {"type": "FeatureCollection", "features": features}


def _coord_in_bounds(lon: float, lat: float) -> bool:
    return LON_MIN <= lon <= LON_MAX and LAT_MIN <= lat <= LAT_MAX


def build_report(routes: list[BusRoute], geojson: dict[str, Any]) -> str:
    lines: list[str] = []
    lines.append("El Puerto OSM bus extract report")
    lines.append(f"Generated: {datetime.now(timezone.utc).isoformat()}")
    lines.append("")
    lines.append(f"Urban route variants: {len(routes)}")
    lines.append("")

    actual_counts: list[int] = []
    for route in routes:
        count = len(route.stops)
        actual_counts.append(count)
        lines.append(
            f"  relation {route.osm_relation_id} ref={route.line_ref} "
            f"direction={route.direction} stops={count} name={route.name!r}"
        )

    lines.append("")
    stop_stats: dict[int, dict[str, Any]] = {}
    for route in routes:
        for stop in route.stops:
            rec = stop_stats.setdefault(
                stop.osm_node_id,
                {
                    "name": stop.name,
                    "name_source": stop.name_source,
                    "lat": stop.lat,
                    "lon": stop.lon,
                },
            )
            if stop.name and not rec["name"]:
                rec["name"] = stop.name
                rec["name_source"] = stop.name_source

    unique = len(stop_stats)
    with_name = sum(1 for s in stop_stats.values() if s["name"])
    by_source: dict[str, int] = {"own": 0, "nearby_platform": 0, "none": 0}
    for rec in stop_stats.values():
        if rec["name"]:
            src = rec["name_source"]
            by_source[src] = by_source.get(src, 0) + 1
        else:
            by_source["none"] += 1

    lines.append(f"Unique stop nodes: {unique}")
    lines.append(f"Stops with a name: {with_name}")
    lines.append(
        f"  name_source own={by_source.get('own', 0)} "
        f"nearby_platform={by_source.get('nearby_platform', 0)} "
        f"none={by_source['none']}"
    )
    lines.append("")

    unnamed = [
        (nid, rec["lat"], rec["lon"])
        for nid, rec in stop_stats.items()
        if not rec["name"]
    ]
    lines.append(f"Stops without name (showing up to 20 of {len(unnamed)}):")
    for nid, lat, lon in unnamed[:20]:
        lines.append(f"  node {nid} lat={lat} lon={lon}")

    lines.append("")
    lines.append("Stop count check vs previous run:")
    lines.append(f"  expected: {list(EXPECTED_STOP_COUNTS)}")
    lines.append(f"  actual:   {actual_counts}")
    if actual_counts == list(EXPECTED_STOP_COUNTS):
        lines.append("  OK: counts match")
    else:
        for i, (exp, act) in enumerate(
            zip(EXPECTED_STOP_COUNTS, actual_counts, strict=False)
        ):
            if exp != act:
                lines.append(f"  MISMATCH route index {i}: expected {exp}, got {act}")
        if len(actual_counts) != len(EXPECTED_STOP_COUNTS):
            lines.append(
                f"  MISMATCH route count: expected {len(EXPECTED_STOP_COUNTS)}, "
                f"got {len(actual_counts)}"
            )

    lines.append("")
    json.loads(json.dumps(geojson))
    lines.append("GeoJSON: parses as JSON OK")
    out_of_bounds = 0
    for feature in geojson.get("features", []):
        geom = feature.get("geometry", {})
        gtype = geom.get("type")
        coords = geom.get("coordinates", [])
        points: list[tuple[float, float]] = []
        if gtype == "Point":
            points = [(coords[0], coords[1])]
        elif gtype == "LineString":
            points = [(c[0], c[1]) for c in coords]
        elif gtype == "MultiLineString":
            for line in coords:
                points.extend((c[0], c[1]) for c in line)
        for lon, lat in points:
            if not _coord_in_bounds(lon, lat):
                out_of_bounds += 1
    if out_of_bounds == 0:
        lines.append(
            f"Coordinates: all within lon [{LON_MIN}, {LON_MAX}] "
            f"lat [{LAT_MIN}, {LAT_MAX}]"
        )
    else:
        lines.append(f"Coordinates: {out_of_bounds} points outside bounds")

    return "\n".join(lines) + "\n"


def _backup(path: Path) -> None:
    if path.is_file():
        shutil.copy2(path, path.with_suffix(path.suffix + ".bak"))


def run(
    pipeline_root: Path,
    *,
    cached: Path | None = None,
    session: requests.Session | None = None,
) -> tuple[list[BusRoute], str]:
    data_dir = pipeline_root / "data"
    curation_dir = pipeline_root / "curation"
    raw_path = data_dir / "overpass_raw.json"
    report_path = data_dir / "report.txt"
    json_path = curation_dir / "elpuerto_bus_routes_OSM.json"
    geojson_path = curation_dir / "elpuerto_bus_map_osm.geojson"

    if cached is not None:
        payload = load_overpass_dump(cached)
    else:
        payload = fetch_bus_overpass(session=session)
        data_dir.mkdir(parents=True, exist_ok=True)
        raw_path.write_text(
            json.dumps(payload, ensure_ascii=False),
            encoding="utf-8",
        )

    routes = parse_bus_routes(payload)
    transit = routes_to_transit_document(routes)
    geojson = routes_to_geojson(routes)
    report = build_report(routes, geojson)

    _backup(json_path)
    _backup(geojson_path)

    curation_dir.mkdir(parents=True, exist_ok=True)
    json_path.write_text(
        json.dumps(transit, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    geojson_path.write_text(
        json.dumps(geojson, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    report_path.write_text(report, encoding="utf-8")
    print(report, end="")
    return routes, report


def main() -> int:
    parser = argparse.ArgumentParser(description="Extract El Puerto OSM bus routes")
    parser.add_argument(
        "--cached",
        type=Path,
        default=None,
        help="Use saved Overpass JSON (e.g. data/overpass_raw.json)",
    )
    args = parser.parse_args()
    pipeline_root = Path(__file__).resolve().parents[1] / "local" / "pipeline"
    cached = args.cached
    if cached is not None and not cached.is_absolute():
        cached = pipeline_root / cached
    try:
        run(pipeline_root, cached=cached)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
