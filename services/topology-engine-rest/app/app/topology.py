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

from flask import Flask, flash, redirect, render_template, request, session, abort, url_for, Response, jsonify
from flask_login import LoginManager, UserMixin, login_required, login_user, logout_user, current_user
from py2neo import Graph

from app import application
from app import db
from app import utils

import sys, os
import logging
import requests
import json
import ConfigParser

logger = logging.getLogger(__name__)
logging.getLogger('neo4j.bolt').setLevel(logging.INFO)
logger.info ("My Name Is: %s", __name__)
config = ConfigParser.RawConfigParser()
config.read('topology_engine_rest.properties')

NEO4J_HOST = os.environ['neo4jhost'] or config.get('neo4j', 'host')
NEO4J_USER = os.environ['neo4juser'] or config.get('neo4j', 'user')
NEO4J_PASS = os.environ['neo4jpass'] or config.get('neo4j', 'pass')
NEO4J_BOLT = os.environ['neo4jbolt'] or config.get('neo4j', 'bolt')
AUTH = (NEO4J_USER, NEO4J_PASS)

@application.route('/api/v1/topology/network')
@login_required
def api_v1_network():
    """
    2017.03.08 (carmine) - this is now identical to api_v1_topology.
    :return: the switches and links
    """

    try:
        data = {'query' : 'MATCH (n) return n'}

        result_switches = requests.post(NEO4J_BOLT, data=data, auth=AUTH)
        j_switches = json.loads(result_switches.text)
        nodes = []
        topology = {}
        for n in j_switches['data']:
            for r in n:
                node = {}
                node['name'] = (r['data']['name'])
                result_relationships = requests.get(str(r['outgoing_relationships']), auth=AUTH)
                j_paths = json.loads(result_relationships.text)
                outgoing_relationships = []
                for j_path in j_paths:
                    if j_path['type'] == u'isl':
                        outgoing_relationships.append(j_path['data']['dst_switch'])
                    outgoing_relationships.sort()
                    node['outgoing_relationships'] = outgoing_relationships
            nodes.append(node)
        topology['nodes'] = nodes
        return str(json.dumps(topology, default=lambda o: o.__dict__, sort_keys=True))
    except Exception as e:
        return "error: {}".format(str(e))


