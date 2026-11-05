# El Puerto Bus data pipeline (milestone 1)

Builds the local database and review files from sources that stay on this machine. `local/` is gitignored. It holds the timetable PDFs, OSM extracts, generated maps, and `transit.db`.

- `local/pipeline/data/Horarios_Invierno_2026.pdf` (clocks)
- `local/pipeline/data/itinerarios.pdf` (stop names and column order)
- `local/pipeline/data/junta_holidays.json` (build-time holiday snapshot)
- `local/pipeline/data/osm_bus_stops.json` (named OSM stops inside the municipal boundary)
- `local/pipeline/curation/stops_overrides.geojson` (optional manual points, not overwritten)

## Setup

```bash
python3 -m venv .venv
.venv/bin/python3 -m pip install -r pipeline/requirements.txt
```

## Run

From the repository root:

```bash
PYTHONPATH=pipeline .venv/bin/python3 pipeline/run.py
```

Use `--offline` when the PDFs and JSON snapshots under `local/pipeline/data/` are already present.

## OSM urban routes (lines 1–7)

Fetch route relations, stop order, and geometry from Overpass:

```bash
PYTHONPATH=pipeline .venv/bin/python3 pipeline/extract_osm_bus.py
```

Use `--cached data/overpass_raw.json` for offline runs. That path is relative to `local/pipeline/`. Set `OVERPASS_URL` to override the default Overpass endpoint. Writes `local/pipeline/data/report.txt` and backs up prior curation files as `*.bak`.

| Path | Role |
| --- | --- |
| `local/pipeline/curation/elpuerto_bus_routes_OSM.json` | Normalized routes and ordered stops |
| `local/pipeline/curation/elpuerto_bus_map_osm.geojson` | Stop points and route polylines |

## Outputs

These stay in `local/` and are not committed.

| Path | Role |
| --- | --- |
| `local/pipeline/output/transit.db` | SQLite snapshot (stops, clocks, holidays, and `route_lines` from the review map) |
| `local/pipeline/assets/el_puerto.pmtiles` | OpenStreetMap streets, parks, water, buildings, street names, and named places (`pipeline/build_basemap.py --refresh`, needs network once, plus `tippecanoe` and `pmtiles`) |
| `local/pipeline/curation/stops_review.geojson` | Regenerated points and route lines |
| `local/pipeline/curation/stops_unlocated.csv` | Stops without coordinates |
| `local/pipeline/curation/stops_overrides.geojson` | Aligned points (edit in QGIS or geojson.io) |

## Tests

```bash
PYTHONPATH=pipeline .venv/bin/python3 -m unittest discover -s pipeline/tests -p 'test_*.py'
```
