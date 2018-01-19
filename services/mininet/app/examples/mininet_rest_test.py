#!/usr/bin/env python
"""
mininet_rest_test.py: Validate mininet_rest works within the mininet framework, focused on PING

    This will launch an example network, with switches and hosts and rules, confirming that
    ping will work across hosts that are connected properly.

Dependencies:
    This class depends on a controller - it is best to launch both mininet and floodlight.
    $ docker-compose up -d mininet floodlight

    If possible, bring up the entire kilda controller.

Usage (example uses VLAN ID=1000):
    From the command line - run as a MODULE so that mininet_rest can be imported)
        cd /; python -m app.examples.mininet_rest_test

"""

from .. import mininet_rest

import requests
import logging

logger = logging.getLogger(__name__)

topo_json = {
    "controllers": [
        {
            "host": "kilda",
            "name": "floodlight",
            "port": 6653
        }
    ],
    "links": [
        {
            "node1": "00000001",
            "node2": "00000002"
        },
        {
            "node1": "00000002",
            "node2": "00000003"
        },
        {
            "node1": "00000003",
            "node2": "00000004"
        },
        {
            "node1": "00000004",
            "node2": "00000005"
        }
    ],
    "switches": [
        {
            "dpid": "deadbeef00000001",
            "name": "00000001"
        },
        {
            "dpid": "deadbeef00000002",
            "name": "00000002"
        },
        {
            "dpid": "deadbeef00000003",
            "name": "00000003"
        },
        {
            "dpid": "deadbeef00000004",
            "name": "00000004"
        },
        {
            "dpid": "deadbeef00000005",
            "name": "00000005"
        }
    ]
}


def cleanup():
    result = requests.post(url="http://localhost:38080/cleanup")
    print result


def create_topology():
    result = requests.post(url="http://localhost:38080/topology",json=topo_json)
    print result


def test_ping(src_switch, src_port, src_vlan, dst_switch, dst_port, dst_vlan):
    """setup our rules for host / switch / switch and see if we can ping"""
    if src_vlan is None:
        src_vlan = 0
    if dst_vlan is None:
        dst_vlan = 0
    urlbase = "http://localhost:38080/checkpingtraffic"
    args = "srcswitch={}&srcport={}&srcvlan={}&dstswitch={}&dstport={}&dstvlan={}".format(
        src_switch,src_port,src_vlan,dst_switch,dst_port,dst_vlan)
    url = "{}?{}".format(urlbase,args)
    print ("** PING: {}".format(url))
    result = requests.get(url=url)
    return result


def print_ping_result(response, should_ping):
    if response.status_code == 503:
        if should_ping:
            print "FAILURE - CAN'T PING"
        else:
            print "SUCCESS - NO PING"
    elif response.status_code == 200:
        if should_ping:
            print "SUCCESS - CAN PING"
        else:
            print "FAILURE - CAN PING"
    else:
        print "ERROR - WRONG CODE"


def main():
    mininet_rest.init()
    create_topology()
    s2 = "00000002"
    s3 = "00000003"
    s4 = "00000004"

    print_ping_result( test_ping(s2, 2, None, s4, 1, None), should_ping=False) # should fail before rule
    mininet_rest.add_single_switch_rules(s3, 1, 2, None, None)
    print_ping_result( test_ping(s2, 2, None, s4, 1, None), should_ping=True)  # should now work
    mininet_rest.clear_single_switch_rules(s3, 1, 2)
    print_ping_result( test_ping(s2, 2, None, s4, 1, None), should_ping=False) # should fail again

    cleanup()



if __name__ == '__main__':
    main()
