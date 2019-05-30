# Copyright 2018 Telstra Open Source
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

from flask import Flask, request, jsonify
from service.topology import A_SW_NAME
from docker import DockerClient
import logging


FL_CONTAINER_NAME = "floodlight"
DUMMY_CONTROLLER = "tcp:192.0.2.0:6666"
logger = logging.getLogger()
docker = DockerClient(base_url='unix://var/run/docker.sock')

app = Flask(__name__)
A_sw = None
switches = None


def int_from_str_by_pattern(string, pattern):
    start = string.find(pattern)
    end = string.find(' ', start)
    if end == -1:
        end = None
    return int(string[start + len(pattern):end])


def parse_dump_flows(raw):
    data = raw.split('\n')[1:-3]
    return [{'in_port': int_from_str_by_pattern(x, 'in_port='),
             'out_port': int_from_str_by_pattern(x, 'actions=output:')}
            for x in data]


@app.route('/flows', methods=['GET'])
def get_flows_route():
    flows = A_sw.dump_flows()
    return jsonify(parse_dump_flows(flows))


@app.route('/flows', methods=['POST'])
def post_flows_route():
    mappings = request.get_json()
    A_sw.add_route_flows(mappings)
    return jsonify({'status': 'ok'})


@app.route('/flows', methods=['DELETE'])
def delete_flows_route():
    mappings = request.get_json()
    A_sw.del_route_flows(mappings)
    return jsonify({'status': 'ok'})


@app.route('/ports', methods=['POST'])
def ports_up():
    A_sw.mod_port_state(request.get_json(), "up")
    return jsonify({'status': 'ok'})


@app.route('/ports', methods=['DELETE'])
def ports_down():
    A_sw.mod_port_state(request.get_json(), "down")
    return jsonify({'status': 'ok'})


@app.route('/knockoutswitch', methods=['POST'])
def switch_knock_out():
    body = request.get_json()
    sw = body['name']
    switches[sw].force_update_controller_host(DUMMY_CONTROLLER, batch=False)
    return jsonify({'status': 'ok'})


@app.route("/reviveswitch", methods=['POST'])
def switch_revive():
    body = request.get_json()
    sw = body['name']
    switches[sw].add_controller(batch=False)
    return jsonify({'status': 'ok'})


@app.route('/floodlight/stop', methods=['POST'])
def fl_stop():
    docker.containers.get(FL_CONTAINER_NAME).stop()
    return jsonify({'status': 'ok'})


@app.route('/floodlight/start', methods=['POST'])
def fl_start():
    docker.containers.get(FL_CONTAINER_NAME).start()
    return jsonify({'status': 'ok'})


@app.route('/floodlight/restart', methods=['POST'])
def fl_restart():
    docker.containers.get(FL_CONTAINER_NAME).restart()
    return jsonify({'status': 'ok'})


def init_app(_switches):
    global switches, A_sw
    switches = _switches
    A_sw = switches[A_SW_NAME]
    return app
