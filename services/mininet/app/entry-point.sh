#!/bin/bash

service openvswitch-switch start

kilda-mininet-rest &
kilda-mininet-flow-tool &

sleep infinity
