#!/usr/bin/env python3
"""Report refresh attempts and observations, without treating callbacks as fresh car data."""
import json
import sys

def report(records):
    requests = [r for r in records if r.get('event') == 'refresh_requested']
    print('Refresh attempts:', len(requests))
    for r in records:
        if r.get('event', '').startswith('refresh_'):
            print(json.dumps(r, ensure_ascii=False))
    callbacks = [r for r in records if r.get('event') == 'widget_callback']
    if callbacks:
        print('\nClickable controls in the latest snapshot:')
        for n in callbacks[-1].get('nodes', []):
            if n.get('clickable'):
                print(json.dumps(n, ensure_ascii=False))
    print('\nListener called does not prove a refresh request reached Audi.')
    print('Compare the request time, lock state and Audi contact text. A coincident scheduled update is possible.')

if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python3 inspect_refresh.py exported-log.ndjson')
    with open(sys.argv[1], encoding='utf-8') as f:
        records = [json.loads(line) for line in f if line.strip()]
    report(records)
