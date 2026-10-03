#!/usr/bin/env python3
"""Reject stale branch heads and later failing or incomplete acceptance runs."""
import argparse
import json
from pathlib import Path
import subprocess
import urllib.parse


def gh_json(endpoint):
    result = subprocess.run(['gh', 'api', endpoint], check=True, capture_output=True, text=True)
    return json.loads(result.stdout)


def validate(record, head, runs):
    if head != record['head']:
        raise ValueError('Branch moved; audit the new head before release.')
    relevant = [r for r in runs if r['name'] == record['acceptance_workflow']]
    if not relevant:
        raise ValueError('No current relevant acceptance run was found.')
    latest = max(relevant, key=lambda r: (r['run_number'], r.get('run_attempt', 1)))
    if latest['head_sha'] != head or latest['status'] != 'completed' or latest['conclusion'] != 'success':
        raise ValueError('Latest relevant acceptance is not successful at current HEAD.')
    if latest['id'] != record['acceptance_run_id']:
        raise ValueError('Acceptance changed; update the audited record after reviewing evidence.')
    return latest['id']


def main():
    records = json.loads(Path(__file__).with_name('release-identities.json').read_text())
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', choices=records, required=True)
    args = parser.parse_args()
    record = records[args.app]
    branch = urllib.parse.quote(record['branch'], safe='')
    head = gh_json(f'repos/Tomex777/Build/branches/{branch}')['commit']['sha']
    runs = gh_json(f'repos/Tomex777/Build/actions/runs?branch={branch}&per_page=100')['workflow_runs']
    run_id = validate(record, head, runs)
    print(f'Current acceptance verified: {args.app} {head} run {run_id}')


if __name__ == '__main__':
    main()
