import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / 'app/src/main/python/annie_guest.py'
spec = importlib.util.spec_from_file_location('annie_guest', path)
guest = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guest)

class PythonGuestTests(unittest.TestCase):
    def test_real_python_functions_comprehensions_and_math(self):
        self.assertEqual('42', guest.run('import math\ndef main(ctx):\n return sum([n for n in range(7)]) * int(math.sqrt(4))', 'main.py', 'main', '{}'))

    def test_message_and_input(self):
        self.assertEqual('{"type": "text", "text": "HELLO"}', guest.run('def main(ctx):\n return {"type": "text", "text": ctx["text"].upper()}', 'main.py', 'main', '{"text":"hello"}'))

    def test_host_access_and_introspection_rejected(self):
        for source in ['import os', 'import java', 'x = ().__class__', 'x = "{0.__class__}".format(1)', 'from math import __dict__', '_private = 1']:
            with self.subTest(source=source), self.assertRaises(ValueError):
                guest.run(source, 'bad.py', 'main', '{}')
        with self.assertRaises(NameError):
            guest.run('def main(ctx):\n return open("/data/private")', 'bad.py', 'main', '{}')

    def test_guest_state_is_fresh(self):
        source = 'values = []\ndef main(ctx):\n values.append(1)\n return len(values)'
        self.assertEqual('1', guest.run(source, 'main.py', 'main', '{}'))
        self.assertEqual('1', guest.run(source, 'main.py', 'main', '{}'))

    def test_loop_is_bounded(self):
        with self.assertRaises(TimeoutError):
            guest.run('def main(ctx):\n while True: pass', 'loop.py', 'main', '{}')

if __name__ == '__main__':
    unittest.main()