class Nodes(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

class Edge(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

class Link(object):
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=False, indent=4)

@application.route('/api/v1/topology/nodes')
@login_required
def api_v1_topology_nodes():
    data = {'query' : 'MATCH (n) return n'}
    result_switches = requests.post(NEO4J_BOLT, data=data, auth=AUTH)
    j_switches = json.loads(result_switches.text)
    nodes = Nodes()
    nodes.edges = []
    for n in j_switches['data']:
        for r in n:
            result_relationships = requests.get(str(r['outgoing_relationships']), auth=auth)
            j_paths = json.loads(result_relationships.text)
            outgoing_relationships = []
            for j_path in j_paths:
                target = Link()
                if j_path['type'] == u'isl':
                    edge = Edge()
                    source = Link()
                    source.label = r['data']['name']
                    source.id = r['metadata']['id']
                    dest_node = requests.get(str(j_path['end']), auth=auth)
                    j_dest_node = json.loads(dest_node.text)
                    target.label = j_path['data']['dst_switch']
                    target.id = j_dest_node['metadata']['id']
                    edge.value = "{} to {}".format(source.label, target.label)
                    edge.source = source
                    edge.target = target
                    nodes.edges.append(edge)
    return nodes.toJSON()


@application.route('/api/v1/topology/clear')
@login_required
def api_v1_topo_clear():
    """
    Clear the entire topology
    :returns the result of api_v1_network() after the delete
    """
    try:
        data = {'query' : 'MATCH (n) detach delete n'}
        requests.post(NEO4J_BOLT, data=data, auth=AUTH)
        return api_v1_network()
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/topology/network', methods=['GET'])
@login_required
def topology_network():
    return render_template('topologynetwork.html')


def create_p2n_driver():
    graph = Graph("http://{}:{}@{}:7474/db/data/".format(
        NEO4J_USER, NEO4J_PASS, NEO4J_HOST))
    return graph

graph = create_p2n_driver()


@application.route('/api/v1/topology/flows')
@login_required
def api_v1_topology_flows():
    try:
        query = "MATCH (a:switch)-[r:flow]->(b:switch) RETURN r"
        result = graph.data(query)

        flows = []
        for data in result:
            path = json.loads(data['r']['flowpath'])
            flow = json.loads(json.dumps(data['r'],
                                         default=lambda o: o.__dict__,
                                         sort_keys=True))
            flow['flowpath'] = path
            flows.append(flow)

        return str(json.dumps(flows, default=lambda o: o.__dict__, sort_keys=True))
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/api/v1/topology/link/props', methods=['GET','PUT','DELETE'])
@login_required
def api_v1_link_props():
    """
    This function handles the CRUD for link properties. Link properties are used in associating
    external costs a link. It can be used to associate other properties with a link as well.

    Because some of the links may not exist yet, the properties are stored in link_props nodes,
    where the name of the node is identical to the name of the link (eg leverage switch/port for
    both src and dst

    NB: We use PUT for both create and for update.

    The format for PUT:

        [{src_switch:val, src_port:val, dst_switch:val, dst_port:val, props:{key:value,..}},..]

    The format for DELETE:

        Delete all props for link: [{src_switch:val, src_port:val, dst_switch:val, dst_port:val},..]
        Delete some props: [{src_switch:val, src_port:val, dst_switch:val, dst_port:val},props:{key:value,..}},..]
        Delete all nodes: {all=true}

    The format for GET:

        Get All link props:   no args
        Get some link props: ?[src_switch=X][&src_port=XX][&dst_switch=Z][&dst_port=ZZ]
        -- ie all args are optional .. if supplied, they'll be used.


    :return: the result of the operation for PUT and DELETE, otherwise the link properties.
    """
    try:
        if request.method == 'PUT':
            return handle_put_props(request.get_json(force=True))

        if request.method == 'DELETE':
            return handle_delete_props(request.get_json(force=True))

        if request.method == 'GET':
            return handle_get_props(request.args)
    except Exception as e:
        logger.error("Uncaught Exception in link_props: %s", e)
        return jsonify({"Error": "Uncaught Exception"})


def handle_get_props(args):
    """
    Return the link_props that match the arguments provided. If not args are provided, then all
    link props will be returned.

    :param args: The http request args .. ie URL/ROUTE?arg1=val1&arg2=val2
    :return: The nodes that match the query
    """
    primary_keys = ['src_sw', 'src_pt', 'dst_sw', 'dst_pt']
    query_set = ' '
    result = {}
    for key in primary_keys:
        val = args.get(key)
        if val:
            # make sure val doesn't have quotes
            val=val.replace('"','').replace("'",'')
            query_set += ' %s:"%s",' % (key, val)
    try:
        # NB: query_set could be just whitespace .. Neo4j is okay with empty props in the query - ie { }
        query = 'MATCH (lp:link_props { %s }) RETURN lp' % query_set[:-1] # remove trailing ',' if there
        result = graph.data(query)
    except Exception as e:
        logger.error ("Exception trying to get link_props: %s", e)

    rj = jsonify(result)
    logger.debug("results: %s", rj)
    return rj


def handle_delete_props(j):
    """
    Deletes the appropriate nodes. Whereas the "put_props" needs all primary keys, this delete
    will work on specific links as well as a family of links - ie specify only src switch, or
    only dst switch. As another example, you could specify just the src_port.

    :param j: The json body of the request
    :return:  {successes=X, failures=Y, messages=[M]} ; Successes = rows affected, could be greater
                than the number of requests if not all primary keys were provided (ie wildcard)
    """
    #
    # TODO: handle_put_props should have unit test .. but currently relying on acceptance tests.
    #
    return handle_props(j, del_link_props)


def handle_put_props(j):
    """
    This will create or update all properties that have been passed in.

    :param j: the json body. For details, look at put_link_props.
    :return: {successes=X, failures=Y, messages=[M]}
    """
    return handle_props(j, put_link_props)


def handle_props(j, action):
    """
    This will process each row, calling 'action'

    :param j: the json body. For details, look at put_link_props / del_link_props
    :param action: the function to call per row
    :return: {successes=X, failures=Y, messages=[M]}
    """

    #
    # TODO: handle_put_props should have unit test .. but currently relying on acceptance tests.
    #
    success = 0
    failure = 0
    if isinstance(j, dict):
        j = [j]

    msgs = []
    for props in j:
        try:
            # a bit dodgy .. result is a number if worked, otherwise a message
            worked, result = action(props)
            if worked:
                success += result
            else:
                failure += 1
                msgs.append(result)
        except Exception as e:
            msgs.append('Exception occurred: %s' % e)

    return jsonify(successes=success, failures=failure, messages=msgs)


def del_link_props(props):
    """
    This will delete one or more nodes if they match the pattern. Here's a couple of examples:

        [{
            # Delete a specific node
            # ======================
            "src_sw":"de:ad:be:ef:01:11:22:01",
            "src_pt":"1" ,
            "dst_sw":"de:ad:be:ef:02:11:22:02",
            "dst_pt":"2"
        } , {
            # Delete all links with this switch as src
            # ========================================
            "src_sw":"de:ad:be:ef:03:33:33:03"
        }]


    NB: At least one primary key needs to be passed in. Otherwise, all link_props would be deleted,
        which is probably some other kinds of operation. This could be a feature .. and should only
        be enabled if the user sends in a specific k/v, like "drop_all:True"

    :param props: one or more primary keys.
    :return: success / row count of affected  *or* failure AND failure message
    """
    query_set = ''
    for k,v in props.iteritems():
        if k != 'props':
            # in case the user inadvertently added props, eg copy/paste issue, ignore it.
            query_set += ' %s:"%s",' % (k,v)

    if len(query_set) > 0:
        query = 'MATCH (lp:link_props { %s }) DETACH DELETE lp RETURN COUNT(lp) as affected' % query_set[:-1] # remove trailing ','
        result = graph.data(query)
        affected = result[0].get('affected', 0)
        logger.debug('\n DELETE QUERY = %s \n AFFECTED = %s', query, affected)
        return True, affected
    else:
        return False, "NO PRIMARY KEYS"


def put_link_props(props):
    """
    This will put properties into the link_props table, if the set of primary keys is passed in.
    It will create or update the node, and only the properties passed in be created/updated.
    It is okay to pass in no additional properties (ie just primary keys is okay).
    It will not delete any properties - if the put omits some extra props, they'll still be there.

    Here's an example of a call that works:

        [{
            "src_sw":"de:ad:be:ef:01:11:22:01",
            "src_pt":"1" ,
            "dst_sw":"de:ad:be:ef:02:11:22:02",
            "dst_pt":"2" ,
            "props": {
                "cost":"1",
                "popularity":"5",
                "jon":"grumpy"
            }
        }]

    NB: This method will also ensure no properties are added that are reserved. At the time of this
    writing the reservered words are:
        - latency, speed, available_bandwidth, status

    :param props: a single node
    :return: success / row count of 1  *or* failure AND failure message
    """
    #
    # TODO: Add warning if we are adding a link to a "preoccupied" src sw/pt or dst sw/pt
    #       - ie can't, or shouldn't, have the same src sw/pt leading to different dst .. so, will
    #           be better to at least warn the user that there is another link .. this is to help
    #           the use uncover a possible bug in their data set.
    #
    reserved_words = ['latency', 'speed', 'available_bandwidth', 'status']

    success, src_sw, src_pt, dst_sw, dst_pt = get_link_prop_keys(props)
    if not success:
        return False, 'PRIMARY KEY VIOLATION: one+ primary keys are empty: src_sw:"%s", src_pt:"%s", dst_sw:"%s", dst_pt:"%s"' % (src_sw, src_pt, dst_sw, dst_pt)
    else:
        query_merge = 'MERGE (lp:link_props { src_sw:"%s", src_pt:"%s", dst_sw:"%s", dst_pt:"%s" })' % (src_sw, src_pt, dst_sw, dst_pt)
        query_set = ''

        new_props = props.get('props',{})
        if len(new_props) > 0:
            for (k,v) in new_props.iteritems():
                if k in reserved_words:
                    return False, 'RESERVED WORD VIOLATION: do not use %s' % k
                query_set += 'lp.%s = "%s", ' % (k, v)
            query_set = ' SET ' + query_set[:-2]

        query = query_merge + query_set
        graph.data(query)
        logger.debug('\n QUERY = %s ', query)
        return True, 1


def get_link_prop_keys(props):
    """
    This function can be used to pull out the primary keys from a dictionary, and return success
    iff all primary keys are present.
    :param props: the dict
    :return: all primary keys, and success if they were all present.
    """
    src_sw = props.get('src_sw','')
    src_pt = props.get('src_pt','')
    dst_sw = props.get('dst_sw','')
    dst_pt = props.get('dst_pt','')
    success = (len(src_sw) > 0) and (len(src_pt) > 0) and (len(dst_sw) > 0) and (len(dst_pt) > 0)
    return success,src_sw,src_pt,dst_sw,dst_pt


def format_isl(link):
    """
    :param link: A valid Link returned from the db
    :return: A dictionary in the form of org.openkilda.messaging.info.event.IslInfoData
    """
    return {
        'clazz': 'org.openkilda.messaging.info.event.IslInfoData',
        'latency_ns': int(link['latency']),
        'path': [{'switch_id': link['src_switch'],
                  'port_no': int(link['src_port']),
                  'seq_id': 0,
                  'segment_latency': int(link['latency'])},
                 {'switch_id': link['dst_switch'],
                  'port_no': int(link['dst_port']),
                  'seq_id': 1,
                  'segment_latency': 0}],
        'speed': link['speed'],
        'state': 'DISCOVERED' if link['status'] == 'active' else 'FAILED',
        'available_bandwidth': link['available_bandwidth']
    }

def format_switch(switch):
    """
    :param switch: A valid Switch returned from the db
    :return: A dictionary in the form of org.openkilda.messaging.info.event.SwitchInfoData
    """
    return {
        'clazz': 'org.openkilda.messaging.info.event.SwitchInfoData',
        'switch_id': switch['name'],
        'address': switch['address'],
        'hostname': switch['hostname'],
        'state': 'ACTIVATED' if switch['state'] == 'active' else 'DEACTIVATED',
        'description': switch['description']
    }


@application.route('/api/v1/topology/links')
@login_required
def api_v1_topology_links():
    """
    :return: all isl relationships in the database
    """
    try:
        query = "MATCH (a:switch)-[r:isl]->(b:switch) RETURN r"
        result = graph.data(query)

        links = []
        for link in result:
            links.append(format_isl(link['r']))

        application.logger.info('links found %d', len(result))

        return jsonify(links)
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/api/v1/topology/switches')
@login_required
def api_v1_topology_switches():
    """
    :return: all switches in the database
    """
    try:
        query = "MATCH (n:switch) RETURN n"
        result = graph.data(query)

        switches = []
        for sw in result:
            switches.append(format_switch(sw['n']))

        application.logger.info('switches found %d', len(result))

        return jsonify(switches)
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/api/v1/topology/links/bandwidth/<src_switch>/<src_port>')
@login_required
def api_v1_topology_link_bandwidth(src_switch, src_port):
    try:
        data = {'query': "MATCH (a:switch)-[r:isl]->(b:switch) "
                         "WHERE r.src_switch = '{}' AND r.src_port = {} "
                         "RETURN r.available_bandwidth".format(
                            str(src_switch), int(src_port))}

        response = requests.post(NEO4J_BOLT, data=data, auth=AUTH)
        data = json.loads(response.text)
        bandwidth = data['data'][0][0]

        return str(bandwidth)

    except Exception as e:
        return "error: {}".format(str(e))

