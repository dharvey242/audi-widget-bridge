#!/usr/bin/env python3
"""Summarise an exported probe log; never infer vehicle freshness from receipt time."""
import json
import sys
from collections import Counter

def summarise(records):
    callbacks = [r for r in records if r.get('event') == 'widget_callback']
    locked = [r for r in callbacks if r.get('phase') == 'live_callback'
              and r.get('deviceLocked') is True and r.get('screenInteractive') is False]
    changed = [r for r in locked if r.get('changedSincePrevious') is True
               and r.get('remoteViewsPresent') is True and not r.get('extractionError')]
    return callbacks, locked, changed

def main():
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python3 inspect_log.py exported-log.ndjson')
    records = []
    with open(sys.argv[1], encoding='utf-8') as f:
        for number, line in enumerate(f, 1):
            if line.strip():
                try:
                    records.append(json.loads(line))
                except json.JSONDecodeError as exc:
                    raise SystemExit(f'Invalid JSON on line {number}: {exc}')
    callbacks, locked, changed = summarise(records)
    print('Events:', dict(Counter(r.get('event') for r in records)))
    print('Widget callbacks:', len(callbacks))
    print('Post-startup callbacks while device locked and screen off:', len(locked))
    print('Those with changed extracted content:', len(changed))
    for r in changed:
        print('\n', r['receivedAt'], r.get('provider'), 'session', r.get('session'))
        for n in r.get('nodes', []):
            if n.get('visibleInLayout'):
                print(' ', n.get('text', ''), n.get('description', ''))
    print('\nThese are delivery observations, not proof of fresh vehicle data.')
    print('Inspect the Audi timestamp and charge/range text. CONTROL DATA is synthetic.')

if __name__ == '__main__':
    main()
