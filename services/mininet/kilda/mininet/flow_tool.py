#!/usr/bin/python
# Copyright 2017 Telstra Open Source
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


from bottle import run, get, response, request, post, error, install
import ctypes
import multiprocessing
import os
import scapy.all as s
import socket
import logging
import json
from logging.config import dictConfig
from functools import wraps

logger = logging.getLogger()

DUMMY_CONTROLLER = "tcp:127.0.1.2:20000"


def log_to_logger(fn):
    '''
    Wrap a Bottle request so that a log line is emitted after it's handled.
    (This decorator can be extended to take the desired logger as a param.)
    '''
    @wraps(fn)
    def _log_to_logger(*args, **kwargs):
        actual_response = fn(*args, **kwargs)
        logger.info('%s %s %s %s' % (request.remote_addr,
                                    request.method,
                                    request.url,
                                    response.status))
        return actual_response
    return _log_to_logger

install(log_to_logger)

number_of_packets = 1000
expected_delta = 500
of_ctl = "ovs-ofctl -O openflow13"


def required_parameters(*pars):
    def _hatch(__):
        def _hatchet():
            for _ in pars:
                if request.query.get(_) is None:
                    response.status = 500
                    return "%s: %s must be specified\n" % (request.path, _)
            return __(dict([(_, request.query.get(_)) for _ in pars]))
        return _hatchet
    return _hatch


def respond(status, ok_message, fail_message):
    if status:
        response.status = 200
        return ok_message
    response.status = 503
    return fail_message


@error(404)
def not_found(error):
    return "Thank you, Mario! but our princess is in another castle!\n"


@post('/set_link_state')
@required_parameters("switch", "port", "newstate")
def link_state_changer(p):
    iface = "%s-eth%s" % (p['switch'], p['port'])
    newstate = iface, p['newstate']
    result = os.system("ifconfig %s %s" % newstate)
    return respond(result == 0,
                   "Successfully put link %s in state %s\n" % newstate,
                   "Failed to put link %s in state %s\n" % newstate)


@get('/checkflowtraffic')
@required_parameters("srcswitch", "dstswitch", "srcport", "dstport", "srcvlan",
                     "dstvlan")
def check_traffic(p):
    def traffic_sender(linkid, vlanid):
        payload = s.Ether()/s.Dot1Q(vlan=int(vlanid))/s.IP()/s.ICMP()
        s.sendp(payload, iface=linkid, count=number_of_packets)

    def traffic_listener(traffic_goes_through, vlanid, link):
        # NOTE: sniff() takes optional filter argument which is supposed to
        # contain BPF string. This filter is then supposed to be applied to
        # captured packets in a manner similar to other traffic capture tools.
        # However in case sniff() fails to use filtering it apparently just
        # returns any packet instead of failing. It appears that running
        # scapy in a container with insufficient (i.e. any other set than full
        # set) privileges results exactly in this behavior. lfilter argument
        # apparently makes things even worse since sniff appears to loose
        # packets when lfilter is used.
        # That is why an approach with a delta of packets and sniff timeout
        # is used now. It appears to be the most reliable way to test traffic
        # through flow.
        result = s.sniff(timeout=5, iface=link)
        received = sum(1 for _ in result if _.haslayer(s.ICMP))
        if number_of_packets - received < expected_delta:
            traffic_goes_through.value = True

    traffic_goes_through = multiprocessing.Value(ctypes.c_bool, False)
    sender = multiprocessing.Process(
        target=traffic_sender,
        args=("%s-eth%s" % (p['srcswitch'], p['srcport']), p['srcvlan']))
    checker = multiprocessing.Process(
        target=traffic_listener,
        args=(traffic_goes_through, p['dstvlan'],
              "%s-eth%s" % (p['dstswitch'], p['dstport'])))
    checker.start(), sender.start(), sender.join(5), checker.join(7)

    return respond(traffic_goes_through.value,
                   "Traffic seems to go through\n",
                   "Traffic does not seem to go through\n")


@post("/knockoutswitch")
@required_parameters("switch")
def switch_knock_out(p):
    result = os.system("ovs-vsctl set-controller %s %s" % (p['switch'],
                                                           DUMMY_CONTROLLER))
    return respond(result == 0,
                   "Switch %s is successfully knocked out\n" % p['switch'],
                   "Failed to knock out switch %s\n" % p['switch'])


@post("/reviveswitch")
@required_parameters("switch", "controller")
def switch_revive(p):
    params = p['controller'].split(":", 3)
    ip = socket.gethostbyname(params[1])
    controller = params[0] + ":" + ip + ":" + params[2]
    result = os.system("ovs-vsctl set-controller %s %s" %
                       (p['switch'], controller))
    return respond(result == 0,
                   "Switch %s is successfully revived\n" % p['switch'],
                   "Failed to revive switch %s\n" % p['switch'])


@post("/cutlink")
@required_parameters("switch", "port")
def cut_link(p):
    sppair = (p['switch'], p['port'])
    result = os.system("ovs-ofctl add-flow %s priority=65500,in_port=%s,"
                       "action=drop -O openflow13" % sppair)
    return respond(result == 0,
                   "Link to switch %s port %s is successfully cut\n" % sppair,
                   "Failed to cut link to switch %s port %s\n" % sppair)


@post("/restorelink")
@required_parameters("switch", "port")
def restore_link(p):
    sppair = (p['switch'], p['port'])
    result = os.system("ovs-ofctl del-flows %s -O openflow13 \"priority=65500"
                       ",in_port=%s\" --strict" % (p['switch'], p['port']))
    return respond(result == 0,
                   "Link to switch %s port %s is restored\n" % sppair,
                   "Failed to restore link to switch %s port %s\n" % sppair)


def port_mod(switch, port, action):
    return os.system("%s mod-port %s %s %s" % (of_ctl, switch, port, action))


@post("/port/down")
@required_parameters("switch", "port")
def port_down(p):
    result = port_mod(p['switch'], p['port'], 'down')
    return respond(result == 0,
                   "Switch %s port %s down\n" % (p['switch'], p['port']),
                   "Fail switch %s port %s down\n" % (p['switch'], p['port']))


@post("/port/up")
@required_parameters("switch", "port")
def port_up(p):
    result = port_mod(p['switch'], p['port'], 'up')
    return respond(result == 0,
                   "Switch %s port %s up\n" % (p['switch'], p['port']),
                   "Fail switch %s port %s up\n" % (p['switch'], p['port']))


@post("/send_malformed_packet")
def send_malformed_packet():
    # This packet create isl between de:ad:be:ef:00:00:00:02 and
    # de:ad:be:ef:00:00:00:02

    data = '\x02\x07\x04\xbe\xef\x00\x00\x00\x02\x04\x03\x02\x00\x01\x06\x02' \
           '\x00x\xfe\x0c\x00&\xe1\x00\xde\xad\xbe\xef\x00\x00\x00\x02\xfe' \
           '\x0c\x00&\xe1\x01\x00\x00\x01_\xb6\x8c\xacG\xfe\x08\x00&\xe1\x02' \
           '\x00\x00\x00\x00\x00\x00'

    payload = (s.Ether(dst="00:26:e1:ff:ff:ff") /
               s.IP(dst="192.168.0.255") /
               s.UDP(dport=61231, sport=61231) /
               data)

    try:
        s.sendp(payload, iface="00000001-eth1")
        return "ok"
    except Exception as ex:
        response.status = 500
        return "can't send malformed packet {}".format(ex)


def main():
    with open("/app/log.json", "r") as fd:
        logging.config.dictConfig(json.load(fd))

    run(host='0.0.0.0', port=17191, debug=True)
