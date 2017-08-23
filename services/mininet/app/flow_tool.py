#!/usr/bin/python

from bottle import run, get, response, post, error
import ctypes
import multiprocessing
import os
import scapy.all as scapy


links = ("s3-eth1", "s3-eth2")
links_up = list(links)
links_down = []
number_of_packets = 1000


@error(404)
def not_found(error):
    retval = """404
Thank you, Mario! but our princess is in another castle!\n"""
    return retval


@get('/debug/linkstate')
def link_state():
    retval = """links up: [%s]
links down: [%s]\n""" % (", ".join(links_up), ", ".join(links_down))
    return retval


@post('/linkdown')
def link_down():
    try:
        link_to_down = links_up[0]
    except IndexError:
        response.status = 500
        return "No more links to cut!\n"
    else:
        result = os.system("ifconfig %s down" % (link_to_down))
        if result == 0:
            links_up.pop(0)
            links_down.append(link_to_down)
            response.status = 200
        else:
            response.status = 500
            return "Failed to put existing link down\n"


@post('/linkup')
def link_down():
    try:
        link_to_up = links_down[-1]
    except IndexError:
        response.status = 500
        return "No more links to restore!\n"
    else:
        result = os.system("ifconfig %s up" % (link_to_up))
        if result == 0:
            links_down.pop()
            links_up.append(link_to_up)
            response.status = 200
        else:
            response.status = 500


@post('/cleanse')
def cleanse():
    while links_down:
        links_down.pop()
    while links_up:
        links_up.pop()
    for link in links:
        result = os.system("ifconfig %s up" % (link))
        links_up.append(link)


def traffic_sender():
    scapy.sendp(scapy.Ether()/scapy.Dot1Q(vlan=1000)/scapy.IP()/scapy.ICMP(),
                iface="s1-eth1", count=number_of_packets)


def traffic_listener(traffic_goes_through):
    result = scapy.sniff(count=number_of_packets,
                         filter='icmp and (vlan 1000)',
                         iface='s5-eth1')
    received = len([_ for _ in result if _.payload.payload.name == 'ICMP'])
    if number_of_packets - received < 200:
        traffic_goes_through.value = True


@get('/checkflowtraffic')
def check_traffic():
    traffic_goes_through = multiprocessing.Value(ctypes.c_bool, False)
    sender = multiprocessing.Process(target=traffic_sender)
    checker = multiprocessing.Process(target=traffic_listener,
                                      args=(traffic_goes_through,))
    checker.start()
    sender.start()
    sender.join(5)
    checker.join(5)
    if traffic_goes_through.value:
        response.status = 200
        return "Traffic seems to go through\n"
    else:
        response.status = 503
        return "Traffic does not seem to go through\n"


run(host='0.0.0.0', port=17191, debug=True)
