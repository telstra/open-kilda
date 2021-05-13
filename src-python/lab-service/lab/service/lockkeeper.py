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

from flask import Flask, request, jsonify, Response
from service.topology import A_SW_NAME
from docker import DockerClient
from collections import defaultdict
import logging

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
    data = raw.split('\n')[1:-1]
    return [{'in_port': int_from_str_by_pattern(x, 'in_port='),
             'out_port': int_from_str_by_pattern(x, 'actions=output:')}
            for x in data]


def execute_commands_in_container(commands, container_name):
    c = docker.from_env().containers.get(container_name)
    for command in commands:
        c.exec_run(command)


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
    docker.containers.get(request.get_json().get('containerName')).stop()
    return jsonify({'status': 'ok'})


@app.route('/floodlight/start', methods=['POST'])
def fl_start():
    docker.containers.get(request.get_json().get('containerName')).start()
    return jsonify({'status': 'ok'})


@app.route('/floodlight/restart', methods=['POST'])
def fl_restart():
    docker.containers.get(request.get_json().get('containerName')).restart()
    return jsonify({'status': 'ok'})


@app.route("/set-controller", methods=['POST'])
def set_controller():
    body = request.get_json()
    sw = body['name']
    controller = body['controller']
    switches[sw].set_controller(controller)
    return jsonify({'status': 'ok'})


@app.route('/floodlight/tc', methods=['POST'])
def floodlight_shape_traffic():
    body = request.get_json()
    common_commands = ['tc qdisc del dev eth0 root',
                       'tc qdisc add dev eth0 root handle 1: prio priomap 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2',
                       'tc qdisc add dev eth0 parent 1:1 handle 10: netem delay {}ms'.format(
                           body['tcData']['egressDelayMs'])]
    switches_by_container = defaultdict(list)
    for sw in body['affectedAddresses']:
        switches_by_container[sw['containerName']].append(sw)
    for container, switches in switches_by_container.items():
        commands = common_commands.copy()
        for switch in switches:
            commands.extend(['tc filter add dev eth0 protocol ip parent 1:0 prio 1 u32 match ip dst {}/32 match ip '
                             'dport {} 0xffff flowid 1:1'.format(switch['ip'], switch['port'])])
        execute_commands_in_container(commands, container)
    return jsonify({'status': 'Applied traffic control rules to {}'.format(switches_by_container)})


@app.route('/floodlight/tc/cleanup', methods=['POST'])
def floodlight_remove_traffic_shaping():
    body = request.get_json()
    execute_commands_in_container(['tc qdisc del dev eth0 root'], body['containerName'])
    return jsonify({'status': 'ok'})


@app.route('/floodlight/block', methods=['POST'])
def block_floodlight_access():
    body = request.get_json()
    execute_commands_in_container(get_iptables_commands(body, "-A"), body['containerName'])
    if 'ip' in body and 'port' in body:
        return jsonify({'status': "blocked %s:%s" % (body['ip'], body['port'])})
    elif 'ip' in body:
        return jsonify({'status': "blocked ip %s" % (body['ip'])})
    elif 'port' in body:
        return jsonify({'status': "blocked port %s" % (body['port'])})
    else:
        return Response({"status": "Please pass 'ip' or 'port' or both"}, status=400, mimetype='application/json')


@app.route('/floodlight/unblock', methods=['POST'])
def unblock_floodlight_access():
    body = request.get_json()
    execute_commands_in_container(get_iptables_commands(body, "-D"), body['containerName'])
    if 'ip' in body and 'port' in body:
        return jsonify({'status': "unblocked %s:%s" % (body['ip'], body['port'])})
    elif 'ip' in body:
        return jsonify({'status': "unblocked ip %s" % (body['ip'])})
    elif 'port' in body:
        return jsonify({'status': "unblocked port %s" % (body['port'])})
    else:
        return Response({"status": "Please pass 'ip' or 'port' or both"}, status=400, mimetype='application/json')


