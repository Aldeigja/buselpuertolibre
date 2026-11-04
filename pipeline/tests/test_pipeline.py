from __future__ import annotations

import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from elpuerto.build import PipelinePaths, run_pipeline
from elpuerto.route_lines import expected_route_lines
from elpuerto.junta import EXPECTED_2026, EXPECTED_2027_REGIONAL

REPO_ROOT = Path(__file__).resolve().parents[2]
LOCAL_PIPELINE = REPO_ROOT / "local" / "pipeline"
DATA = LOCAL_PIPELINE / "data"


class PipelineIntegrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not (DATA / "Horarios_Invierno_2026.pdf").is_file():
            raise unittest.SkipTest("Committed PDF snapshots missing.")
        if not (DATA / "itinerarios.pdf").is_file():
            raise unittest.SkipTest("Committed itinerary PDF missing.")
        if not (DATA / "junta_holidays.json").is_file():
            raise unittest.SkipTest("Committed Junta snapshot missing.")
        if not (DATA / "osm_bus_stops.json").is_file():
            raise unittest.SkipTest("Committed OSM snapshot missing.")

    def test_offline_pipeline_outputs(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "pipeline"
            self._seed(root)
            paths = PipelinePaths(root)
            result = run_pipeline(paths, use_network=False)
            self.assertGreater(result.stop_count, 100)
            self.assertGreater(result.departure_count, 50)
            self.assertTrue(paths.db_path.is_file())
            self.assertTrue(paths.review_geojson.is_file())
            self.assertTrue(paths.unlocated_csv.is_file())
            self._assert_db_invariants(paths.db_path)
            self._assert_route_lines_match_review(
                paths.db_path, paths.review_geojson
            )

    def test_override_replaces_osm_coordinate(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "pipeline"
            self._seed(root)
            paths = PipelinePaths(root)
            run_pipeline(paths, use_network=False)
            conn = sqlite3.connect(paths.db_path)
            row = conn.execute(
                """
                SELECT stop_id, lat, lon, coord_source
                FROM itinerary_stops
                WHERE coord_source = 'osm_route'
                LIMIT 1
                """
            ).fetchone()
            conn.close()
            self.assertIsNotNone(row)
            stop_id, lat, lon, _ = row
            override = {
                "type": "FeatureCollection",
                "features": [
                    {
                        "type": "Feature",
                        "geometry": {
                            "type": "Point",
                            "coordinates": [-6.23, 36.601],
                        },
                        "properties": {"stop_id": stop_id},
                    }
                ],
            }
            paths.overrides_geojson.write_text(
                json.dumps(override),
                encoding="utf-8",
            )
            run_pipeline(paths, use_network=False)
            conn = sqlite3.connect(paths.db_path)
            lat2, lon2, source = conn.execute(
                "SELECT lat, lon, coord_source FROM itinerary_stops WHERE stop_id = ?",
                (stop_id,),
            ).fetchone()
            conn.close()
            self.assertEqual(source, "override")
            self.assertAlmostEqual(lat2, 36.601)
            self.assertAlmostEqual(lon2, -6.23)

            review = json.loads(paths.review_geojson.read_text(encoding="utf-8"))
            line_features = [
                f
                for f in review["features"]
                if (f.get("geometry") or {}).get("type")
                in {"LineString", "MultiLineString"}
            ]
            self.assertGreater(len(line_features), 0)
            point = next(
                f
                for f in review["features"]
                if f.get("properties", {}).get("stop_id") == stop_id
            )
            self.assertEqual(point["geometry"]["coordinates"], [-6.23, 36.601])

    def _seed(self, root: Path) -> None:
        (root / "data").mkdir(parents=True)
        (root / "curation").mkdir(parents=True)
        for name in (
            "Horarios_Invierno_2026.pdf",
            "itinerarios.pdf",
            "junta_holidays.json",
            "osm_bus_stops.json",
        ):
            (root / "data" / name).write_bytes((DATA / name).read_bytes())
        curation = LOCAL_PIPELINE / "curation"
        for name in (
            "elpuerto_bus_routes_OSM.json",
            "elpuerto_bus_map_osm.geojson",
        ):
            (root / "curation" / name).write_bytes((curation / name).read_bytes())

    def _assert_db_invariants(self, db_path: Path) -> None:
        conn = sqlite3.connect(db_path)
        try:
            lines = {
                row[0]
                for row in conn.execute(
                    "SELECT DISTINCT line FROM itinerary_stops"
                ).fetchall()
            }
            self.assertEqual(lines, set(range(1, 8)))

            stop_id_notnull = conn.execute(
                "SELECT \"notnull\" FROM pragma_table_info('itinerary_stops') WHERE name = 'stop_id'"
            ).fetchone()[0]
            self.assertEqual(
                stop_id_notnull,
                1,
                "stop_id must be NOT NULL so Room accepts the packaged database",
            )

            ret_departures = conn.execute(
                "SELECT COUNT(*) FROM departures WHERE column_label LIKE '%Ret%'"
            ).fetchone()[0]
            self.assertEqual(ret_departures, 0)

            bad_coords = conn.execute(
                """
                SELECT COUNT(*) FROM itinerary_stops
                WHERE lat IS NOT NULL
                  AND coord_source NOT IN ('osm', 'osm_route', 'osm_place', 'override')
                """
            ).fetchone()[0]
            self.assertEqual(bad_coords, 0)

            holidays = {
                row[0]
                for row in conn.execute(
                    "SELECT date_iso FROM holidays"
                ).fetchall()
            }
            self.assertTrue(EXPECTED_2026 <= holidays)
            self.assertTrue(EXPECTED_2027_REGIONAL <= holidays)

            season = dict(
                conn.execute("SELECT key, value FROM meta").fetchall()
            )
            self.assertEqual(season["season_start"], "2026-08-31")
            self.assertEqual(season["season_end"], "2027-08-30")

            self._assert_bound(
                conn, 3, "VALDELAGRANA", "Colegio Público de Valdelagrana"
            )
            self._assert_bound(conn, 3, "T. MARÍTIMA", "Terminal Marítima")
            self._assert_bound(
                conn, 3, "EE.FF", "Estación de Ferrocarril - Plaza Estación"
            )
            station_seq = conn.execute(
                """
                SELECT DISTINCT s.sequence FROM departures d
                JOIN itinerary_stops s ON s.stop_id = d.bound_stop_id
                WHERE d.line = 3 AND d.column_label = 'EE.FF'
                """
            ).fetchall()
            self.assertEqual(station_seq, [(22,)])
            self._assert_bound(conn, 5, "HIJUELAS", "Hijuela del Tío Prieto")
            self._assert_bound(conn, 4, "CREVILLET", "Av. Constitución")
            self._assert_bound(
                conn, 7, "AMB. ZONA NORTE", "Centro de Salud Pinillo Chico"
            )
            self._assert_bound(conn, 7, "BDA. LAS NIEVES", "Las Nieves")
            milagros = self._bound_ids(conn, 7, "LOS MILAGROS")
            self.assertEqual(len(milagros), 1)
            self.assertIn("inbound", milagros[0][0])
            self.assertEqual(milagros[0][1], "Ronda Ferrocarril - Los Milagros")

            terminal_720 = conn.execute(
                """
                SELECT s.stop_name FROM departures d
                JOIN itinerary_stops s ON s.stop_id = d.bound_stop_id
                WHERE d.line = 3 AND d.service_day = 'laborables'
                  AND d.column_label = 'T. MARÍTIMA'
                  AND d.seconds_since_midnight = ?
                """,
                (7 * 3600 + 20 * 60,),
            ).fetchall()
            self.assertIn(("Terminal Marítima",), terminal_720)
            valdelagrana_735 = conn.execute(
                """
                SELECT s.stop_name FROM departures d
                JOIN itinerary_stops s ON s.stop_id = d.bound_stop_id
                WHERE d.line = 3 AND d.service_day = 'laborables'
                  AND d.column_label = 'VALDELAGRANA'
                  AND d.seconds_since_midnight = ?
                """,
                (7 * 3600 + 35 * 60,),
            ).fetchall()
            self.assertIn(("Colegio Público de Valdelagrana",), valdelagrana_735)

            cantarranas = conn.execute(
                """
                SELECT d.seconds_since_midnight, s.stop_name, s.coord_source, s.stop_id
                FROM departures d
                JOIN itinerary_stops s ON s.stop_id = d.bound_stop_id
                WHERE d.line = 6 AND d.column_label = 'Cantarranas'
                ORDER BY d.seconds_since_midnight
                """
            ).fetchall()
            self.assertEqual(
                [row[0] for row in cantarranas],
                [7 * 3600 + 30 * 60, 8 * 3600 + 8 * 60, 14 * 3600 + 28 * 60],
            )
            self.assertTrue(
                all(
                    row[1] == "Cantarranas"
                    and row[2] == "osm_place"
                    and row[3] == "L6_detour_11125177414"
                    for row in cantarranas
                )
            )
            bartolome = conn.execute(
                """
                SELECT DISTINCT s.sequence FROM departures d
                JOIN itinerary_stops s ON s.stop_id = d.bound_stop_id
                WHERE d.line = 4 AND d.column_label = 'SAN BARTOLOMÉ'
                """
            ).fetchall()
            self.assertEqual(bartolome, [(41,)])
            unbound = {
                (row[0], row[1])
                for row in conn.execute(
                    "SELECT line, column_label FROM unbound_columns"
                )
            }
            self.assertEqual(unbound, set())
            for line in (1, 2):
                for day in ("laborables", "sabados", "festivos"):
                    labels = {
                        row[0]
                        for row in conn.execute(
                            """
                            SELECT DISTINCT column_label FROM departures
                            WHERE line = ? AND service_day = ?
                            """,
                            (line, day),
                        )
                    }
                    self.assertEqual(
                        labels,
                        {"C.C. El Paseo", "Esc. Acuña"},
                        f"L{line} {day}",
                    )
        finally:
            conn.close()

    def _bound_ids(
        self, conn: sqlite3.Connection, line: int, label: str
    ) -> list[tuple[str, str]]:
        return conn.execute(
            """
            SELECT DISTINCT s.stop_id, s.stop_name
            FROM departures d
            JOIN itinerary_stops s ON s.stop_id = d.bound_stop_id
            WHERE d.line = ? AND d.column_label = ?
            """,
            (line, label),
        ).fetchall()

    def _assert_route_lines_match_review(
        self, db_path: Path, review_path: Path
    ) -> None:
        expected = {
            (row.line, row.direction): (
                row.geometry_type,
                row.coordinates_json,
            )
            for row in expected_route_lines(review_path)
        }
        conn = sqlite3.connect(db_path)
        try:
            rows = conn.execute(
                """
                SELECT line, direction, geometry_type, coordinates_json
                FROM route_lines ORDER BY line, direction
                """
            ).fetchall()
        finally:
            conn.close()
        self.assertEqual(len(rows), 9)
        for line, direction, geometry_type, coordinates_json in rows:
            key = (line, direction)
            self.assertIn(key, expected, f"missing review geometry for L{line} {direction}")
            exp_type, exp_coords = expected[key]
            self.assertEqual(geometry_type, exp_type)
            self.assertEqual(coordinates_json, exp_coords)
        for line in range(1, 6):
            dirs = {d for ln, d in expected if ln == line}
            self.assertEqual(dirs, {"outbound"})
        for line in (6, 7):
            dirs = {d for ln, d in expected if ln == line}
            self.assertEqual(dirs, {"outbound", "inbound"})

    def _assert_bound(
        self,
        conn: sqlite3.Connection,
        line: int,
        label: str,
        stop_name: str,
    ) -> None:
        rows = self._bound_ids(conn, line, label)
        self.assertEqual([name for _stop_id, name in rows], [stop_name], label)


if __name__ == "__main__":
    unittest.main()
