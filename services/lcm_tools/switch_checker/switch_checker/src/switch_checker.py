#!/usr/bin/env python

from py2neo import Graph

import argparse
import logging
import requests
import sys

logger = logging.getLogger()
logging.basicConfig(
    format='%(asctime)s.%(msecs)s:%(name)s:%(thread)d:%(levelname)s:%(process)d:%(message)s',
    level=logging.INFO
)


def parse_cmd_line():
    parser = argparse.ArgumentParser(description='Validate Switch Rules')
    parser.add_argument('switch_id', type=str, help='Switch dpid')
    parser.add_argument('neo_host', type=str, help='Neo4j host name or IP')
    parser.add_argument('neo_user', type=str, help='Neo4j user name')
    parser.add_argument('neo_passwd', type=str, help='Neo4j password')
    parser.add_argument('kilda_host', type=str, help='Kilda Northbound host name or IP')
    parser.add_argument('kilda_user', type=str, help='Kilda user name')
    parser.add_argument('kilda_passwd', type=str, help='Kilda password')
    parser.add_argument('--neo_scheme', type=str, help='Neo4j scheme name', default='http')
    parser.add_argument('--neo_port', type=int, help='Neo4j port', default=7474)
    parser.add_argument('--neo_secure', type=bool, help='Neo4j secure', default=False)
    parser.add_argument('--kilda_scheme', type=str, help='Kilda scheme name', default='http')
    parser.add_argument('--kilda_port', type=str, help='Kilda port', default=8080)

    return parser.parse_args()


def get_db_connection(args):
    uri = "{}://{}:{}/db/data".format(
        args.neo_scheme, args.neo_host, args.neo_port)
    logger.info("openning connection to neo (%s)", uri)
    return Graph(
        uri,
        user=args.neo_user,
        password=args.neo_passwd,
        secure=args.neo_secure,
        http_port=args.neo_port
)


def query_neo(graph, query):
    logger.debug("executing neo query: %s", query)
    return graph.run(query).data()


def get_flow_segments(graph, switch_id):
    query = "MATCH (:switch)-[flow_segment:flow_segment]->(:switch) WHERE flow_segment.dst_switch=\"{sw_id}\" RETURN DISTINCT flow_segment"\
        .format(sw_id=switch_id)
    return query_neo(graph, query)


def get_flows(graph, switch_id):
    query = "MATCH (switch:switch)-[flow:flow]->(:switch) WHERE switch.name=\"{sw_id}\" RETURN DISTINCT flow.cookie"\
        .format(sw_id=switch_id)
    return query_neo(graph, query)


def get_cookies_for_segment(graph, switch_id):
    cookies = set()
    segments = get_flow_segments(graph, switch_id)
    for segment in segments:
        cookies.add(segment['flow_segment']['cookie'])
    return cookies


def get_cookies_for_flow(graph, switch_id):
    cookies = set()
    flows = get_flows(graph, switch_id)
    for flow in flows:
        cookies.add(flow['flow.cookie'])
    return cookies


def get_switch_rules(args):
    uri = "{}://{}:{}/api/v1/switches/{}/rules".format(
        args.kilda_scheme, args.kilda_host, args.kilda_port, args.switch_id)
    logger.debug("executing get request: %s", uri)
    rules = requests.get(uri, auth=(args.kilda_user, args.kilda_passwd))
    if rules.status_code <> 200:
        logger.error("Error getting switch rules from %s", uri)
        sys.exit(1)
    return rules.json()

def get_switch_cookies(args):
    rules = get_switch_rules(args)

    cookies = set()
    for rule in rules['flows']:
        cookies.add(rule['cookie'])
    return cookies


def print_lists(name, list):
    print "{}: {}".format(name, len(list))
    if len(list) > 0:
        for item in list:
            print "\t",  item


def main(args):
    graph = get_db_connection(args)
    segment_cookies = get_cookies_for_segment(graph, args.switch_id)
    flow_cookies = get_cookies_for_flow(graph, args.switch_id)
    switch_cookies = get_switch_cookies(args)

    missing_rule = segment_cookies.union(flow_cookies) - switch_cookies
    extra_rule = (switch_cookies - segment_cookies) - flow_cookies
    proper_rule = switch_cookies - extra_rule


    print "\n"

    print "number of switch rules:  {}".format(len(switch_cookies))
    print "number of flow segments: {}".format(len(segment_cookies))
    print "number of ingress flows: {}".format(len(flow_cookies))
    print_lists("missing rules", missing_rule)
    print_lists("extra rules", extra_rule)
    print_lists("proper rules", proper_rule)


if __name__ == "__main__":
    logger.info("Starting switch_checker")
    args = parse_cmd_line()
    main(args)