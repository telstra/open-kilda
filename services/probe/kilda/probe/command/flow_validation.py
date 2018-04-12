# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

import click
import requests
import sys
import uuid


@click.command(name='validate-flows-command')
@click.pass_obj
def validate_flows(ctx):
    print (ctx)
    northbound_endpoint = ctx.nb_endpoint
    user = ctx.nb_user
    password = ctx.nb_pass
    correlation_id = str(uuid.uuid4())

    sys.stdout.write('START VALIDATING FLOW\n')
    flows = requests.get(northbound_endpoint + '/api/v1/flows',
                         auth=(user, password),
                         headers={'correlation_id': correlation_id}).json()
    flow_ids = list(map(lambda x: x['flowid'], flows))

    correct_flows = []
    incorrect_flows = []
    for index, flow_id in enumerate(flow_ids):
        sys.stdout.write('>> Current progress: {}/{}\n'.format(index,
                                                               len(flow_ids)))

        path = northbound_endpoint + '/api/v1/flows/{}/validate'.format(flow_id)
        response = requests.get(path,
                                auth=(user, password),
                                headers={'correlation_id':
                                         correlation_id + flow_id})
        result = response.json()

        valid = result[0]['as_expected'] and result[1]['as_expected']
        flow_info = {
            'FLOW_ID': flow_id,
            'FORWARD_VALID': result[0]['as_expected'],
            'REVERSE_VALID': result[1]['as_expected']
        }

        if valid:
            correct_flows.append(flow_info)
        else:
            incorrect_flows.append(flow_info)

    sys.stdout.write('ALL FLOWS ARE PROCESSED\n')
    print_results(correct_flows, incorrect_flows)


def print_results(correct_flows, incorrect_flows):
    sys.stdout.write('>> VALID FLOWS:\n')
    for flow in correct_flows:
        sys.stdout.write('> {}\n'.format(flow))

    sys.stdout.write('>> INVALID FLOWS:\n')
    for flow in incorrect_flows:
        sys.stdout.write('> {}\n'.format(flow))

    sys.stdout.write('>>> TOTAL VALID FLOWS: {}\n'.format(len(correct_flows)))
    sys.stdout.write('>>> TOTAL INVALID FLOWS: {}\n'.format(len(incorrect_flows)))
