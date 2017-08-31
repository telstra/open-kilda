#!/usr/bin/python

from bottle import run, get, response, request, post, error
import ctypes
import multiprocessing
import os
import scapy.all as scapy


number_of_packets = 1000


@error(404)
def not_found(error):
    retval = """404
Thank you, Mario! but our princess is in another castle!\n"""
    return retval


@post('/set_link_state')
def link_update():
    required_parameters = ("switch", "port", "newstate")
    for _ in required_parameters:
        if request.query.get(_) is None:
            response.status = 500
            return "%s: %s must be specified" % (request.path, _)
    p = dict([(_, request.query.get(_)) for _ in required_parameters])

    iface = "%s-eth%s" % (p['switch'], p['port'])
    print "Setting", iface, "to", p['newstate']
    result = os.system("ifconfig %s %s" % (iface, p['newstate']))
    print "result is", result
    if result == 0:
        response.status = 200
        return "Successfully put link %s in state %s\n" % (iface,
                                                           p['newstate'])
    else:
        return "Failed to put link %s in state %s\n" % (iface, p['newstate'])


def traffic_sender(linkid, vlanid):
    scapy.sendp(
        scapy.Ether()/scapy.Dot1Q(vlan=int(vlanid))/scapy.IP()/scapy.ICMP(),
        iface=linkid, count=number_of_packets)


def traffic_listener(traffic_goes_through, vlanid, link):
    result = scapy.sniff(count=number_of_packets,
                         filter='icmp and (vlan %s)' % vlanid,
                         iface='%s' % link)
    received = len([_ for _ in result if _.payload.payload.name == 'ICMP'])
    if number_of_packets - received < 250:
        traffic_goes_through.value = True


@get('/checkflowtraffic')
def check_traffic():
    required_parameters = ("srcswitch", "dstswitch", "srcport", "dstport",
                           "srcvlan", "dstvlan")
    for _ in required_parameters:
        if request.query.get(_) is None:
            response.status = 500
            return "%s: %s must be specified\n" % (request.path, _)
    p = dict([(_, request.query.get(_)) for _ in required_parameters])

    traffic_goes_through = multiprocessing.Value(ctypes.c_bool, False)
    sender = multiprocessing.Process(
        target=traffic_sender,
        args=("%s-eth%s" % (p['srcswitch'], p['srcport']), p['srcvlan']))
    checker = multiprocessing.Process(
        target=traffic_listener,
        args=(traffic_goes_through, p['dstvlan'],
              "%s-eth%s" % (p['dstswitch'], p['dstport'])))
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
