import unittest
from check_current_acceptance import validate


class AcceptanceGateTests(unittest.TestCase):
    def setUp(self):
        self.record = dict(head='current', acceptance_workflow='Product', acceptance_run_id=100)
        self.green = dict(id=100, run_number=100, name='Product', head_sha='current',
                          status='completed', conclusion='success')

    def test_current_green_accepts(self):
        self.assertEqual(validate(self.record, 'current', [self.green]), 100)

    def test_later_failure_overrides_green(self):
        later = dict(self.green, id=101, run_number=101, conclusion='failure')
        with self.assertRaises(ValueError):
            validate(self.record, 'current', [self.green, later])

    def test_unfinished_rerun_cannot_release(self):
        rerun = dict(self.green, run_attempt=2, status='in_progress', conclusion=None)
        with self.assertRaises(ValueError):
            validate(self.record, 'current', [rerun])

    def test_snapshot_does_not_hide_failed_product(self):
        failed = dict(self.green, conclusion='failure')
        snapshot = dict(self.green, name='Source snapshot', run_number=200)
        with self.assertRaises(ValueError):
            validate(self.record, 'current', [failed, snapshot])

    def test_moved_head_rejects(self):
        with self.assertRaises(ValueError):
            validate(self.record, 'new', [self.green])


if __name__ == '__main__':
    unittest.main()
