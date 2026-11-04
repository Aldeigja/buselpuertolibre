from __future__ import annotations

import unittest

from elpuerto.basemap import sea_ring


def _inside(ring: list[list[float]], lon: float, lat: float) -> bool:
    contained = False
    for start, end in zip(ring, ring[1:]):
        crosses = (start[1] > lat) != (end[1] > lat)
        if crosses and lon < (end[0] - start[0]) * (lat - start[1]) / (end[1] - start[1]) + start[0]:
            contained = not contained
    return contained


class SeaRingTest(unittest.TestCase):
    def test_eastbound_coast_fills_the_south(self) -> None:
        ring = sea_ring([[[0.0, 5.0], [10.0, 5.0]]], 0.0, 0.0, 10.0, 10.0)
        self.assertIsNotNone(ring)
        assert ring is not None
        self.assertTrue(_inside(ring, 5.0, 2.0))
        self.assertFalse(_inside(ring, 5.0, 8.0))


if __name__ == "__main__":
    unittest.main()
