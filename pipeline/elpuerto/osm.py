from __future__ import annotations

import json
from pathlib import Path
from urllib.request import Request, urlopen

from elpuerto.models import OsmStop
from elpuerto.normalize import fold_name

# OpenStreetMap relation for El Puerto de Santa María (admin boundary).
MUNICIPALITY_RELATION_ID = 342093
OVERPASS_URL = "https://overpass-api.de/api/interpreter"


def fetch_osm_bus_stops() -> tuple[list[OsmStop], str]:
    """Return bus stops with a name tag inside the municipal boundary."""
    query = f"""
    [out:json][timeout:180];
    relation({MUNICIPALITY_RELATION_ID});
    map_to_area->.a;
    (
      node["highway"="bus_stop"]["name"](area.a);
    );
    out body;
    """
    req = Request(
        OVERPASS_URL,
        data=f"data={query}".encode("utf-8"),
        headers={"User-Agent": "elpuerto-bus-pipeline/0.1"},
        method="POST",
    )
    with urlopen(req, timeout=200) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    stops: list[OsmStop] = []
    for el in payload.get("elements", []):
        if el.get("type") != "node":
            continue
        name = el.get("tags", {}).get("name")
        if not name:
            continue
        stops.append(
            OsmStop(
                osm_id=int(el["id"]),
                name=name,
                lat=float(el["lat"]),
                lon=float(el["lon"]),
            )
        )
    if not stops:
        raise RuntimeError(
            "Overpass returned no named bus stops inside the municipal boundary."
        )
    return stops, query.strip()


def load_osm_snapshot(path: Path) -> list[OsmStop]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return [
        OsmStop(
            osm_id=int(item["osm_id"]),
            name=str(item["name"]),
            lat=float(item["lat"]),
            lon=float(item["lon"]),
        )
        for item in data.get("stops", [])
    ]


def save_osm_snapshot(path: Path, stops: list[OsmStop], query: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "relation_id": MUNICIPALITY_RELATION_ID,
        "overpass_query": query,
        "stops": [
            {
                "osm_id": s.osm_id,
                "name": s.name,
                "lat": s.lat,
                "lon": s.lon,
                "folded": fold_name(s.name),
            }
            for s in stops
        ],
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def match_stop_name(name: str, osm_stops: list[OsmStop]) -> OsmStop | None:
    target = fold_name(name)
    matches = [s for s in osm_stops if fold_name(s.name) == target]
    if len(matches) == 1:
        return matches[0]
    return None
