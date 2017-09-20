#!/usr/bin/python


from bottle import run, get, response, request, post, error
import ctypes
import multiprocessing
import os
import scapy.all as s


number_of_packets = 1000
expected_delta = 500


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
    result = os.system("ovs-vsctl del-controller %s" % p['switch'])
    return respond(result == 0,
                   "Switch %s is successfully knocked out\n" % p['switch'],
                   "Failed to knock out switch %s\n" % p['switch'])


@post("/reviveswitch")
@required_parameters("switch", "controller")
def switch_revive(p):
    result = os.system("ovs-vsctl set-controller %s %s" %
                       (p['switch'], p["controller"]))
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


run(host='0.0.0.0', port=17191, debug=True)
