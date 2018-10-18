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
from urllib.parse import urlparse
import os

app = Flask(__name__)

logger = logging.getLogger()

API_CONTAINER_NAME = "lab-api"
docker = DockerClient(base_url='unix://var/run/docker.sock')

HW_LOCKKEEPER_REST_HOST = os.environ.get("HW_LOCKKEEPER_REST_HOST")
LAB_SERVICE_IMAGE = os.environ.get("LAB_SERVICE_IMAGE")

HW_LAB_ID = 0
count = 0
labs = {}


def get_network_name():
    cnt = docker.containers.get(API_CONTAINER_NAME)
    return list(cnt.attrs['NetworkSettings']['Networks'].keys())[0]


NETWORK_NAME = get_network_name()


def make_container_name(id):
    return 'lab_service-' + str(id)


class Lab:
    def __init__(self, lab_id, lab_def):
        self.lab_def = lab_def
        self.lab_id = lab_id

        self.tgens = {}
        for tgen_def in lab_def.get('active_traff_gens', []):
            self.tgens[tgen_def['name']] = urlparse(tgen_def['control_endpoint'])

        if lab_id != HW_LAB_ID:
            name = make_container_name(lab_id)
            env = {'LAB_ID': lab_id}
            volumes = {'/var/run/docker.sock': {'bind': '/var/run/docker.sock', 'mode': 'rw'}}
            docker.containers.run(
                LAB_SERVICE_IMAGE, environment=env, volumes=volumes, name=name, privileged=True, detach=True)
            docker.networks.get(NETWORK_NAME).connect(name, aliases=[name])

    def destroy(self):
        if self.lab_id != HW_LAB_ID:
            cnt = docker.containers.get(make_container_name(self.lab_id))
            cnt.stop()
            cnt.remove()


@app.route('/api/', methods=['GET'])
def get_defined_labs():
    return jsonify([lab_id for lab_id in labs.keys()])


@app.route('/api', methods=['POST'])
def create_lab():
    global count

    try:
        is_hw = request.headers.get('Hw-Env', False)
        if is_hw:
            if HW_LAB_ID in labs:
                return Response('Hardware lab already defined', status=409)
            else:
                labs[HW_LAB_ID] = Lab(HW_LAB_ID, request.get_json())
                return jsonify({'lab_id': HW_LAB_ID})

        count = count + 1
        labs[count] = Lab(count, request.get_json())
    except Exception as ex:
        logger.exception(ex)
        return Response(str(ex), status=500)
    return jsonify({'lab_id': count})


@app.route('/api/<int:lab_id>', methods=['DELETE'])
def delete_lab(lab_id):
    if lab_id not in labs:
        return Response('No lab with id %d' % lab_id, status=404)

    try:
        labs[lab_id].destroy()
        del labs[lab_id]
    except Exception as ex:
        logger.exception(ex)
        return Response(str(ex), status=500)

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


@app.route('/api/<int:lab_id>/traffgen/<tg_name>/<path:to_path>', methods=['GET', 'HEAD', 'POST', 'DELETE'])
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


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8288)
