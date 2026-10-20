#!/usr/bin/env python3
"""Offline regression tests for screenshots from real API26/API36 captures."""
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

SCRIPT = Path(__file__).with_name("verify-acceptance-frames.py")
spec = importlib.util.spec_from_file_location("qa_frames", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def png(path, *, size=(120, 120), shift=False, outside=False, corrupted=False):
    width, height = size
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            rgb = (25, 45, 80)
            if (20 < x < 50 and 30 < y < 48):
                rgb = (190, 170, 80)
            if shift and ((50 < x < 80 and 30 < y < 48) if not outside else (x < 10 and y < 10)):
                rgb = (215, 60, 25)
            raw.extend(rgb)
    def chunk(name, contents):
        return struct.pack('>I', len(contents)) + name + contents + struct.pack('>I', zlib.crc32(name + contents))
    data = (module.PNG_MAGIC + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))
    path.write_bytes(data[:-4] + b'BAD!' if corrupted else data)


class AcceptanceFramesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.before, self.after = (Path(self.temp.name) / name for name in ('a.png', 'b.png'))

    def test_genuine_visual_change(self):
        png(self.before)
        png(self.after, shift=True)
        report = module.compare(self.before, self.after)
        self.assertTrue(report['passed'])
        self.assertGreater(report['changed_percent'], 0.25)
        self.assertEqual(report['dimensions'], [120, 120])

    def test_identical_frames_rejected(self):
        png(self.before)
        png(self.after)
        with self.assertRaisesRegex(module.EvidenceError, 'static pose'):
            module.compare(self.before, self.after)

    def test_changes_outside_canvas_rejected(self):
        png(self.before)
        png(self.after, shift=True, outside=True)
        with self.assertRaisesRegex(module.EvidenceError, 'static pose'):
            module.compare(self.before, self.after)

    def test_dimension_mismatch_rejected(self):
        png(self.before)
        png(self.after, size=(122, 120))
        with self.assertRaisesRegex(module.EvidenceError, 'dimensions changed'):
            module.compare(self.before, self.after)

    def test_crc_corruption_rejected(self):
        png(self.before)
        png(self.after, shift=True, corrupted=True)
        with self.assertRaisesRegex(module.EvidenceError, 'CRC mismatch'):
            module.compare(self.before, self.after)

    def test_invalid_images_rejected(self):
        self.before.write_bytes(b'no screenshot')
        png(self.after, shift=True)
        with self.assertRaisesRegex(module.EvidenceError, 'not a PNG'):
            module.compare(self.before, self.after)


if __name__ == '__main__':
    unittest.main()
