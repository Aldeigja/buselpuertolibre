"""Municipal PMTiles basemap from OpenStreetMap, for the offline app."""

from __future__ import annotations

import json
import ssl
import subprocess
import tempfile
from pathlib import Path
from urllib.request import Request, urlopen

from elpuerto.build import local_pipeline_dir

PMTILES_NAME = "el_puerto.pmtiles"
BASEMAP_FILENAME = "overpass_basemap.json"
OVERPASS_URL = "https://overpass-api.de/api/interpreter"

# Covers the urban lines, Valdelagrana, Fuentebravía, and the bay edge.
SOUTH, WEST, NORTH, EAST = 36.54, -6.32, 36.66, -6.10

BASEMAP_QUERY = f"""
[out:json][timeout:240];
(
  way["highway"]({SOUTH},{WEST},{NORTH},{EAST});
  way["waterway"~"^(riverbank|dock|canal|river)$"]({SOUTH},{WEST},{NORTH},{EAST});
  way["natural"~"^(water|coastline|bay|wetland)$"]({SOUTH},{WEST},{NORTH},{EAST});
  way["landuse"~"^(forest|grass|meadow|recreation_ground|cemetery|vineyard|orchard|allotments|reservoir)$"]({SOUTH},{WEST},{NORTH},{EAST});
  way["leisure"~"^(park|garden|pitch|playground|nature_reserve)$"]({SOUTH},{WEST},{NORTH},{EAST});
  way["building"]({SOUTH},{WEST},{NORTH},{EAST});
  node["name"]["amenity"]({SOUTH},{WEST},{NORTH},{EAST});
  node["name"]["shop"]({SOUTH},{WEST},{NORTH},{EAST});
  node["name"]["tourism"]({SOUTH},{WEST},{NORTH},{EAST});
  node["name"]["leisure"]({SOUTH},{WEST},{NORTH},{EAST});
  way["name"]["amenity"]({SOUTH},{WEST},{NORTH},{EAST});
  way["name"]["shop"]({SOUTH},{WEST},{NORTH},{EAST});
  way["name"]["tourism"]({SOUTH},{WEST},{NORTH},{EAST});
);
out geom;
""".strip()

WATER_KEYS = {"natural": {"water", "bay", "wetland"}, "waterway": {"riverbank", "dock", "canal", "river"}, "landuse": {"reservoir"}}

# Named places worth a label. Benches, bins, and parking spaces stay off the map.
SKIP_AMENITY = {
    "atm",
    "bench",
    "bicycle_parking",
    "drinking_water",
    "parking",
    "parking_entrance",
    "parking_space",
    "post_box",
    "recycling",
    "shelter",
    "telephone",
    "toilets",
    "vending_machine",
    "waste_basket",
    "waste_disposal",
}
SKIP_TOURISM = {"artwork", "information", "yes"}
PLACE_RANK = {
    "hospital": 0,
    "clinic": 1,
    "doctors": 1,
    "pharmacy": 2,
    "school": 3,
    "kindergarten": 3,
    "college": 3,
    "university": 3,
    "bank": 4,
    "supermarket": 5,
    "convenience": 5,
    "fuel": 6,
    "restaurant": 7,
    "cafe": 7,
    "fast_food": 7,
    "bar": 8,
    "pub": 8,
}
PARK_KEYS = {
    "landuse": {"forest", "grass", "meadow", "recreation_ground", "cemetery", "vineyard", "orchard", "allotments"},
    "leisure": {"park", "garden", "pitch", "playground", "nature_reserve"},
}


def basemap_assets_dir(root: Path | None = None) -> Path:
    return (root or local_pipeline_dir()) / "assets"


def basemap_snapshot(root: Path | None = None) -> Path:
    return (root or local_pipeline_dir()) / "data" / BASEMAP_FILENAME


