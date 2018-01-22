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
from functools import partial

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
        },
        {
            "node1": "00000005",
            "node2": "00000006"
        },
        {
            "node1": "00000006",
            "node2": "00000007"
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
        },
        {
            "dpid": "deadbeef00000006",
            "name": "00000006"
        },
        {
            "dpid": "deadbeef00000007",
            "name": "00000007"
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


def call_pingagle(pingable, should_ping):
    result = pingable()
    print_ping_result( result, should_ping) # should fail
    if result.status_code == 503 and not should_ping:
        return 1
    elif result.status_code == 200 and should_ping:
        return 1
    return 0


def test_scenario(name, pingable, psetup, pclear):
    total_result = 0;  # add a +1 for each passed test
    total_expected = 3;

    # There should not be a ping beforehand
    total_result += call_pingagle(pingable, should_ping=False)

    # Now there should be a ping, after adding the rule that Kilda should add when running
    for setup in psetup:
        setup()
    total_result += call_pingagle(pingable, should_ping=True)

    # After removing rules, ping shouldn't work
    for clear in pclear:
        clear()
    total_result += call_pingagle(pingable, should_ping=False)

    if total_result == total_expected:
        print ("\n{} ... ALL TESTS PASSED\n".format(name))
    else:
        print ("\n{} ... FAILURE: {} of {} passed\n".format(name, total_result, total_expected))


s2 = "00000002"
s3 = "00000003"
s4 = "00000004"
s5 = "00000005"
s6 = "00000006"


def test_single_switch_scenario():
    """
    This test is similar to the Kilda single switch test(s), but without kilda issuing the rules.
    The intent is to confirm that the test harness works (ie pingable works as intended), but also
    serve as an example of what the rules may look like for the kilda deployment.

    Examples:   # flows without transit vlans and intermediate switches
      | flow_id |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
      | c1none  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:03 |         2        |        0         |   10000   |

    """

    pingable = partial(test_ping,s2,2,0,s4,1,0)
    psetup = [ partial(mininet_rest.add_single_switch_rules, s3, 1, 2, 0, 0 ) ]
    pclear = [ partial(mininet_rest.clear_single_switch_rules, s3, 1, 2) ]
    test_scenario("test_single_switch_scenario", pingable, psetup, pclear)


def test_two_switch_scenario():
    """
    Examples:   # flows with transit vlans and without intermediate switches
    | flow_id |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
    | c2none  | de:ad:be:ef:00:00:00:03 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
    """

    pingable = partial(test_ping, s2, 2, 0, s5, 1, 0)
    psetup = [
        partial( mininet_rest.add_single_switch_rules, s3, 1, 2, 0, 0 ),
        partial( mininet_rest.add_single_switch_rules, s4, 1, 2, 0, 0 )
    ]
    pclear = [
        partial(mininet_rest.clear_single_switch_rules, s3, 1, 2),
        partial(mininet_rest.clear_single_switch_rules, s4, 1, 2)
    ]
    test_scenario("test_two_switch_scenario", pingable, psetup, pclear)


def test_three_switch_scenario():
    """
    Examples:   # flows with transit vlans and intermediate switches
    | flow_id |      source_switch      | source_port | source_vlan |   destination_switch    | destination_port | destination_vlan | bandwidth |
    | c3none  | de:ad:be:ef:00:00:00:02 |      1      |      0      | de:ad:be:ef:00:00:00:04 |         2        |        0         |   10000   |
    """

    pingable = partial(test_ping, s2, 2, 0, s6, 1, 0)
    psetup = [
        partial( mininet_rest.add_single_switch_rules, s3, 1, 2, 0, 0 ),
        partial( mininet_rest.add_single_switch_rules, s4, 1, 2, 0, 0 ),
        partial( mininet_rest.add_single_switch_rules, s5, 1, 2, 0, 0 )
    ]
    pclear = [
        partial(mininet_rest.clear_single_switch_rules, s3, 1, 2),
        partial(mininet_rest.clear_single_switch_rules, s4, 1, 2),
        partial(mininet_rest.clear_single_switch_rules, s5, 1, 2)
    ]
    test_scenario("test_three_switch_scenario", pingable, psetup, pclear)


def main():
    mininet_rest.init()
    create_topology()
    test_single_switch_scenario()
    test_two_switch_scenario()
    test_three_switch_scenario()
    cleanup()



if __name__ == '__main__':
    main()
