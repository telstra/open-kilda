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

from flask import Flask, Response, request, jsonify
from docker import DockerClient
import requests
import logging
import itertools
from threading import Event
from urllib.parse import urlparse
import os
from common import init_logger, run_thread, loop_forever

API_PORT = 8288

app = Flask(__name__)
init_logger()
logger = logging.getLogger()
docker = DockerClient(base_url='unix://var/run/docker.sock')

SELF_CONTAINER_ID = os.environ.get("SELF_CONTAINER_ID")
if SELF_CONTAINER_ID:
    SELF_CONTAINER = docker.containers.get(SELF_CONTAINER_ID)
    LAB_SERVICE_IMAGE = os.environ.get("LAB_SERVICE_IMAGE", SELF_CONTAINER.image.tags[0])
    NETWORK_NAME = list(SELF_CONTAINER.attrs['NetworkSettings']['Networks'].keys())[0]
else:
    logger.warning("Seems like lab-api isn't running inside container. "
                   "It's required to create virtual topologies properly")

HW_LOCKKEEPER_REST_HOST = os.environ.get("HW_LOCKKEEPER_REST_HOST")

HW_LAB_ID = 0
count = itertools.count(start=1, step=1)
labs = {}


def make_container_name(id):
    return 'lab_service-' + str(id)


class Lab:
    def __init__(self, lab_id, lab_def):
        self.lab_def = lab_def
        self.lab_id = lab_id

        self.activated = Event()
        self.error = None

        self.tgens = {}
        for tgen_def in [active_tgen for active_tgen in lab_def.get('traff_gens', [])
                         if active_tgen['status'].lower() == "active"]:
            self.tgens[tgen_def['name']] = urlparse(tgen_def['control_endpoint'])

        if lab_id == HW_LAB_ID:
            # HW lab doesn't require activation
            self.activated.set()

    def run(self):
        name = make_container_name(self.lab_id)
        env = {'LAB_ID': self.lab_id}
        try:
            env['API_HOST'] = os.environ['API_HOST']
        except KeyError:
            api_host = SELF_CONTAINER.attrs['Config']['Hostname']
            env['API_HOST'] = '{}:{}'.format(api_host, API_PORT)
        volumes = {
            '/var/run/docker.sock': {'bind': '/var/run/docker.sock', 'mode': 'rw'},
            '/lib/modules': {'bind': '/lib/modules', 'mode': 'ro'}}
        docker.containers.run(LAB_SERVICE_IMAGE, command='service', environment=env, volumes=volumes,
                              name=name, privileged=True, detach=True)
        docker.networks.get(NETWORK_NAME).connect(name, aliases=[name])

    def destroy(self):
        if self.lab_id != HW_LAB_ID:
            logger.debug('Destroying lab with id %s' % self.lab_id)
            cnt = docker.containers.get(make_container_name(self.lab_id))
            cnt.stop()
            cnt.remove()
        del labs[self.lab_id]


@app.route('/api', methods=['GET'])
def get_defined_labs():
    return jsonify([lab_id for lab_id in labs.keys()])


@app.route('/api', methods=['POST'])
def create_lab():
    try:
        is_hw = request.headers.get('Hw-Env', False)
        if is_hw:
            if HW_LAB_ID in labs:
                return Response('Hardware lab already defined', status=409)
            else:
                labs[HW_LAB_ID] = Lab(HW_LAB_ID, request.get_json())
                return jsonify({'lab_id': HW_LAB_ID})

        lab_id = next(count)
        lab = Lab(lab_id, request.get_json())
        labs[lab_id] = lab
        lab.run()
        lab.activated.wait()
        if lab.error:
            lab.destroy()
            return Response(lab.error, status=500)
    except Exception as ex:
        logger.exception(ex)
        return Response(str(ex), status=500)

    return jsonify({'lab_id': lab_id})


@app.route('/api/<int:lab_id>', methods=['DELETE'])
def delete_lab(lab_id):
    if lab_id not in labs:
        return Response('No lab with id %d' % lab_id, status=404)

    try:
        labs[lab_id].destroy()
    except Exception as ex:
        logger.exception(ex)
        return Response(str(ex), status=500)

    return jsonify({'status': 'ok'})


@app.route('/api/<int:lab_id>/activate', methods=['POST'])
def activate_lab(lab_id):
    if lab_id not in labs:
        return Response('No lab with id %d' % lab_id, status=404)

    lab = labs[lab_id]
    try:
        data = request.get_json()
        if 'error' in data:
            lab.error = data['error']
    finally:
        lab.activated.set()

    return jsonify({'status': 'ok'})


@app.route('/api/<int:lab_id>/definition', methods=['GET'])
def get_lab_definition(lab_id):
    if lab_id not in labs:
        return Response('No lab with id %d' % lab_id, status=404)
    return jsonify(labs[lab_id].lab_def)


def proxy_request(host, port, path):
    url = 'http://{}:{}/{}'.format(host, str(port), path)
    r = requests.request(
        request.method, url, headers=request.headers, data=request.data
    )
    return Response(r.content, status=r.status_code, headers=dict(r.headers))


@app.route('/api/<int:lab_id>/lock-keeper/<path:to_path>', methods=['GET', 'POST', 'DELETE'])
def lockkeeper_proxy(lab_id, to_path):
    try:
        if lab_id not in labs:
            return Response('No lab with id %d' % lab_id, status=404)

        if lab_id == HW_LAB_ID:
            return proxy_request(HW_LOCKKEEPER_REST_HOST, '5001', to_path)
        return proxy_request(make_container_name(lab_id), '5001', to_path)
    except Exception as ex:
        logger.exception(ex)
        return Response(str(ex), status=500)


@app.route('/api/<int:lab_id>/traffgen/<tg_name>/<path:to_path>', methods=['GET', 'HEAD', 'POST', 'PUT', 'DELETE'])
def traffgen_proxy(lab_id, tg_name, to_path):
    try:
        if lab_id not in labs:
            return Response('No lab with id %d' % lab_id, status=404)

        tg_url = labs[lab_id].tgens[tg_name]
        if lab_id == HW_LAB_ID:
            return proxy_request(tg_url.hostname, tg_url.port, to_path)
        return proxy_request(make_container_name(lab_id), tg_url.port, to_path)
    except Exception as ex:
        logger.exception(ex)
        return Response(str(ex), status=500)


@app.route('/api/flush', methods=['POST'])
def flush_labs_api():
    global labs

    keys = []
    for lab in list(labs.values()):
        try:
            lab.destroy()
            keys.append(lab.lab_id)
        except Exception as ex:
            logger.exception(ex)
    labs = {}
    return jsonify(keys)


def main():
    server_th = run_thread(lambda: app.run(host='0.0.0.0', port=API_PORT, threaded=True))

    def teardown():
        logger.info('Terminating...')

        for lab in list(labs.values()):
            try:
                lab.destroy()
            except Exception as ex:
                logger.exception(ex)
        server_th.terminate()

    loop_forever(teardown)
