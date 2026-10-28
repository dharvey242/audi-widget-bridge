#!/usr/bin/env python3
"""Register this bridge on YOUR Home Assistant. No third-party dependencies.

No vehicle readings or administrator token are stored. The generated connection
and registration files are credentials: keep them private and out of Git.
"""
import argparse
import getpass
import ipaddress
import json
import os
from pathlib import Path
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import uuid


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Never forward an administrator token to a redirect target.


def validate_url(value, *, webhook=False):
    p = urllib.parse.urlsplit(value)
    if any(x in value for x in ('YOUR_', 'REPLACE_')):
        raise ValueError('Replace example placeholders with your own details.')
    if not p.hostname or p.username or p.password or p.query or p.fragment:
        raise ValueError('Use a complete URL without credentials, query or fragment.')
    private = p.hostname == 'localhost' or p.hostname.endswith('.local')
    try:
        address = ipaddress.ip_address(p.hostname)
        private = address.version == 4 and (address.is_loopback or any(address in ipaddress.ip_network(network)
            for network in ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16')))
    except ValueError:
        pass
    if p.scheme != 'https' and not (p.scheme == 'http' and private):
        raise ValueError('Use HTTPS, or HTTP on a trusted private IPv4/.local address.')
    if webhook and len(p.path) < 20:
        raise ValueError('Invalid webhook URL.')
    return value.rstrip('/')


def private_write(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd, temporary = tempfile.mkstemp(dir=path.parent, prefix=path.name + '.')
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2)
            f.write('\n')
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def request(instance, token, endpoint, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(instance + endpoint, data=data,
        headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
    with urllib.request.build_opener(NoRedirect()).open(req, timeout=30) as response:
        return json.load(response)


def connection_file(instance, registration, device_name):
    candidates = []
    if registration.get('cloudhook_url'):
        candidates.append(registration['cloudhook_url'])
    if registration.get('remote_ui_url'):
        candidates.append(registration['remote_ui_url'].rstrip('/') + '/api/webhook/' + registration['webhook_id'])
    candidates.append(instance + '/api/webhook/' + registration['webhook_id'])
    urls = list(dict.fromkeys(validate_url(value, webhook=True) for value in candidates))
    return {'format': 1, 'device_name': device_name, 'webhook_urls': urls}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', help='YOUR Home Assistant base URL; prompts if omitted.')
    parser.add_argument('--device-name', default='Audi Widget Bridge')
    parser.add_argument('--manufacturer', default='Android')
    parser.add_argument('--model', default='Phone')
    parser.add_argument('--os-version', default='unknown')
    parser.add_argument('--private-dir', type=Path, default=Path('.private'),
                        help='Private state/output directory (default: .private, Git-ignored).')
    args = parser.parse_args()
    instance = validate_url(args.url or os.environ.get('HASS_URL') or input('YOUR Home Assistant URL: ').strip())
    directory = args.private_dir
    state_file = directory / 'registration.json'
    if state_file.exists():
        state = json.loads(state_file.read_text(encoding='utf-8'))
        if state['instance_url'] != instance or state['device_name'] != args.device_name:
            raise ValueError('This private directory belongs to another registration. Choose another --private-dir.')
        if not state.get('registration'):
            raise ValueError('A previous registration was interrupted. Inspect/remove its Mobile App entry in Home Assistant before deleting the pending registration.json and retrying.')
        print('Reusing the saved registration; no new Home Assistant device created.')
    else:
        token = os.environ.get('HASS_TOKEN') or getpass.getpass('YOUR Home Assistant long-lived access token (hidden): ')
        if not token:
            raise ValueError('An access token is required.')
        config = request(instance, token, '/api/config')
        if 'mobile_app' not in config.get('components', []):
            raise ValueError('Enable mobile_app in Home Assistant before registering.')
        state = {'instance_url': instance, 'device_name': args.device_name,
                 'device_id': 'audi-widget-' + str(uuid.uuid4()), 'registration': None}
        # Save a pending checkpoint to avoid accidental duplicate devices on rerun.
        private_write(state_file, state)
        body = {'device_id': state['device_id'], 'app_id': 'local.audi.widgetprobe',
                'app_name': 'Audi Widget Bridge', 'app_version': '0.4',
                'device_name': args.device_name, 'manufacturer': args.manufacturer,
                'model': args.model, 'os_name': 'Android', 'os_version': args.os_version,
                'supports_encryption': False, 'app_data': {}}
        registration = request(instance, token, '/api/mobile_app/registrations', body)
        if not isinstance(registration.get('webhook_id'), str) or not registration['webhook_id']:
            raise ValueError('Registration response had no webhook ID. Inspect the new Mobile App entry before retrying.')
        state['registration'] = registration
        private_write(state_file, state)
        del token
        print('Created your dedicated Home Assistant Mobile App device.')
    output = directory / 'connection.json'
    private_write(output, connection_file(instance, state['registration'], args.device_name))
    print('Connection file written:', output)
    print('Transfer it privately to your phone and import it in Audi Widget Bridge.')
    print('Sensors appear after the first real Audi widget upload. No vehicle data was seeded.')
    print('The Mobile App registration may create a location tracker: disable that unused entity in Home Assistant. This bridge never sends location.')


if __name__ == '__main__':
    try:
        main()
    except urllib.error.HTTPError as error:
        print(f'Home Assistant returned HTTP {error.code}. Check URL/token and Mobile App configuration. No response body or credential was logged.', file=sys.stderr)
        sys.exit(1)
    except urllib.error.URLError:
        print('Could not connect securely to Home Assistant. Check connectivity, URL and certificates.', file=sys.stderr)
        sys.exit(1)
    except (ValueError, OSError, KeyError) as error:
        # ValueError messages above are our own messages; arbitrary I/O errors can contain private paths.
        print(str(error) if isinstance(error, ValueError) else f'Setup failed: {type(error).__name__}. Check the private registration files.', file=sys.stderr)
        sys.exit(1)