# Leads to OVS crash in certain situations. Test case:
# ovs switch gets blocked by fl -> fire port down on this switch or on the other end of connected ISL -> ovs crash
@app.route('/floodlight/block-switch', methods=['POST'])
def block_floodlight_switch_access():
    body = request.get_json()
    if 'ip' in body and 'port' in body:
        commands = ['iptables -A INPUT -s {} -p tcp --tcp-flags ALL SYN -j REJECT'.format(body['ip'])]
        commands.extend(get_iptables_commands(body, "-A"))
        execute_commands_in_container(commands, body['containerName'])
        return jsonify({'status': "blocked switch %s:%s" % (body['ip'], body['port'])})
    else:
        return Response({"status": "Both 'ip' and 'port' are required"}, status=400, mimetype='application/json')


@app.route('/floodlight/unblock-switch', methods=['POST'])
def unblock_floodlight_switch_access():
    body = request.get_json()
    if 'ip' in body and 'port' in body:
        commands = ['iptables -D INPUT -s {} -p tcp --tcp-flags ALL SYN -j REJECT'.format(body['ip'])]
        commands.extend(get_iptables_commands(body, "-D"))
        execute_commands_in_container(commands, body['containerName'])
        return jsonify({'status': "unblocked switch %s:%s" % (body['ip'], body['port'])})
    else:
        return Response({"status": "Both 'ip' and 'port' are required"}, status=400, mimetype='application/json')


@app.route('/floodlight/unblock-all', methods=['POST'])
def remove_floodlight_access_restrictions():
    execute_commands_in_container(['iptables -F INPUT', 'iptables -F OUTPUT'], request.get_json().get('containerName'))
    return jsonify({'status': 'All iptables rules in INPUT/OUTPUT chains were removed'})


@app.route('/get-container-ip/<string:name>')
def get_container_ip(name):
    response = docker.from_env().containers.get(name).exec_run("hostname -i").output
    return Response(response)


@app.route("/meter/update", methods=['POST'])
def update_burst_size_and_rate():
    body = request.get_json()
    sw = body['name']
    meter_id = body['meterId']
    burst_size = body['burstSize']
    rate = body['rate']
    switches[sw].update_burst_size_and_rate(meter_id, burst_size, rate)
    return jsonify({'status': 'ok'})


@app.route('/floodlight/nat/input', methods=['POST'])
def nat_change_ip():
    body = request.get_json()
    execute_commands_in_container(
        ['iptables -t nat -A INPUT -p tcp -s {} -j SNAT --to-source {}'.format(body['ip'], body['newIp'])],
        body.get('containerName'))
    return jsonify({'status': "Input connections from '%s' are now changed to '%s'" % (body['ip'], body['newIp'])})


@app.route('/floodlight/nat/input/flush', methods=['POST'])
def nat_cleanup():
    execute_commands_in_container(
        ['iptables -t nat -F INPUT'], request.get_json().get('containerName'))
    return jsonify({'status': "iptables -t nat -F INPUT"})


def get_iptables_commands(address, operation):
    commands = []
    if 'ip' in address and 'port' in address:
        commands.append('iptables {} INPUT -s {} -p tcp --source-port {} -j REJECT --reject-with tcp-reset -m state \
        --state ESTABLISHED'
                        .format(operation, address['ip'], address['port']))
        commands.append('iptables {} OUTPUT -d {} -p tcp --destination-port {} -j REJECT --reject-with tcp-reset -m \
        state --state ESTABLISHED'
                        .format(operation, address['ip'], address['port']))
    elif 'ip' in address:
        commands.append('iptables {} INPUT -s {} -j REJECT --reject-with tcp-reset -m state --state ESTABLISHED'
                        .format(operation, address['ip']))
        commands.append('iptables {} OUTPUT -d {} -j REJECT --reject-with tcp-reset -m state --state ESTABLISHED'
                        .format(operation, address['ip']))
    elif 'port' in address:
        commands.append('iptables {} INPUT -p tcp --source-port {} -j REJECT --reject-with tcp-reset -m state --state \
        ESTABLISHED'
                        .format(operation, address['port']))
        commands.append('iptables {} OUTPUT -p tcp --destination-port {} -j REJECT --reject-with tcp-reset -m state \
        --state ESTABLISHED'
                        .format(operation, address['port']))
    return commands


def init_app(_switches):
    global switches, A_sw
    switches = _switches
    A_sw = switches[A_SW_NAME]
    return app
