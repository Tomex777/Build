import tempfile
import struct
import unittest
from pathlib import Path
from check_surface_control_evidence import dimensions, validate


class SurfaceControlEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        # A tiny but correctly structured synthetic IHDR buffer exercises only
        # the static evidence parser, *never* substitutes for a device screenshot.
        self.png = b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" + struct.pack(">II", 2400, 1080)
        self.png += b"\x08\x02\x00\x00\x00" + b"0" * 12000
        (self.root / "controls-two-thumb-moon.png").write_bytes(self.png)
        (self.root / "controls-qa-device.txt").write_text(
            "sdk=36\ncompletedFrames=12\nelapsedMs=7000\nprocessPssKiB=10000\n"
            "touchEvents=19\ntouchDispatchMedianMs=4.2\ntouchDispatchMaxMs=11.5\n"
            "method=Android GLES runtime; includes waits; not a benchmark\n"
        )
        (self.root / "controls-meminfo-active.txt").write_text("TOTAL PSS: 100000\n")
        (self.root / "controls-gfxinfo-active.txt").write_text("Profile data in ms\n")

    def test_valid(self):
        self.assertEqual(dimensions(self.root / "controls-two-thumb-moon.png"), (2400, 1080))
        validate(self.root, 36)

    def test_wrong_sdk_rejected(self):
        with self.assertRaisesRegex(ValueError, "Wrong SDK"):
            validate(self.root, 26)

    def test_empty_screenshot_rejected(self):
        (self.root / "controls-two-thumb-moon.png").write_bytes(b"\x89PNG")
        with self.assertRaises(ValueError):
            validate(self.root, 36)

    def test_missing_live_diagnostics_rejected(self):
        (self.root / "controls-meminfo-active.txt").unlink()
        with self.assertRaises(FileNotFoundError):
            validate(self.root, 36)

    def test_stale_process_rejected(self):
        (self.root / "controls-meminfo-active.txt").write_text("No process found for com.night.endless")
        with self.assertRaises(ValueError):
            validate(self.root, 36)


if __name__ == "__main__":
    unittest.main()
