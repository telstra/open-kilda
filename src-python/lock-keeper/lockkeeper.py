import os
from collections import defaultdict

import docker
import paramiko
from flask import Flask, Response
from flask import jsonify
from flask import request

app = Flask(__name__)

HOST = os.environ.get("LOCK_KEEPER_HOST")
USER = os.environ.get("LOCK_KEEPER_USER")
SECRET = os.environ.get("LOCK_KEEPER_SECRET")
PORT = 22


def execute_remote_commands(commands):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(hostname=HOST, username=USER, password=SECRET, port=PORT)
    data = []
    for command in commands:
        stdin, stdout, stderr = client.exec_command(command)
        data.append((stdout.read() + stderr.read()).decode('utf-8'))
    client.close()
    return data


def execute_commands_in_container(commands, container_name):
    container = docker.from_env().containers.get(container_name)
    for command in commands:
        container.exec_run(command)


def execute_remote_command(command):
    return execute_remote_commands([command])[0]


def change_ports_state(ports, port_state):
    """Common port states: up, down."""
    commands = ["ovs-ofctl mod-port br0 {} {}".format(str(port), port_state)
                for port in ports]
    return execute_remote_commands(commands)


def test_answer():
    test_data = ('NXST_FLOW reply (xid=0x4):\n cookie=0x15, duration=422883.97'
                 '5s, table=0, n_packets=70278, n_bytes=17499222, idle_age=5, '
                 'hard_age=65534, in_port=7 actions=output:8\n cookie=0x16, du'
                 'ration=422884.022s, table=0, n_packets=69701, n_bytes=173555'
                 '49, idle_age=4, hard_age=65534, in_port=51 actions=output:52'
                 '\n')

    assert parse_dump_flows(test_data) == [
        {
            'in_port': 7,
            'out_port': 8
        }, {
            'in_port': 51,
            'out_port': 52
        }
    ]


def int_from_str_by_pattern(string, pattern):
    start = string.find(pattern)
    end = string.find(' ', start)
    if end == -1:
        end = None
    return int(string[start + len(pattern):end])


def parse_dump_flows(raw):
    data = raw[raw.find('\n') + 1:]
    return [{'in_port': int_from_str_by_pattern(x, 'in_port='),
             'out_port': int_from_str_by_pattern(x, 'actions=output:')}
            for x in data.split('\n') if x]


@app.route('/flows', methods=['GET'])
def get_flows_route():
    flows = execute_remote_command('ovs-ofctl dump-flows br0')
    return jsonify(parse_dump_flows(flows))


@app.route('/flows', methods=['POST'])
def post_flows_route():
    payload = request.get_json()
    commands = ['ovs-ofctl add-flow br0 in_port={in_port},' \
                'actions=output={out_port}'.format(**flow) for flow in payload]
    execute_remote_commands(commands)
    return jsonify({'status': 'ok'})


@app.route('/flows', methods=['DELETE'])
def delete_flows_route():
    payload = request.get_json()
    commands = ['ovs-ofctl del-flows br0 in_port={in_port}'.format(**flow)
                for flow in payload]
    execute_remote_commands(commands)
    return jsonify({'status': 'ok'})


@app.route('/ports', methods=['POST'])
def ports_up():
    change_ports_state(request.get_json(), "up")
    return jsonify({'status': 'ok'})


@app.route('/ports', methods=['DELETE'])
def ports_down():
    change_ports_state(request.get_json(), "down")
    return jsonify({'status': 'ok'})


@app.route('/floodlight/stop', methods=['POST'])
def fl_stop():
    docker.from_env().containers.get(request.get_json().get('containerName')).stop()
    return jsonify({'status': 'ok'})


@app.route('/floodlight/start', methods=['POST'])
def fl_start():
    docker.from_env().containers.get(request.get_json().get('containerName')).start()
    return jsonify({'status': 'ok'})


@app.route('/floodlight/restart', methods=['POST'])
def fl_restart():
    docker.from_env().containers.get(request.get_json().get('containerName')).restart()
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


@app.route('/floodlight/block-switch', methods=['POST'])
def block_floodlight_switch_access():
    body = request.get_json()
    if 'ip' in body and 'port' in body:
        commands = ['iptables -A INPUT -s {} -p tcp --syn -j REJECT'.format(body['ip'])]
        commands.extend(get_iptables_commands(body, "-A"))
        execute_commands_in_container(commands, body['containerName'])
        return jsonify({'status': "blocked switch %s:%s" % (body['ip'], body['port'])})
    else:
        return Response({"status": "Both 'ip' and 'port' are required"}, status=400, mimetype='application/json')


@app.route('/floodlight/unblock-switch', methods=['POST'])
def unblock_floodlight_switch_access():
    body = request.get_json()
    if 'ip' in body and 'port' in body:
        commands = ['iptables -D INPUT -s {} -p tcp --syn -j REJECT'.format(body['ip'])]
        commands.extend(get_iptables_commands(body, "-D"))
        execute_commands_in_container(commands, body['containerName'])
        return jsonify({'status': "unblocked switch %s:%s" % (body['ip'], body['port'])})
    else:
        return Response({"status": "Both 'ip' and 'port' are required"}, status=400, mimetype='application/json')


@app.route('/floodlight/unblock-all', methods=['POST'])
def remove_floodlight_access_restrictions():
    execute_commands_in_container(['iptables -F INPUT', 'iptables -F OUTPUT'], request.get_json().get('containerName'))
    return jsonify({'status': 'All iptables rules in INPUT/OUTPUT chains were removed'})


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
        commands.append('iptables {} INPUT -s {} -p tcp -j REJECT --reject-with tcp-reset -m state --state ESTABLISHED'
                        .format(operation, address['ip']))
        commands.append('iptables {} OUTPUT -d {} -p tcp -j REJECT --reject-with tcp-reset -m state --state ESTABLISHED'
                        .format(operation, address['ip']))
    elif 'port' in address:
        commands.append('iptables {} INPUT -p tcp --source-port {} -j REJECT --reject-with tcp-reset -m state --state \
        ESTABLISHED'
                        .format(operation, address['port']))
        commands.append('iptables {} OUTPUT -p tcp --destination-port {} -j REJECT --reject-with tcp-reset -m state \
        --state ESTABLISHED'
                        .format(operation, address['port']))
    return commands
