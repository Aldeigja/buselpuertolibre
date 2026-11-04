#!/usr/bin/env python3
"""Rebuild transit.db and curation outputs."""

from __future__ import annotations

import argparse
import sys

from elpuerto.build import PipelinePaths, local_pipeline_dir, run_pipeline


def main() -> int:
    parser = argparse.ArgumentParser(description="El Puerto Bus data pipeline")
    parser.add_argument(
        "--offline",
        action="store_true",
        help="Use local snapshots under local/pipeline/data (no network).",
    )
    args = parser.parse_args()
    root = local_pipeline_dir()
    paths = PipelinePaths(root)
    result = run_pipeline(paths, use_network=not args.offline)
    print(f"Stops: {result.stop_count}")
    print(f"Departures (bound clocks): {result.departure_count}")
    print(f"Unlocated stops: {result.unlocated_count}")
    print(f"Unbound timetable columns: {result.unbound_column_count}")
    for warning in result.warnings:
        print(f"WARNING: {warning}", file=sys.stderr)
    print(f"Database: {paths.db_path}")
    print(f"Review map: {paths.review_geojson}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
