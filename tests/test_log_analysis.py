import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('inspect_log', Path(__file__).resolve().parents[1] / 'tools/inspect_log.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class LogAnalysisTest(unittest.TestCase):
    def test_only_changed_locked_successful_callbacks_qualify(self):
        base = dict(event='widget_callback', phase='live_callback', deviceLocked=True,
                    screenInteractive=False, changedSincePrevious=True, remoteViewsPresent=True)
        records = [base, dict(base, phase='startup_snapshot'), dict(base, deviceLocked=False),
                   dict(base, screenInteractive=True), dict(base, changedSincePrevious=False),
                   dict(base, extractionError='failed'), dict(base, remoteViewsPresent=False)]
        callbacks, locked, changed = module.summarise(records)
        self.assertEqual(len(callbacks), 7)
        self.assertEqual(len(locked), 4)
        self.assertEqual(changed, [base])
