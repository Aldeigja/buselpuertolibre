#!/usr/bin/env python3
"""Write local/pipeline/assets/el_puerto.pmtiles from the Overpass snapshot."""

from __future__ import annotations

import argparse

from elpuerto.basemap import ensure_basemap


def main() -> int:
    parser = argparse.ArgumentParser(description="Write the municipal PMTiles basemap.")
    parser.add_argument(
        "--refresh",
        action="store_true",
        help="Download a new Overpass snapshot before tiling.",
    )
    args = parser.parse_args()
    path = ensure_basemap(refresh=args.refresh)
    print(f"Basemap: {path} ({path.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
