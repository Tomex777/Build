"""Offline tests for NASA Trek georeferencing and tile indices."""
import math
import unittest
from PIL import Image
from prepare_nasa_trek_imagery import (
    SITE, HALF_EXTENT_KM, geographic_point, wmts_pixel,
    required_tile_indices, bilinear_rgb,
)

class TrekGeoreferencingTest(unittest.TestCase):
    def test_wmts_resampling_interpolates_real_pixel_values(self):
        # Each NASA source sample lives at its half-pixel center.
        mosaic = Image.new("RGB", (2, 2))
        mosaic.putpixel((0, 0), (20, 40, 60))
        mosaic.putpixel((1, 0), (100, 120, 140))
        mosaic.putpixel((0, 1), (180, 200, 220))
        mosaic.putpixel((1, 1), (220, 240, 255))
        src = mosaic.load()
        self.assertEqual((20, 40, 60), bilinear_rgb(src, 2, 2, .5, .5))
        self.assertEqual((100, 120, 140), bilinear_rgb(src, 2, 2, 1.5, .5))
        self.assertEqual((130, 150, 169), bilinear_rgb(src, 2, 2, 1.0, 1.0))
        self.assertEqual((20, 40, 60), bilinear_rgb(src, 2, 2, -10, -10))
        self.assertEqual((220, 240, 255), bilinear_rgb(src, 2, 2, 12, 12))

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
