# -*- coding:utf-8 -*-

import pprint
import random
import json

from topologylistener import message_utils


send_idx = 0


def mock_send(payload, correlation_id):
    global send_idx

    print('Send request #{}'.format(send_idx))
    print('Send correlation id: {}'.format(correlation_id))

    send_idx += 1
    encoded = json.dumps(payload, indent=4)
    print(encoded)
    print('\n')


message_utils.send_cache_message = mock_send


def main():
    switch_set = []
    isl_set = []
    flow_set = []

    payload_dummy = []
    for dummy_size in (20, 32, 64, 128, 512, 1024):
        desc = ' {} '.format(dummy_size)
        dummy = '*' * dummy_size
        dummy = dummy[:2] + desc + dummy[2 + len(desc):]
        dummy = dummy[:dummy_size]
        payload_dummy.append(dummy)

    for kind, dest in (
            ('switch', switch_set),
            ('isl', isl_set),
            ('flow', flow_set)):

        available_chunks = payload_dummy[:]
        random.shuffle(available_chunks)
        for payload in available_chunks:
            dest.append({'kind': kind, 'payload': payload})

    message_utils.send_network_dump(
        'correlation', switch_set, isl_set, flow_set, size_limit=10000)


if __name__ == '__main__':
    main()
