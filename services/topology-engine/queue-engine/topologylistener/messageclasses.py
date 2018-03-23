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

import logging

import gevent.lock
from py2neo.database import status as neo4j_errors

from topologylistener import config
from topologylistener import db
from topologylistener import exc
from topologylistener import flow_utils
from topologylistener import message_utils


raw_logger = logging.getLogger(__name__)

graph = flow_utils.graph
switch_states = {
    'active': 'ACTIVATED',
    'inactive': 'DEACTIVATED',
    'removed': 'REMOVED'
}

MT_SWITCH = "org.openkilda.messaging.info.event.SwitchInfoData"
MT_ISL = "org.openkilda.messaging.info.event.IslInfoData"
MT_PORT = "org.openkilda.messaging.info.event.PortInfoData"
MT_FLOW_INFODATA = "org.openkilda.messaging.info.flow.FlowInfoData"
MT_FLOW_RESPONSE = "org.openkilda.messaging.info.flow.FlowResponse"
MT_NETWORK = "org.openkilda.messaging.info.discovery.NetworkInfoData"
CD_NETWORK = "org.openkilda.messaging.command.discovery.NetworkCommandData"

# This is used for blocking on flow changes.
neo4j_update_lock = gevent.lock.RLock()


class MessageItem(object):
    def __init__(self, context, raw_request):
        self.context = context
        self.raw_request = raw_request

        self.log = self.context.log(raw_logger)

        self.payload = self.raw_request["payload"]
        self.kind = self.payload["clazz"]

    def handle(self):
        if self.kind == MT_SWITCH:
            if self.payload['state'] == "ADDED":
                event_handled = self.create_switch()
            elif self.payload['state'] == "ACTIVATED":
                event_handled = self.activate_switch()
            elif self.payload['state'] == "DEACTIVATED":
                event_handled = self.deactivate_switch()
            elif self.payload['state'] == "REMOVED":
                event_handled = self.remove_switch()
            else:
                raise exc.NoHandlerError

        elif self.kind == MT_ISL:
            if self.payload['state'] == "DISCOVERED":
                event_handled = self.create_isl()
            elif self.payload['state'] == "FAILED":
                event_handled = self.isl_discovery_failed()
            else:
                raise exc.NoHandlerError

        elif self.kind == MT_PORT:
            if self.payload['state'] == "DOWN":
                event_handled = self.port_down()
            else:
                event_handled = True

        elif self.kind == MT_FLOW_INFODATA:
            event_handled = self.flow_operation()

        elif self.kind == CD_NETWORK:
            event_handled = self.dump_network()

        else:
            raise exc.NoHandlerError

        # FIXME(surabujin): in most cases this suggestion is incorrect.
        if not event_handled:
            raise exc.RecoverableError

        # Cache topology expects to receive OFE events
        if self.kind in {MT_SWITCH, MT_SWITCH, MT_PORT}:
            message_utils.send_cache_message(self.context, self.payload)

    def activate_switch(self):
        switch_id = self.payload['switch_id']
        self.log.debug('Switch %s activation request', switch_id)

        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switch_id))
        if switch:
            graph.merge(switch)
            switch['state'] = "active"
            switch.push()
            self.log.info('Activating switch: %s', switch_id)
        return True

    def create_switch(self):
        switch_id = self.payload['switch_id']

        self.log.debug('Switch %s create request', switch_id)
        query = (
            "MERGE (a:switch{{name:'{}'}}) "
            "SET "
            "a.name='{}', "
            "a.address='{}', "
            "a.hostname='{}', "
            "a.description='{}', "
            "a.controller='{}', "
            "a.state = 'active' "
        ).format(
            switch_id, switch_id,
            self.payload['address'],
            self.payload['hostname'],
            self.payload['description'],
            self.payload['controller']
        )
        graph.run(query)

        self.log.info("Successfully created switch %s", switch_id)
        return True

    def deactivate_switch(self):
        switch_id = self.payload['switch_id']

        self.log.debug('Switch %s deactivation request', switch_id)
        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switch_id))
        if switch:
            graph.merge(switch)
            switch['state'] = "inactive"
            switch.push()
            self.log.info('Deactivating switch: %s', switch_id)
            return True

        else:
            self.log.warning('Switch %s does not exist in topology', switch_id)

        return True

    def remove_switch(self):
        switch_id = self.payload['switch_id']

        self.log.debug('Switch %s removing request', switch_id)

        try:
            switch = graph.find_one('switch',
                                    property_key='name',
                                    property_value='{}'.format(switch_id))
            if switch:
                graph.merge(switch)
                switch['state'] = "removed"
                switch.push()
                self.log.info('Removing switch: %s', switch_id)
                self.delete_isl(switch_id, None)
        except neo4j_errors.TransientError as e:
            raise exc.RecoverableError(e)

        return True

    @staticmethod
    def isl_exists(src_switch, src_port):
        if src_port:
            exists_query = ("MATCH (a:switch)-[r:isl {{"
                            "src_switch: '{}', "
                            "src_port: {}, status: 'active'}}]->(b:switch) return r")
            return graph.run(exists_query.format(src_switch, src_port)).data()
        else:
            exists_query = ("MATCH (a:switch)-[r:isl {{"
                            "src_switch: '{}', status: 'active'}}]->(b:switch) return r")
            return graph.run(exists_query.format(src_switch)).data()

    def delete_isl(self, src_switch, src_port):
        """
        Delete the ISL if it exists.

        Ideally, the result of this function is whether the relationship is gone (true if it is)

        :return: True always, unless an exception occurs
        """

        self.log.info(
                'Removing ISL, if it exists: src_switch=%s, src_port=%s',
                src_switch, src_port)

        if src_port:
            delete_query = ("MATCH (a:switch)-[r:isl {{"
                            "src_switch: '{}', "
                            "src_port: {}}}]->(b:switch) delete r")
            graph.run(delete_query.format(src_switch, src_port)).data()
        else:
            delete_query = ("MATCH (a:switch)-[r:isl {{"
                            "src_switch: '{}'}}]->(b:switch) delete r")
            graph.run(delete_query.format(src_switch)).data()

        return True

    def deactivate_isl(self, src_switch, src_port):
        """
        Update the ISL, if it exists, to a state of inactive

        Ideally, the result of this function is whether the relationship is gone (true if it is)

        :return: True always, unless an exception occurs
        """
        self.log.info(
                'Deactivating ISL: src_switch=%s, src_port=%s',
                src_switch, src_port)

        if src_port:
            query = ("MATCH (a:switch)-[r:isl {{"
                     "src_switch: '{}', "
                     "src_port: {}}}]->(b:switch) SET r.status = 'inactive'")
            graph.run(query.format(src_switch, src_port)).data()
        else:
            query = ("MATCH (a:switch)-[r:isl {{"
                     "src_switch: '{}'}}]->(b:switch) SET r.status = 'inactive'")
            graph.run(query.format(src_switch)).data()

        return True

    def isl_discovery_failed(self):
        """
        :return: Ideally, this should return true IF discovery is deleted or deactivated.
        """
        path = self.payload['path']
        switch_id = path[0]['switch_id']
        port_id = int(path[0]['port_no'])

        effective_policy = config.get("isl_failover_policy", "effective_policy")
        self.log.info(
                'Isl failure: %s_%d -- apply policy %s',
                switch_id, port_id, effective_policy)

        if effective_policy == 'deactivate':
            self.deactivate_isl(switch_id, port_id)
        else: # effective_policy == 'delete':
            # The last option is to delete .. which is a catchall, ie unknown policy
            self.delete_isl(switch_id, port_id)
        return True # the event was "handled"

    def port_down(self):
        switch_id = self.payload['switch_id']
        port_id = int(self.payload['port_no'])

        self.log.debug('Port %s_%d deletion request', switch_id, port_id)

        return self.delete_isl(switch_id, port_id)

    def create_isl(self):
        """
        Two parts to creating an ISL:
        (1) create the relationship itself
        (2) add any link properties, if they exist.

        NB: The query used for (2) is the same as in the TER
        TODO: either share the same query as library in python, or handle in java

        :return: success or failure (boolean)
        """
        path = self.payload['path']
        latency = int(self.payload['latency_ns'])
        a_switch = path[0]['switch_id']
        a_port = int(path[0]['port_no'])
        b_switch = path[1]['switch_id']
        b_port = int(path[1]['port_no'])
        speed = int(self.payload['speed'])
        available_bandwidth = int(self.payload['available_bandwidth'])

        try:
            self.log.debug(
                    'ISL %s_%d create or update request', a_switch, a_port)

            #
            # Given that we know the the src and dst exist, the following query will either
            # create the relationship if it doesn't exist, or update it if it does
            #
            isl_create_or_update = (
                "MERGE "
                "(src:switch {{name:'{}'}}) "
                "ON CREATE SET src.state = 'inactive' "
                "MERGE "
                "(dst:switch {{name:'{}'}}) "
                "ON CREATE SET dst.state = 'inactive' "
                "MERGE "
                "(src)-[i:isl {{"
                "src_switch: '{}', src_port: {}, "
                "dst_switch: '{}', dst_port: {} "
                "}}]->(dst) "
                "SET "
                "i.latency = {}, "
                "i.speed = {}, "
                "i.max_bandwidth = {}, "
                "i.status = 'active' "
            ).format(
                a_switch,
                b_switch,
                a_switch, a_port,
                b_switch, b_port,
                latency,
                speed,
                available_bandwidth
            )
            graph.run(isl_create_or_update)

            #
            # Now handle the second part .. pull properties from link_props if they exist
            #

            src_sw, src_pt, dst_sw, dst_pt = a_switch, a_port, b_switch, b_port # use same names as TER code
            query = 'MATCH (src:switch)-[i:isl]->(dst:switch) '
            query += ' WHERE i.src_switch = "%s" ' \
                     ' AND i.src_port = %s ' \
                     ' AND i.dst_switch = "%s" ' \
                     ' AND i.dst_port = %s ' % (src_sw, src_pt, dst_sw, dst_pt)
            query += ' MATCH (lp:link_props) '
            query += ' WHERE lp.src_switch = "%s" ' \
                     ' AND lp.src_port = %s ' \
                     ' AND lp.dst_switch = "%s" ' \
                     ' AND lp.dst_port = %s ' % (src_sw, src_pt, dst_sw, dst_pt)
            query += ' SET i += lp '
            graph.run(query)

            #
            # Finally, update the available_bandwidth..
            #
            flow_utils.update_isl_bandwidth(
                    self.context, src_sw, src_pt, dst_sw, dst_pt)

            self.log.info('ISL between %s and %s updated', a_switch, b_switch)

        except Exception as e:
            self.log.exception(
                    'ISL between %s and %s creation error: %s',
                    a_switch, b_switch, e)
            return False

        return True

    # TODO(surabujin): split on 2 method and drop out "propagate" argument
    def create_flow(self, flow_id, flow, propagate=True):
        rules = flow_utils.build_rules(flow)

        self.log.info('Flow rules were built: flow_id=%s', flow_id)
        flow_utils.store_flow(self.context, flow)
        self.log.info('Flow was stored: flow_id=%s', flow_id)

        if propagate:
            message_utils.send_install_commands(self.context, rules)
            self.log.info('Flow rules INSTALLED: flow_id=%s', flow_id)
            message_utils.send_info_message(
                    self.context,
                    {'payload': flow, 'clazz': MT_FLOW_RESPONSE})
        else:
            # The request is sent from Northbound .. send response back
            self.log.info('Flow rules NOT PROPAGATED: flow_id=%s', flow_id)
            data = {"payload": {"flowid": flow_id, "status": "UP"},
                    "clazz": message_utils.MT_INFO_FLOW_STATUS}
            message_utils.send_to_topic(
                    self.context, data, message_utils.MT_INFO,
                    destination="NORTHBOUND",
                    topic=config.KAFKA_NORTHBOUND_TOPIC)

    # TODO(surabujin): split on 2 method and drop out "propagate" argument
    def delete_flow(self, flow_id, flow, parent_tx=None, propagate=True):
        """
        Simple algorithm - delete the stuff in the DB, send delete commands, send a response.
        Complexity - each segment in the path may have a separate cookie, so that information needs to be gathered.
        NB: Each switch in the flow should get a delete command.

        # TODO: eliminate flowpath as part of delete_flow request; rely on flow_id only
        # TODO: Add state to flow .. ie "DELETING", as part of refactoring project to add state
        - eg: flow_utils.update_state(flow, DELETING, parent_tx)

        All flows .. single switch or multi .. will start with deleting based
        on the src and flow cookie; then we'll have a delete per segment based
        on the destination. Consequently, the "single switch flow" is
        automatically addressed using this algorithm.

        :param tx: py2neo transaction that cover whole flow operation
        :return: True, unless an exception is raised.
        """

        flow_cookie = int(flow['cookie'])
        nodes = [{'switch_id': flow['src_switch'], 'flow_id': flow_id,
                  'cookie': flow_cookie}]
        segments = flow_utils.fetch_flow_segments(flow_id, flow_cookie)
        for segment in segments:
            # every segment should have a cookie field, based on merge_segment;
            # but just in case..
            segment_cookie = segment.get('cookie', flow_cookie)
            nodes.append(
                    {'switch_id': segment['dst_switch'], 'flow_id': flow_id,
                     'cookie': segment_cookie})

        if propagate:
            self.log.info(
                    'Flow rules remove start: flow_id=%s, path=%s',
                    flow_id, nodes)
            message_utils.send_delete_commands(self.context, nodes)
            self.log.info('Flow rules removed end: flow_id=%s', flow_id)
        else:
            # The request is sent from Northbound .. send response back
            self.log.info(
                    'Flow rules NOT PROPAGATED: flow_id=%s', flow_id)
            data = {"payload": {"flowid": flow_id, "status": "DOWN"},
                    "clazz": message_utils.MT_INFO_FLOW_STATUS}
            message_utils.send_to_topic(
                    self.context, data, message_utils.MT_INFO,
                    destination="NORTHBOUND",
                    topic=config.KAFKA_NORTHBOUND_TOPIC)

        flow_utils.remove_flow(self.context, flow, parent_tx)

        self.log.info('Flow was removed: flow_id=%s', flow_id)

    def update_flow(self, flow_id, flow, tx):
        self.log.debug('Fetch flow info from DB')
        old_flow = flow_utils.get_old_flow(self.context, flow)

        self.log.debug('Create NEW/UPDATED flow')

        self.log.info('Flow rules were built: flow_id=%s', flow_id)
        rules = flow_utils.build_rules(flow)

        # TODO: add tx to store_flow
        flow_utils.store_flow(self.context, flow, tx)
        self.log.info('Flow was stored: flow_id=%s', flow_id)

        message_utils.send_install_commands(self.context, rules)

        self.log.debug('Remove OLD flow')
        self.delete_flow(old_flow['flowid'], old_flow, tx)

        payload = {'payload': flow, 'clazz': MT_FLOW_RESPONSE}
        message_utils.send_info_message(self.context, payload)

    def flow_operation(self):
        operation = self.payload['operation']
        flows = self.payload['payload']
        forward = flows['forward']
        reverse = flows['reverse']
        flow_id = forward['flowid']

        self.log.debug('Flow %s request processing', operation)

        with db.LockAdapter.wrap_lock(neo4j_update_lock), graph.begin() as tx:
            try:
                if operation == "CREATE" or operation == "PUSH":
                    propagate = operation == "CREATE"
                    # TODO: leverage transaction for creating both flows
                    self.create_flow(flow_id, forward, propagate)
                    self.create_flow(flow_id, reverse, propagate)

                elif operation == "DELETE" or operation == "UNPUSH":
                    propagate = operation == "DELETE"
                    self.delete_flow(flow_id, forward, tx, propagate)
                    self.delete_flow(flow_id, reverse, tx, propagate)
                    if propagate:
                        message_utils.send_info_message(
                                self.context,
                                {'payload': forward, 'clazz': MT_FLOW_RESPONSE})
                        message_utils.send_info_message(
                                self.context,
                                {'payload': reverse, 'clazz': MT_FLOW_RESPONSE})

                elif operation == "UPDATE":
                    self.update_flow(flow_id, forward, tx)
                    self.update_flow(flow_id, reverse, tx)

                else:
                    self.log.warning(
                            'Unsupported flow operation %r have been requested',
                            operation)
                    raise exc.NoHandlerError

            except neo4j_errors.TransientError as e:
                raise exc.RecoverableError(e)

            except Exception as e:
                kind = {
                    'CREATE': 'CREATION_FAILURE',
                    'PUSH': 'PUSH_FAILURE',
                    'DELETE': 'DELETION_FAILURE',
                    'UNPUSH': 'UNPUSH_FAILURE',
                    'UPDATE': 'UPDATE_FAILURE',
                }[operation]

                extra_args = {}
                if operation in {}:
                    extra_args['destination'] = 'NORTHBOUND'
                    extra_args['topic'] = config.KAFKA_NORTHBOUND_TOPIC

                message_utils.send_error_message(
                        self.context, kind, str(e), flow_id, **extra_args)

                raise exc.UnrecoverableError()

        self.log.info('Flow %s request processed', operation)
        return True

    def get_switches(self):
        try:
            query = "MATCH (n:switch) RETURN n"
            result = graph.run(query).data()

            switches = []
            for data in result:
                node = data['n']
                switch = {
                    'switch_id': node['name'],
                    'state': switch_states[node['state']],
                    'address': node['address'],
                    'hostname': node['hostname'],
                    'description': node['description'],
                    'controller': node['controller'],
                    'clazz': MT_SWITCH,
                }
                switches.append(switch)

            self.log.info('Got switches: %s', switches)

        except Exception as e:
            self.log.exception('Can not get switches', e)
            raise

        return switches

    def get_isls(self):
        try:
            result = self.fetch_isls()

            isls = []
            for link in result:
                # link = data['r']
                isl = {
                    'id': str(
                        link['src_switch'] + '_' + str(link['src_port'])),
                    'speed': int(link['speed']),
                    'latency_ns': int(link['latency']),
                    'available_bandwidth': int(link['available_bandwidth']),
                    'state': "DISCOVERED",
                    'path': [
                        {'switch_id': str(link['src_switch']),
                         'port_no': int(link['src_port']),
                         'seq_id': 0,
                         'segment_latency': int(link['latency'])},
                        {'switch_id': str(link['dst_switch']),
                         'port_no': int(link['dst_port']),
                         'seq_id': 1,
                         'segment_latency': 0}],
                    'clazz': MT_ISL
                }
                isls.append(isl)
            self.log.info('Got isls: %s', isls)

        except Exception as e:
            self.log.exception('Can not get isls', e)
            raise

        return isls

    def fetch_isls(self, pull=True, sort_key='src_switch'):
        """
        :return: an unsorted list of ISL relationships with all properties pulled from the db if pull=True
        """
        try:
            # query = "MATCH (a:switch)-[r:isl]->(b:switch) RETURN r ORDER BY r.src_switch, r.src_port"
            isls=[]
            rels = graph.match(rel_type="isl")
            for rel in rels:
                if pull:
                    graph.pull(rel)
                isls.append(rel)

            if sort_key:
                isls = sorted(isls, key=lambda x: x[sort_key])

            return isls
        except Exception as e:
            self.log.exception('FAILED to get ISLs from the DB ', e.message)
            raise

    def dump_network(self):
        self.log.debug('Dump network request')

        step = "Init"
        try:
            step = "Switches"
            switches = self.get_switches()
            self.log.debug("%s: %s", step, switches)

            step = "ISLs"
            isls = self.get_isls()
            self.log.debug("%s: %s", step, isls)

            step = "Flows"
            flows = flow_utils.get_flows(self.context)
            self.log.debug("%s: %s", step, flows)

            step = "Send"
            payload = {
                'switches': switches,
                'isls': isls,
                'flows': flows,
                'clazz': MT_NETWORK}
            message_utils.send_cache_message(self.context, payload)

        except Exception as e:
            self.log.exception('Can not dump network: %s', e)
            message_utils.send_error_message(
                self.context, "INTERNAL_ERROR", str(e), step,
                "WFM_CACHE", config.KAFKA_CACHE_TOPIC)
            raise

        return True
