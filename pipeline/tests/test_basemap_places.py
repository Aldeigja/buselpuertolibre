from __future__ import annotations

import unittest

from elpuerto.basemap import features_from_payload, place_properties


class PlacePropertiesTest(unittest.TestCase):
    def test_named_pharmacy_is_a_place(self) -> None:
        props = place_properties({"name": "Farmacia Jesús López Palomino", "amenity": "pharmacy"})
        self.assertEqual(props["kind"], "pharmacy")
        self.assertEqual(props["name"], "Farmacia Jesús López Palomino")

    def test_bench_and_unnamed_shop_are_skipped(self) -> None:
        self.assertIsNone(place_properties({"name": "Seat", "amenity": "bench"}))
        self.assertIsNone(place_properties({"shop": "bakery"}))

    def test_street_keeps_its_name_and_a_shop_way_gets_a_point(self) -> None:
        payload = {
            "elements": [
                {
                    "type": "way",
                    "tags": {"highway": "residential", "name": "Calle del Ruiseñor"},
                    "geometry": [
                        {"lon": -6.23, "lat": 36.59},
                        {"lon": -6.22, "lat": 36.59},
                    ],
                },
                {
                    "type": "node",
                    "lon": -6.237,
                    "lat": 36.593,
                    "tags": {"name": "Banco de Andalucía", "amenity": "bank"},
                },
            ]
        }
        buckets, _coasts = features_from_payload(payload)
        self.assertEqual(buckets["streets"][0]["properties"]["name"], "Calle del Ruiseñor")
        self.assertEqual(buckets["places"][0]["properties"]["name"], "Banco de Andalucía")
        self.assertEqual(buckets["places"][0]["geometry"]["type"], "Point")


if __name__ == "__main__":
    unittest.main()
