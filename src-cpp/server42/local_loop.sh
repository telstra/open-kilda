#!/usr/bin/env bash

ip link add eth42 type veth peer name eth24
ip link set eth24 up
ip link set eth42 up

./server42 -c 0x1f --vdev=net_pcap0,iface=eth42 --vdev=net_pcap1,iface=eth24 --no-huge -- --debug
