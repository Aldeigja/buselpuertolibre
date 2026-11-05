from __future__ import annotations

import json
import unittest
from pathlib import Path

import extract_osm_bus as osm_bus
from elpuerto.osm_routes import serve_line1_at_plaza_noria, without_teleported_repeats

parse_bus_routes = osm_bus.parse_bus_routes
parse_line_ref = osm_bus.parse_line_ref
routes_to_geojson = osm_bus.routes_to_geojson
routes_to_transit_document = osm_bus.routes_to_transit_document
stitch_way_geometries = osm_bus.stitch_way_geometries

FIXTURE = Path(__file__).resolve().parent / "fixtures" / "overpass_routes_minimal.json"


class TeleportedRepeatTest(unittest.TestCase):
    def test_drops_the_glorieta_jump_and_keeps_a_real_return(self) -> None:
        glorieta = {"name": "Glorieta de Fuentebravía", "role": "platform", "name_source": "own", "lat": 36.61200, "lon": -6.28725}
        terminal = {"name": "Terminal Marítima", "role": "platform", "name_source": "own", "lat": 36.59378, "lon": -6.22704}
        micaela = {"name": "Micaela Aramburu - Las Galeras", "role": "platform", "name_source": "own", "lat": 36.59410, "lon": -6.22740}
        jumped = {"name": "Glorieta de Fuentebravía", "role": "stop", "name_source": "nearby_platform", "lat": 36.61201, "lon": -6.28717}
        returned = {"name": "Poblado de Doña Blanca", "role": "platform", "name_source": "own", "lat": 36.62, "lon": -6.25}
        kept = without_teleported_repeats([glorieta, terminal, micaela, jumped, returned])
        self.assertEqual([stop["name"] for stop in kept], [
            "Glorieta de Fuentebravía",
            "Terminal Marítima",
            "Micaela Aramburu - Las Galeras",
            "Poblado de Doña Blanca",
        ])


class Line1NoriaTest(unittest.TestCase):
    def test_moves_line1_onto_plaza_noria_and_leaves_the_other_lines(self) -> None:
        golondrina = {
            "stop_sequence": 22,
            "sequence_index": 21,
            "osm_node_id": 13392814811,
            "name": "Pza. Noria - Golondrina",
            "lat": 36.593613,
            "lon": -6.2355833,
        }
        noria = {
            "stop_sequence": 24,
            "sequence_index": 23,
            "osm_node_id": 13406610340,
            "name": "Pza. Noria",
            "lat": 36.5938536,
            "lon": -6.2359652,
        }
        routes = [
            {"line_ref": "1", "stops": [dict(golondrina)]},
            {"line_ref": "2", "stops": [dict(golondrina, stop_sequence=15)]},
            {"line_ref": "4", "stops": [dict(golondrina, stop_sequence=19), dict(noria)]},
        ]
        serve_line1_at_plaza_noria(routes)
        line1 = routes[0]["stops"][0]
        self.assertEqual(line1["osm_node_id"], 13406610340)
        self.assertEqual(line1["name"], "Pza. Noria")
        self.assertEqual(line1["stop_sequence"], 22)
        self.assertEqual(routes[1]["stops"][0]["osm_node_id"], 13392814811)
        self.assertEqual(routes[2]["stops"][0]["name"], "Pza. Noria - Golondrina")
        self.assertEqual(routes[2]["stops"][1]["stop_sequence"], 24)


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
