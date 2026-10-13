"""Offline tests for NASA Trek georeferencing and tile indices."""
import math
import unittest
from prepare_nasa_trek_imagery import (
    SITE, HALF_EXTENT_KM, geographic_point, wmts_pixel,
    required_tile_indices,
)

class TrekGeoreferencingTest(unittest.TestCase):
    def test_sites_have_geographic_center(self):
        for world, site in SITE.items():
            lat, lon = geographic_point(site, 0, 0)
            self.assertAlmostEqual(lat, site["lat"])
            self.assertAlmostEqual(lon, site["lon"])
            self.assertLess(abs(lat), 85)

    def test_patch_axes_east_and_north(self):
        for site in SITE.values():
            e_lat, e_lon = geographic_point(site, HALF_EXTENT_KM, 0)
            n_lat, n_lon = geographic_point(site, 0, HALF_EXTENT_KM)
            self.assertAlmostEqual(e_lat, site["lat"])
            self.assertGreater(e_lon, site["lon"])
            self.assertGreater(n_lat, site["lat"])
            self.assertAlmostEqual(n_lon, site["lon"])

    def test_world_coverage_maps_to_valid_tiles(self):
        for site in SITE.values():
            columns, rows = required_tile_indices(site)
            self.assertTrue(0 < len(columns) <= 8)
            self.assertTrue(0 < len(rows) <= 8)
            for x in columns:
                self.assertTrue(0 <= x < 2**(site["zoom"] + 1))
            for y in rows:
                self.assertTrue(0 <= y < 2**site["zoom"])

    def test_mosaic_px_matches_coordinates(self):
        for site in SITE.values():
            lat, lon = geographic_point(site, HALF_EXTENT_KM, HALF_EXTENT_KM)
            east, north = wmts_pixel(site["zoom"], lon, lat)
            centerE, centerN = wmts_pixel(site["zoom"], site["lon"], site["lat"])
            self.assertGreater(east, centerE)
            self.assertLess(north, centerN)

if __name__ == "__main__":
    unittest.main()
