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
import py2neo
from werkzeug import exceptions as http_errors

import logging
import json
import ConfigParser

from app import application
from . import neo4j_tools

logger = logging.getLogger(__name__)
logger.info ("My Name Is: %s", __name__)

# Adjust the logging level for NEO
logging.getLogger('neo4j.bolt').setLevel(logging.INFO)


config = ConfigParser.RawConfigParser()
config.read('topology_engine_rest.ini')

neo4j_connect = neo4j_tools.connect(config)

@application.route('/api/v1/topology/network')
@login_required
def api_v1_network():
    """
    2017.03.08 (carmine) - this is now identical to api_v1_topology.
    :return: the switches and links
    """

    query = 'MATCH (n) return n'
    topology = []
    for record in neo4j_connect.data(query):
        record = record['n']
        relations = [
            rel['dst_switch']
            for rel in neo4j_connect.match(record, rel_type='isl')]
        relations.sort()
        topology.append({
            'name': record['name'],
            'outgoing_relationships': relations
        })

    topology.sort(key=lambda x:x['name'])
    topology = {'nodes': topology}

    return json.dumps(topology, default=lambda o: o.__dict__, sort_keys=True)


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
    edges = []
    for record in neo4j_connect.data('MATCH (n) return n'):
        source = record['n']

        for rel in neo4j_connect.match(source, rel_type='isl'):
            dest = rel.end_node()

            s = Link()
            s.label = source['name']
            s.id = py2neo.remote(source)._id

            t = Link()
            t.label = dest['name']
            t.id = py2neo.remote(dest)._id

            edge = Edge()
            edge.value = "{} to {}".format(s.label, t.label)
            edge.source = s
            edge.target = t

            edges.append(edge)

    edges.sort(key=lambda x: (x.source['id'], x.target['id']))
    nodes = Nodes()
    nodes.edges = edges

    return nodes.toJSON()


@application.route('/api/v1/topology/clear')
@login_required
def api_v1_topo_clear():
    """
    Clear the entire topology
    :returns the result of api_v1_network() after the delete
    """
    query = 'MATCH (n) detach delete n'
    neo4j_connect.run(query)
    return api_v1_network()


@application.route('/topology/network', methods=['GET'])
@login_required
def topology_network():
    return render_template('topologynetwork.html')


@application.route('/api/v1/topology/flows')
@login_required
def api_v1_topology_flows():
    try:
        query = "MATCH (a:switch)-[r:flow]->(b:switch) RETURN r"
        result = neo4j_connect.data(query)
        flows = [format_flow(raw['r']) for raw in result]
        return json.dumps(flows)
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/api/v1/topology/flows/<flow_id>')
@login_required
def api_v1_topology_get_flow(flow_id):
    query = (
        "MATCH (a:switch)-[r:flow]->(b:switch)\n"
        "WHERE r.flowid = {flow_id}\n"
        "RETURN r")
    result = neo4j_connect.data(query, flow_id=flow_id)
    if not result:
        return http_errors.NotFound(
                'There is no flow with flow_id={}'.format(flow_id))
    if len(result) < 2:
        return http_errors.NotFound('Flow data corrupted (too few results)')
    elif 2 < len(result):
        return http_errors.NotFound('Flow data corrupted (too many results)')

    flow_pair = [format_flow(record['r']) for record in result]
    flow_pair.sort(key=lambda x: is_forward_cookie(x['cookie']))
    flow_data = dict(zip(['reverse', 'forward'], flow_pair))

    return jsonify(flow_data)


# =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
#
# BEGIN: Link Properties section
#
# =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
PRIMARY_KEYS = ['src_switch', 'src_port', 'dst_switch', 'dst_port']

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
    logger.debug("CALLED LINK/PROPS with method {}, body {} ", request.method, request.data)
    try:
        if request.method == 'PUT':
            return handle_put_props(request.get_json(force=True))

        if request.method == 'DELETE':
            return handle_delete_props(request.get_json(force=True))

        if request.method == 'GET':
            return handle_get_props(request.args)
    except Exception as e:
        logger.error("Uncaught Exception in link_props: %s", e)
        logger.error("The data that caused the exception: %s", request.data)
        # TODO: this should augment the response status..
        return jsonify({"Error": "Uncaught Exception"})