def place_properties(tags: dict) -> dict | None:
    """A named shop, amenity, or similar place. Streets are labelled separately."""
    name = str(tags.get("name") or "").strip()
    if not name:
        return None
    amenity = str(tags.get("amenity") or "")
    shop = str(tags.get("shop") or "")
    tourism = str(tags.get("tourism") or "")
    leisure = str(tags.get("leisure") or "")
    if amenity in SKIP_AMENITY:
        amenity = ""
    if tourism in SKIP_TOURISM:
        tourism = ""
    if leisure not in {"park", "playground", "sports_centre", "stadium", "marina"}:
        leisure = ""
    kind = amenity or (f"shop" if shop else "") or tourism or leisure
    if not kind:
        return None
    return {"name": name, "kind": kind, "rank": PLACE_RANK.get(kind, 12)}


def _centroid(coords: list[list[float]]) -> list[float] | None:
    if not coords:
        return None
    return [sum(point[0] for point in coords) / len(coords), sum(point[1] for point in coords) / len(coords)]


def _point(lon: float, lat: float, properties: dict) -> dict:
    return {
        "type": "Feature",
        "geometry": {"type": "Point", "coordinates": [lon, lat]},
        "properties": properties,
    }


def fetch_basemap(dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    try:
        import certifi
    except ImportError:
        certifi = None  # type: ignore[assignment]
    ctx = ssl.create_default_context(cafile=certifi.where()) if certifi else None
    req = Request(
        OVERPASS_URL,
        data=BASEMAP_QUERY.encode("utf-8"),
        headers={"User-Agent": "elpuerto-bus-pipeline/0.1"},
        method="POST",
    )
    with urlopen(req, timeout=240, context=ctx) as resp:
        dest.write_bytes(resp.read())


def _coords(element: dict) -> list[list[float]]:
    geom = element.get("geometry") or []
    return [[float(pt["lon"]), float(pt["lat"])] for pt in geom if "lon" in pt and "lat" in pt]


def _closed(coords: list[list[float]]) -> bool:
    if len(coords) < 4:
        return False
    return abs(coords[0][0] - coords[-1][0]) < 1e-6 and abs(coords[0][1] - coords[-1][1]) < 1e-6


def _near(a: list[float], b: list[float], tol: float = 1e-5) -> bool:
    return abs(a[0] - b[0]) <= tol and abs(a[1] - b[1]) <= tol


def _chain_coastline(lines: list[list[list[float]]]) -> list[list[list[float]]]:
    """Join OSM coastline ways end to start. Direction stays as digitized."""
    unused = [line[:] for line in lines if len(line) >= 2]
    chains: list[list[list[float]]] = []
    while unused:
        chain = unused.pop(0)
        changed = True
        while changed:
            changed = False
            for index, other in enumerate(unused):
                if _near(chain[-1], other[0]):
                    chain = chain + other[1:]
                    unused.pop(index)
                    changed = True
                    break
                if _near(chain[0], other[-1]):
                    chain = other + chain[1:]
                    unused.pop(index)
                    changed = True
                    break
        chains.append(chain)
    return chains


def _clip_segment(
    start: list[float],
    end: list[float],
    west: float,
    south: float,
    east: float,
    north: float,
) -> list[list[float]] | None:
    """Liang–Barsky clip. Returns the inside portion, including border hits."""
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    p = (-dx, dx, -dy, dy)
    q = (start[0] - west, east - start[0], start[1] - south, north - start[1])
    u1, u2 = 0.0, 1.0
    for pi, qi in zip(p, q):
        if abs(pi) < 1e-15:
            if qi < 0:
                return None
            continue
        t = qi / pi
        if pi < 0:
            u1 = max(u1, t)
        else:
            u2 = min(u2, t)
        if u1 > u2:
            return None
    return [
        [start[0] + u1 * dx, start[1] + u1 * dy],
        [start[0] + u2 * dx, start[1] + u2 * dy],
    ]


def _clip_polyline(
    coords: list[list[float]],
    west: float,
    south: float,
    east: float,
    north: float,
) -> list[list[list[float]]]:
    parts: list[list[list[float]]] = []
    current: list[list[float]] = []
    for start, end in zip(coords, coords[1:]):
        clipped = _clip_segment(start, end, west, south, east, north)
        if clipped is None:
            if len(current) >= 2:
                parts.append(current)
            current = []
            continue
        if not current:
            current = [clipped[0], clipped[1]]
        elif _near(current[-1], clipped[0], 1e-8):
            current.append(clipped[1])
        else:
            if len(current) >= 2:
                parts.append(current)
            current = [clipped[0], clipped[1]]
    if len(current) >= 2:
        parts.append(current)
    return parts


def _on_border(point: list[float], west: float, south: float, east: float, north: float) -> bool:
    lon, lat = point
    return (
        abs(lon - west) < 1e-7
        or abs(lon - east) < 1e-7
        or abs(lat - south) < 1e-7
        or abs(lat - north) < 1e-7
    )


def _border_clockwise(
    start: list[float],
    end: list[float],
    west: float,
    south: float,
    east: float,
    north: float,
) -> list[list[float]]:
    """Walk the rectangle clockwise from start until end. North is up."""
    corners = {
        "nw": [west, north],
        "ne": [east, north],
        "se": [east, south],
        "sw": [west, south],
    }

    def edge_and_pos(point: list[float]) -> tuple[str, float]:
        lon, lat = point
        if abs(lat - north) < 1e-7 and west - 1e-7 <= lon <= east + 1e-7:
            return "n", lon
        if abs(lon - east) < 1e-7 and south - 1e-7 <= lat <= north + 1e-7:
            return "e", -lat
        if abs(lat - south) < 1e-7 and west - 1e-7 <= lon <= east + 1e-7:
            return "s", -lon
        return "w", lat

    order = ("n", "e", "s", "w")
    start_edge, start_pos = edge_and_pos(start)
    end_edge, end_pos = edge_and_pos(end)
    path = [start]
    edge = start_edge
    pos = start_pos
    for _ in range(8):
        if edge == end_edge and pos <= end_pos + 1e-9:
            path.append(end)
            return path
        if edge == "n":
            path.append(corners["ne"])
        elif edge == "e":
            path.append(corners["se"])
        elif edge == "s":
            path.append(corners["sw"])
        else:
            path.append(corners["nw"])
        edge = order[(order.index(edge) + 1) % 4]
        pos = -1e9
    path.append(end)
    return path


def sea_ring(
    coastline: list[list[list[float]]],
    west: float,
    south: float,
    east: float,
    north: float,
) -> list[list[float]] | None:
    """One water polygon: OSM coastline keeps the sea on the right, closed on the box."""
    longest: list[list[float]] | None = None
    for chain in _chain_coastline(coastline):
        for part in _clip_polyline(chain, west, south, east, north):
            if not _on_border(part[0], west, south, east, north):
                continue
            if not _on_border(part[-1], west, south, east, north):
                continue
            if longest is None or len(part) > len(longest):
                longest = part
    if longest is None:
        return None
    closing = _border_clockwise(longest[-1], longest[0], west, south, east, north)
    ring = longest + closing[1:]
    if not _near(ring[0], ring[-1]):
        ring.append(ring[0])
    return ring


def _feature(coords: list[list[float]], properties: dict, polygon: bool) -> dict | None:
    if polygon:
        if not _closed(coords):
            return None
        geometry = {"type": "Polygon", "coordinates": [coords]}
    else:
        if len(coords) < 2:
            return None
        geometry = {"type": "LineString", "coordinates": coords}
    return {"type": "Feature", "geometry": geometry, "properties": properties}


def features_from_payload(payload: dict) -> tuple[dict[str, list[dict]], list[list[list[float]]]]:
    buckets: dict[str, list[dict]] = {
        "water": [],
        "parks": [],
        "buildings": [],
        "streets": [],
        "places": [],
    }
    coasts: list[list[list[float]]] = []
    for element in payload.get("elements", []):
        kind = element.get("type")
        tags = element.get("tags") or {}
        if kind == "node":
            place = place_properties(tags)
            if place and "lon" in element and "lat" in element:
                buckets["places"].append(_point(float(element["lon"]), float(element["lat"]), place))
            continue
        if kind != "way":
            continue
        coords = _coords(element)
        if tags.get("natural") == "coastline":
            coasts.append(coords)
            continue
        if tags.get("natural") in WATER_KEYS["natural"] or tags.get("waterway") in WATER_KEYS["waterway"] or tags.get("landuse") in WATER_KEYS["landuse"]:
            feature = _feature(coords, {"kind": tags.get("natural") or tags.get("waterway") or "water"}, polygon=True)
            if feature:
                buckets["water"].append(feature)
        elif tags.get("landuse") in PARK_KEYS["landuse"] or tags.get("leisure") in PARK_KEYS["leisure"]:
            feature = _feature(coords, {"kind": tags.get("landuse") or tags.get("leisure") or "park"}, polygon=True)
            if feature:
                buckets["parks"].append(feature)
        if "building" in tags:
            feature = _feature(coords, {"building": tags.get("building") or "yes"}, polygon=True)
            if feature:
                buckets["buildings"].append(feature)
        if "highway" in tags:
            properties = {"highway": tags.get("highway") or ""}
            street_name = str(tags.get("name") or tags.get("ref") or "").strip()
            if street_name:
                properties["name"] = street_name
            feature = _feature(coords, properties, polygon=False)
            if feature:
                buckets["streets"].append(feature)
        place = place_properties(tags)
        if place:
            center = _centroid(coords)
            if center:
                buckets["places"].append(_point(center[0], center[1], place))
    ring = sea_ring(coasts, WEST, SOUTH, EAST, NORTH)
    if ring:
        buckets["water"].append(
            {
                "type": "Feature",
                "geometry": {"type": "Polygon", "coordinates": [ring]},
                "properties": {"kind": "sea"},
            }
        )
    return buckets, coasts


def export_layers(overpass_path: Path, directory: Path) -> dict[str, Path]:
    payload = json.loads(overpass_path.read_text(encoding="utf-8"))
    buckets, _coasts = features_from_payload(payload)
    directory.mkdir(parents=True, exist_ok=True)
    paths: dict[str, Path] = {}
    for name, features in buckets.items():
        path = directory / f"{name}.geojson"
        path.write_text(
            json.dumps({"type": "FeatureCollection", "features": features}),
            encoding="utf-8",
        )
        paths[name] = path
        print(f"{name}: {len(features)}")
    if len(buckets["streets"]) < 1000:
        raise RuntimeError(
            f"Basemap has only {len(buckets['streets'])} streets. Refusing a sparse extract."
        )
    return paths


def build_pmtiles(layers: dict[str, Path], pmtiles_out: Path) -> None:
    pmtiles_out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        mbtiles = Path(tmp) / "basemap.mbtiles"
        layer_args: list[str] = []
        for name in ("water", "parks", "buildings", "streets"):
            layer_args.extend(["-L", f"{name}:{layers[name]}"])
        layer_args.extend(
            [
                "-L",
                json.dumps(
                    {
                        "file": str(layers["places"]),
                        "layer": "places",
                        "minzoom": 15,
                        "maxzoom": 16,
                    }
                ),
            ]
        )
        subprocess.run(
            [
                "tippecanoe",
                "-o",
                str(mbtiles),
                "-Z11",
                "-z16",
                "--no-tile-size-limit",
                *layer_args,
            ],
            check=True,
        )
        subprocess.run(
            ["pmtiles", "convert", str(mbtiles), str(pmtiles_out)],
            check=True,
        )


def ensure_basemap(root: Path | None = None, *, use_network: bool = True, refresh: bool = False) -> Path:
    root = root or local_pipeline_dir()
    snapshot = basemap_snapshot(root)
    if refresh or not snapshot.is_file():
        if not use_network:
            raise RuntimeError(f"Missing {snapshot}. Build the basemap once with network.")
        fetch_basemap(snapshot)
    assets = basemap_assets_dir(root)
    pmtiles_path = assets / PMTILES_NAME
    with tempfile.TemporaryDirectory() as tmp:
        layers = export_layers(snapshot, Path(tmp))
        build_pmtiles(layers, pmtiles_path)
    return pmtiles_path
