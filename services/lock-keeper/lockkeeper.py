import os

import paramiko
import docker
from flask import Flask
from flask import jsonify
from flask import request

app = Flask(__name__)

HOST = os.environ.get("LOCK_KEEPER_HOST")
USER = os.environ.get("LOCK_KEEPER_USER")
SECRET = os.environ.get("LOCK_KEEPER_SECRET")
FL_CONTAINER_NAME = "floodlight"
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
    docker.from_env().containers.get(FL_CONTAINER_NAME).stop()
    return jsonify({'status': 'ok'})


@app.route('/floodlight/start', methods=['POST'])
def fl_start():
    docker.from_env().containers.get(FL_CONTAINER_NAME).start()
    return jsonify({'status': 'ok'})


@app.route('/floodlight/restart', methods=['POST'])
def fl_restart():
    docker.from_env().containers.get(FL_CONTAINER_NAME).restart()
    return jsonify({'status': 'ok'})
