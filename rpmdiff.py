#!/usr/bin/env python
from __future__ import print_function
from pprint import pprint as pp
import os
import time
import sys

import errata_tool


def rpmdiffs_ran(advisory):
    advisory.refresh()
    rpmdiffs = advisory.externalTests(test_type='rpmdiff')

    print("Checking to see if RPM diffs have finished running")
    print("Current RPM Diff status data:")
    pp(rpmdiffs)
    not_finished_diffs = []
    for diff in rpmdiffs:
        if diff['attributes']['status'] in ['QUEUED_FOR_TEST', 'RUNNING']:
            not_finished_diffs.append(diff)

    if not_finished_diffs:
        print("There are {} rpmdiffs QUEUED_FOR_TEST or RUNNING".format(
            len(not_finished_diffs)))
        return False
    else:
        print("All diffs have finished running")
        return True

def rpmdiffs_resolved(advisory):
    advisory.refresh()
    rpmdiffs = advisory.externalTests(test_type='rpmdiff')
    completed_diffs = []
    incomplete_diffs = []

    print("Current RPM Diff status data:")
    pp(rpmdiffs)

    for diff in rpmdiffs:
        if diff['attributes']['status'] in ['INFO', 'WAIVED']:
            completed_diffs.append(diff)
        else:
            incomplete_diffs.append(diff)

    if incomplete_diffs:
        pass
    else:
        print("All RPM diffs have been resolved")
        exit(0)

    for diff in incomplete_diffs:
        url = "https://rpmdiff.engineering.redhat.com/run/{}/".format(
            diff['attributes']['external_id'])
        print("{status} - {nvr} - {url}".format(
            status=diff['attributes']['status'],
            nvr=diff['relationships']['brew_build']['nvr'],
            url=url))
    exit(1)

def usage():
    print("""Usage: {} <command> ADVISORY
""".format(sys.argv[0]))
    print("""Advisory is an ADVISORY id number

commands:
    check-ran - Polls until all rpmdiffs have finished running
    check-resolved - Check if all rpmdiffs have been resovled
        Exits after checking once
""")

if __name__ == '__main__':
    if 'REQUESTS_CA_BUNDLE' not in os.environ:
        os.environ['REQUESTS_CA_BUNDLE'] = '/etc/pki/tls/certs/ca-bundle.crt'

    if len(sys.argv) != 3:
        usage()
        exit (1)
    elif sys.argv[1] == "-h" or sys.argv[1] == "--help":
        usage()
        exit (0)
    else:
        command = sys.argv[1]
        advisory = errata_tool.Erratum(errata_id=sys.argv[2])

    if command == 'check-ran':
        while not rpmdiffs_ran(advisory):
            print("Sleeping 60s and then polling again")
            time.sleep(60)
    elif command == 'check-resolved':
        rpmdiffs_resolved(advisory)
    else:
        print("Invalid command: {}".format(command))
        exit(1)
