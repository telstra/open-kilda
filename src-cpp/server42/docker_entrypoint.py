#!/usr/bin/python3

import os
import sys
import logging

import docker
import netifaces
import subprocess

logger = logging.getLogger()

NETWORK_LAB_NAME = os.environ.get("LAB_NET")
if NETWORK_LAB_NAME is None:
    logging.error('environment variable LAB_NET is not defined')
    raise SystemExit(1)

NETWORK_LAB_IFACE = None
docker_client = docker.from_env()
self_container = docker_client.containers.get(os.environ.get('HOSTNAME'))
PROJECT_NAME = self_container.attrs['Config']['Labels'].get('com.docker.compose.project', '')
network_lab_iface_mac = self_container.attrs['NetworkSettings']['Networks'][f"{PROJECT_NAME}_{NETWORK_LAB_NAME}"]['MacAddress']
for iface in netifaces.interfaces():
    ifaddr = netifaces.ifaddresses(iface)
    if ifaddr[netifaces.AF_LINK][0]['addr'] == network_lab_iface_mac:
        NETWORK_LAB_IFACE = iface
        break

if NETWORK_LAB_IFACE is None:
    logging.error(f"can't find correlation between docker network {NETWORK_LAB_NAME} with mac {network_lab_iface_mac} "
                  f"and interfaces:")
    for iface in netifaces.interfaces():
        logging.error(f"{iface} {netifaces.ifaddresses(iface)[netifaces.AF_LINK][0]['addr']}")

    raise SystemExit(2)

subprocess.run(["./server42" , "-c", "0x1f", "--log-level=lib.eal:8" , f"--vdev=net_pcap0,iface={NETWORK_LAB_IFACE}", "--no-huge", "--", "--debug"], stderr=sys.stderr, stdout=sys.stdout)
