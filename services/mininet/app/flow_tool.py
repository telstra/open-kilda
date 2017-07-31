#!/usr/bin/python

from bottle import run, get, response, post
import os
import scapy.all as scapy


links_up = ["s3-eth1", "s3-eth2"]
links_down = []


@post('/linkdown')
def link_down():
    try:
        link_to_down = links_up.pop(0)
    except IndexError:
        response.status = 500
        return "No more links to cut!\n"
    else:
        result = os.system("ifconfig %s down" % (link_to_down))
        if result == 0:
           links_down.append(link_to_down)
           response.status = 200
        else:
           response.status = 500

@post('/linkup')
def link_down():
    try:
        link_to_up = links_down.pop()
    except IndexError:
        response.status = 500
        return "No more links to restore!\n"
    else:
        result = os.system("ifconfig %s up" % (link_to_up))
        if result == 0:
           links_up.append(link_to_up)
           response.status = 200
        else:
           response.status = 500

@get('/checkflowtraffic')
def send_traffic_through_flow():
     def _rx_snapshot():
         rx = 0
         with open("/proc/net/dev", "r") as f:
             for line in f.readlines():
                 if line.startswith("s5-eth2"):
                      rx = int(line.split()[10])
                      break
         return rx

     rx_before = _rx_snapshot()
     scapy.sendp(scapy.Ether()/scapy.Dot1Q(vlan=1000)/scapy.IP()/scapy.ICMP(),
                 iface="s1-eth1", count=1000)
     rx_after = _rx_snapshot()
     if (rx_after - rx_before) > 100:
         response.status = 200
         return "Traffic seems to go through\n"
     else:
         response.status = 503
         return "Traffic does not seem to go through\n"

run(host='0.0.0.0', port=17191, debug=True)
