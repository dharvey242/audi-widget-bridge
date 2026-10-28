import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('registration', ROOT / 'tools/register_home_assistant.py')
registration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(registration)


class RegistrationTest(unittest.TestCase):
    def test_urls(self):
        for url in ['https://ha.example.invalid', 'http://192.168.50.50:8123', 'http://homeassistant.local:8123']:
            self.assertEqual(registration.validate_url(url), url)
        for url in ['http://public.example.invalid', 'https://user:pass@ha.example.invalid',
                    'https://ha.example.invalid?token=x', 'https://YOUR_HOME_ASSISTANT_HOST', 'file:///etc/passwd']:
            with self.assertRaises(ValueError):
                registration.validate_url(url)
        self.assertIsNone(registration.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://elsewhere.invalid'))

    def test_connection_order_and_no_token(self):
        response = {'webhook_id': 'unit-test-webhook-000',
                    'cloudhook_url': 'https://hooks.example.invalid/unit-test-webhook-000',
                    'remote_ui_url': 'https://remote.example.invalid'}
        result = registration.connection_file('https://ha.example.invalid', response, 'Audi Widget Bridge')
        self.assertEqual(result['format'], 1)
        self.assertEqual(len(result['webhook_urls']), 3)
        self.assertTrue(result['webhook_urls'][0].startswith('https://hooks.'))
        self.assertNotIn('token', result)

    def test_register_once_and_reuse_without_seeding(self):
        with tempfile.TemporaryDirectory() as directory:
            args = ['register_home_assistant.py', '--url', 'https://ha.example.invalid', '--private-dir', directory]
            replies = [{'components': ['mobile_app']}, {'webhook_id': 'unit-test-webhook-000'}]
            with patch('sys.argv', args), patch.dict(os.environ, {'HASS_TOKEN': 'unit-test-token'}), \
                 patch.object(registration, 'request', side_effect=replies) as request, \
                 contextlib.redirect_stdout(io.StringIO()) as output:
                registration.main()
            self.assertEqual([call.args[2] for call in request.call_args_list], ['/api/config', '/api/mobile_app/registrations'])
            payload = request.call_args_list[-1].args[3]
            self.assertFalse(payload['supports_encryption'])
            self.assertNotIn('state', payload)
            state = Path(directory, 'registration.json')
            connection = Path(directory, 'connection.json')
            self.assertNotIn('unit-test-token', state.read_text() + connection.read_text() + output.getvalue())
            if os.name == 'posix':
                self.assertEqual(state.stat().st_mode & 0o777, 0o600)
            with patch('sys.argv', args), patch.object(registration, 'request') as request, contextlib.redirect_stdout(io.StringIO()):
                registration.main()
                request.assert_not_called()

    def test_interrupted_registration_refuses_duplicates(self):
        with tempfile.TemporaryDirectory() as directory:
            registration.private_write(Path(directory, 'registration.json'), {
                'instance_url': 'https://ha.example.invalid', 'device_name': 'Audi Widget Bridge',
                'device_id': 'unit-test-device', 'registration': None})
            with patch('sys.argv', ['tool', '--url', 'https://ha.example.invalid', '--private-dir', directory]), \
                 patch.object(registration, 'request') as request:
                with self.assertRaisesRegex(ValueError, 'interrupted'):
                    registration.main()
                request.assert_not_called()


if __name__ == '__main__':
    unittest.main()