def handle_get_props(args):
    """
    Return the link_props that match the arguments provided. If not args are provided, then all
    link props will be returned.

    :param args: The http request args .. ie URL/ROUTE?arg1=val1&arg2=val2
    :return: The nodes that match the query
    """
    query_set = ' '
    result = {}
    for key in PRIMARY_KEYS:
        val = args.get(key)
        if val:
            # make sure val doesn't have quotes - " or '
            val=val.replace('"','').replace("'",'')
            if key in ['src_switch', 'dst_switch']:
                query_set += ' %s:"%s",' % (key, val)
            else:
                query_set += ' %s:%s,' % (key, val)
    try:
        # NB: query_set could be just whitespace .. Neo4j is okay with empty props in the query - ie { }
        query = 'MATCH (lp:link_props { %s }) RETURN lp' % query_set[:-1] # remove trailing ',' if there
        result = neo4j_connect.data(query)
    except Exception as e:
        # TODO: ensure this error augments the reponse http status code
        logger.error("Exception trying to get link_props: %s", e)
        raise e

    #
    # The results are returned "flat" .. id primary keys and extra properties at the same level.
    # But, current expectation is that extra props are all part of the 'props' key.
    # So, pull out both sets (primary / extra props) and then combine into the expected order.
    #
    # TODO: Should we change the response to be flat so that we don't need to do this processing?
    #           A corollary - the inbound put could be flat as well.
    #
    hier_results = []
    for row in [dict(x['lp']) for x in result]:
        not_primary_props = {k:v for k,v in row.items() if k not in PRIMARY_KEYS}
        # The API contract is to return values as strings .. which affects how json objects are created / read.
        # So, loop through all values.
        for k,v in not_primary_props.items():
            not_primary_props[k] = str(v)       # ensure all values are strings .. which fulfills the API contract
        primary_props = {k:v for k,v in row.items() if k in PRIMARY_KEYS}
        primary_props['props'] = not_primary_props
        hier_results.append(primary_props)

    rj = jsonify(hier_results)
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
            "src_switch":"de:ad:be:ef:01:11:22:01",
            "src_port":"1" ,
            "dst_switch":"de:ad:be:ef:02:11:22:02",
            "dst_port":"2"
        } , {
            # Delete all links with this switch as src
            # ========================================
            "src_switch":"de:ad:be:ef:03:33:33:03"
        }]


    NB: At least one primary key needs to be passed in. Otherwise, all link_props would be deleted,
        which is probably some other kinds of operation. This could be a feature .. and should only
        be enabled if the user sends in a specific k/v, like "drop_all:True"

    :param props: one or more primary keys.
    :return: success / row count of affected  *or* failure AND failure message
    """
    query_set = ''
    for key in PRIMARY_KEYS:
        val = props.get(key)
        if val:
            if key in ['src_switch', 'dst_switch']:
                query_set += ' %s:"%s",' % (key, val)
            else:
                query_set += ' %s:%s,' % (key, val)

    if len(query_set) > 0:
        # need to do 2 things:  (1) delete props from ISLs; (2) delete the link_props row(s)
        # TODO: Wrap the whole thing in a transaction so that we know we've cleaned up properly
        # TODO: The transaction could be on a row-by-row basis (ie one link_props / one link)
        query_set = query_set[:-1] # remove trailing ','
        query = 'MATCH (lp:link_props { %s }) DETACH DELETE lp RETURN lp' % query_set
        result = neo4j_connect.data(query)

        affected = 0
        for row in [dict(x['lp']) for x in result]:
            # (1) - delete props from ISLs
            affected += 1
            remove_props_from_isl(row)

        logger.debug('\n DELETE QUERY = %s \n AFFECTED = %s', query, affected)
        return True, affected
    else:
        return False, "NO PRIMARY KEYS"


def remove_props_from_isl(row):
    """
    To remove props, we need to find out what extra props are in the link_props and remove only those
    :param row: a row from the delete link_props query
    """
    # TODO: extra for loops here .. but leveraging existing code patterns .. try to generalize
    # NB: this happens during delete link_props .. doesn't need to be super efficient.
    not_primary_props = {k:v for k,v in row.items() if k not in PRIMARY_KEYS}
    primary_props = {k:v for k,v in row.items() if k in PRIMARY_KEYS}
    success, src_sw, src_pt, dst_sw, dst_pt = get_link_prop_keys(primary_props)
    query_remove = ''
    for k,_ in not_primary_props.iteritems():
        query_remove += ' i.%s,' % k

    if len(query_remove) > 0:
        query_remove = query_remove[:-1] # remove trailing ','

        query = 'MATCH (src:switch)-[i:isl]->(dst:switch) '
        query += ' WHERE i.src_switch = "%s" AND i.src_port = %s AND i.dst_switch = "%s" AND i.dst_port = %s ' % (src_sw, src_pt, dst_sw, dst_pt)
        query += ' REMOVE %s' % query_remove
        neo4j_connect.data(query)


def put_link_props(props):
    """
    This will put properties into the link_props table, if the set of primary keys is passed in.
    It will create or update the node, and only the properties passed in be created/updated.
    It is okay to pass in no additional properties (ie just primary keys is okay).
    It will not delete any properties - if the put omits some extra props, they'll still be there.

    Here's an example of a call that works:

        [{
            "src_switch":"de:ad:be:ef:01:11:22:01",
            "src_port":"1" ,
            "dst_switch":"de:ad:be:ef:02:11:22:02",
            "dst_port":"2"
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
    success, src_sw, src_pt, dst_sw, dst_pt = get_link_prop_keys(props)
    if not success:
        return False, 'PRIMARY KEY VIOLATION: one+ primary keys are empty: src_switch:"%s", src_port:"%s", dst_switch:"%s", dst_port:"%s"' % (src_sw, src_pt, dst_sw, dst_pt)
    else:
        # (1) Create the link_props and then (2) propagate to the isl if it exists
        query_merge = 'MERGE (lp:link_props { src_switch:"%s", src_port:%s, dst_switch:"%s", dst_port:%s })' % (src_sw, src_pt, dst_sw, dst_pt)
        query_set, success, message = build_props_query(props.get('props', {}), 'lp')
        if not success:
            return False, message
        query = query_merge + query_set
        logger.debug('\n LINK_PROP QUERY = %s ', query)
        neo4j_connect.data(query)

        # (2) Now propagate the props if an ISL exists .. this exact same query will be used in TE
        #     when a new ISL is created - it only works if both entities exist.
        query = 'MATCH (src:switch)-[i:isl]->(dst:switch) '
        query += ' WHERE i.src_switch = "%s" AND i.src_port = %s AND i.dst_switch = "%s" AND i.dst_port = %s ' % (src_sw, src_pt, dst_sw, dst_pt)
        query += ' MATCH (lp:link_props) '
        query += ' WHERE lp.src_switch = "%s" AND lp.src_port = %s AND lp.dst_switch = "%s" AND lp.dst_port = %s ' % (src_sw, src_pt, dst_sw, dst_pt)
        # (3) Apply the link properties .. based on what was passed in.
        # NB: This only ever applies to the link what was passed in.  If it is a subset, then old values won't be added as part of this.
        query_set, success, _ = build_props_query(props.get('props', {}), 'i')
        if success:
            query += query_set

        logger.debug('\n LINK_PROP -> ISL QUERY = %s ', query)
        neo4j_connect.data(query)

        return True, 1


def build_props_query(props, var_name):
    """
    For use in a "SET" .. build the query, and error if a reserved word is used

    :param props: The props
    :param var_name: The name of the variable to assign to ... ie lp.cost = , or i.cost =
    :return:
    """
    reserved_words = ['latency', 'speed', 'available_bandwidth', 'status']
    query_set = ''
    if len(props) > 0:
        for (k,v) in props.iteritems():
            if k in reserved_words:
                return query_set, False, 'RESERVED WORD VIOLATION: do not use %s' % k
            if v.isdigit():
                query_set += var_name + '.%s = %s, ' % (k, v)
            elif v.startswith("0x"):
                query_set += var_name + '.%s = %s, ' % (k, int(v,16))
            else:
                query_set += var_name + '.%s = "%s", ' % (k, v)
        query_set = ' SET ' + query_set[:-2]
    return query_set, True, ''


def get_link_prop_keys(props):
    """
    This function can be used to pull out the primary keys from a dictionary, and return success
    iff all primary keys are present.
    :param props: the dict
    :return: all primary keys, and success if they were all present.
    """
    src_sw = props.get('src_switch','')
    src_pt = props.get('src_port','')
    dst_sw = props.get('dst_switch','')
    dst_pt = props.get('dst_port','')
    success = (len(src_sw) > 0) and (len(src_pt) > 0) and (len(dst_sw) > 0) and (len(dst_pt) > 0)
    return success,src_sw,src_pt,dst_sw,dst_pt


# =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
# END: Link Properties section
# =-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=


def format_isl(link):
    """
    :param link: A valid Link returned from the db
    :return: A dictionary in the form of org.openkilda.messaging.info.event.IslInfoData
    """
    isl =  {
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
        'state': get_isl_state(link),
        'available_bandwidth': link['available_bandwidth']
    }

    # fields that have already been used .. should find easier way to do this..
    already_used = list(isl.keys())
    for k,v in link.iteritems():
        if k not in already_used:
            isl[k] = v

    return isl


def get_isl_state(link):
    if link['status'] == 'active':
        return 'DISCOVERED'
    elif link['status'] == 'moved':
        return 'MOVED'
    else:
        return 'FAILED'


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


def format_flow(raw_flow):
    flow = raw_flow.copy()

    path = json.loads(raw_flow['flowpath'])
    path['clazz'] = 'org.openkilda.messaging.info.event.PathInfoData'

    flow['flowpath'] = path

    return flow


@application.route('/api/v1/topology/links')
@login_required
def api_v1_topology_links():
    """
    :return: all isl relationships in the database
    """
    try:
        query = "MATCH (a:switch)-[r:isl]->(b:switch) RETURN r"
        result = neo4j_connect.data(query)

        links = []
        for link in result:
            neo4j_connect.pull(link['r'])
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
        result = neo4j_connect.data(query)

        switches = []
        for sw in result:
            neo4j_connect.pull(sw['n'])
            switches.append(format_switch(sw['n']))

        application.logger.info('switches found %d', len(result))

        return jsonify(switches)
    except Exception as e:
        return "error: {}".format(str(e))


@application.route('/api/v1/topology/links/bandwidth/<src_switch>/<int:src_port>')
@login_required
def api_v1_topology_link_bandwidth(src_switch, src_port):
    query = (
        "MATCH (a:switch)-[r:isl]->(b:switch) "
        "WHERE r.src_switch = '{}' AND r.src_port = {} "
        "RETURN r.available_bandwidth").format(src_switch, int(src_port))

    return neo4j_connect.data(query)[0]['r.available_bandwidth']


@application.route('/api/v1/topology/routes/src/<src_switch>/dst/<dst_switch>')
@login_required
def api_v1_routes_between_nodes(src_switch, dst_switch):
    depth = request.args.get('depth') or '10'
    query = (
        "MATCH p=(src:switch{{name:'{src_switch}'}})-[:isl*..{depth}]->"
        "(dst:switch{{name:'{dst_switch}'}}) "
        "WHERE ALL(x IN NODES(p) WHERE SINGLE(y IN NODES(p) WHERE y = x)) "
        "WITH RELATIONSHIPS(p) as links "
        "WHERE ALL(l IN links WHERE l.status = 'active') "
        "RETURN links"
    ).format(src_switch=src_switch, depth=depth, dst_switch=dst_switch)

    result = neo4j_connect.data(query)

    paths = []
    for links in result:
        current_path = []
        for isl in links['links']:
            path_node = build_path_nodes(isl, len(current_path))
            current_path.extend(path_node)

        paths.append(build_path_info(current_path))

    return jsonify(paths)


def build_path_info(path):
    return {
        'clazz': 'org.openkilda.messaging.info.event.PathInfoData',
        'path': path
    }


def build_path_nodes(link, seq_id):
    nodes = []
    src_node = {
        'clazz': 'org.openkilda.messaging.info.event.PathNode',
        'switch_id': link['src_switch'],
        'port_no': int(link['src_port']),
        'seq_id': seq_id
    }
    nodes.append(src_node)

    dst_node = {
        'clazz': 'org.openkilda.messaging.info.event.PathNode',
        'switch_id': link['dst_switch'],
        'port_no': int(link['dst_port']),
        'seq_id': seq_id + 1
    }
    nodes.append(dst_node)
    return nodes


# FIXME(surabujin): stolen from topology-engine code, must use some shared
# codebase
def is_forward_cookie(cookie):
    return int(cookie) & 0x4000000000000000
