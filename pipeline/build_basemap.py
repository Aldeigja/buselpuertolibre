#!/usr/bin/env python3
"""Write local/pipeline/assets/el_puerto.pmtiles from the Overpass snapshot."""

from __future__ import annotations

from elpuerto.basemap import ensure_basemap


def main() -> int:
    path = ensure_basemap()
    print(f"Basemap: {path} ({path.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
