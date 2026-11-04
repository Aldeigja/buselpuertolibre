from __future__ import annotations

import json
import unittest
from pathlib import Path

import extract_osm_bus as osm_bus

parse_bus_routes = osm_bus.parse_bus_routes
parse_line_ref = osm_bus.parse_line_ref
routes_to_geojson = osm_bus.routes_to_geojson
routes_to_transit_document = osm_bus.routes_to_transit_document
stitch_way_geometries = osm_bus.stitch_way_geometries

FIXTURE = Path(__file__).resolve().parent / "fixtures" / "overpass_routes_minimal.json"


class ParseLineRefTest(unittest.TestCase):
    def test_variants(self) -> None:
        self.assertEqual(parse_line_ref("1"), "1")
        self.assertEqual(parse_line_ref("L1"), "1")
        self.assertEqual(parse_line_ref("L-7"), "7")
        self.assertEqual(parse_line_ref("Línea 3"), "3")
        self.assertIsNone(parse_line_ref("M-040"))
        self.assertIsNone(parse_line_ref("8"))


class StitchGeometryTest(unittest.TestCase):
    def test_merges_touching_segments(self) -> None:
        segments = [
            [(0.0, 0.0), (1.0, 1.0)],
            [(1.0, 1.0), (2.0, 2.0)],
        ]
        geom = stitch_way_geometries(segments)
        self.assertEqual(geom["type"], "LineString")
        self.assertEqual(len(geom["coordinates"]), 3)

    def test_disjoint_becomes_multilinestring(self) -> None:
        segments = [
            [(0.0, 0.0), (1.0, 1.0)],
            [(5.0, 5.0), (6.0, 6.0)],
        ]
        geom = stitch_way_geometries(segments)
        self.assertEqual(geom["type"], "MultiLineString")
        self.assertEqual(len(geom["coordinates"]), 2)


class ParseRoutesFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not FIXTURE.is_file():
            raise unittest.SkipTest("Minimal Overpass fixture missing.")
        cls.payload = json.loads(FIXTURE.read_text(encoding="utf-8"))

    def test_parses_two_directions_for_line_six(self) -> None:
        routes = parse_bus_routes(self.payload)
        line_six = [r for r in routes if r.line_ref == "6"]
        self.assertEqual(len(line_six), 2)
        directions = {r.direction for r in line_six}
        self.assertEqual(directions, {"outbound", "inbound"})
        self.assertEqual(line_six[0].stops[0].stop_sequence, 1)

    def test_outputs_contain_expected_keys(self) -> None:
        routes = parse_bus_routes(self.payload)
        doc = routes_to_transit_document(routes)
        self.assertEqual(doc["municipality"], "El Puerto de Santa María")
        self.assertIn("updated_at", doc)
        self.assertGreaterEqual(len(doc["routes"]), 1)

        geo = routes_to_geojson(routes)
        self.assertEqual(geo["type"], "FeatureCollection")
        kinds = {f["geometry"]["type"] for f in geo["features"]}
        self.assertIn("Point", kinds)
        self.assertIn("LineString", kinds)


if __name__ == "__main__":
    unittest.main()
