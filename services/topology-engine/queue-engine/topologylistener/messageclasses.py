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

import collections
import json
import logging
import threading

import config
import flow_utils
import message_utils

from py2neo import Node

logger = logging.getLogger(__name__)
graph = flow_utils.graph
switch_states = {
    'active': 'ACTIVATED',
    'inactive': 'DEACTIVATED',
    'removed': 'REMOVED'
}

MT_SWITCH = "org.openkilda.messaging.info.event.SwitchInfoData"
MT_SWITCH_EXTENDED = "org.openkilda.messaging.info.event.SwitchInfoExtendedData"
MT_ISL = "org.openkilda.messaging.info.event.IslInfoData"
MT_PORT = "org.openkilda.messaging.info.event.PortInfoData"
MT_FLOW_INFODATA = "org.openkilda.messaging.info.flow.FlowInfoData"
MT_FLOW_RESPONSE = "org.openkilda.messaging.info.flow.FlowResponse"
MT_SYNC_REQUEST = "org.openkilda.messaging.command.switches.SyncRulesRequest"
MT_SWITCH_RULES = "org.openkilda.messaging.info.rule.SwitchFlowEntries"
#feature toggle is the functionality to turn off/on specific features
MT_STATE_TOGGLE = "org.openkilda.messaging.command.system.FeatureToggleStateRequest"
MT_TOGGLE = "org.openkilda.messaging.command.system.FeatureToggleRequest"
MT_NETWORK_TOPOLOGY_CHANGE = (
    "org.openkilda.messaging.info.event.NetworkTopologyChange")
CD_NETWORK = "org.openkilda.messaging.command.discovery.NetworkCommandData"
CD_FLOWS_SYNC_REQUEST = 'org.openkilda.messaging.command.FlowsSyncRequest'


FEATURE_SYNC_OFRULES = 'sync_rules_on_activation'
FEATURE_REROUTE_ON_ISL_DISCOVERY = 'flows_reroute_on_isl_discovery'
FEATURE_CREATE_FLOW = 'create_flow'
FEATURE_UPDATE_FLOW = 'update_flow'
FEATURE_DELETE_FLOW = 'delete_flow'
FEATURE_PUSH_FLOW = 'push_flow'
FEATURE_UNPUSH_FLOW = 'unpush_flow'

features_status = {
    FEATURE_SYNC_OFRULES: False,
    FEATURE_REROUTE_ON_ISL_DISCOVERY: True,
    FEATURE_CREATE_FLOW: False,
    FEATURE_UPDATE_FLOW: False,
    FEATURE_DELETE_FLOW: False,
    FEATURE_PUSH_FLOW:   False,
    FEATURE_UNPUSH_FLOW: False,
}

features_status_app_to_transport_map = {
    FEATURE_SYNC_OFRULES: 'sync_rules',
    FEATURE_REROUTE_ON_ISL_DISCOVERY: 'reflow_on_switch_activation',
    FEATURE_CREATE_FLOW: 'create_flow',
    FEATURE_UPDATE_FLOW: 'update_flow',
    FEATURE_DELETE_FLOW: 'delete_flow',
    FEATURE_PUSH_FLOW: 'push_flow',
    FEATURE_UNPUSH_FLOW: 'unpush_flow'
}

features_status_transport_to_app_map = {
    transport: app
    for app, transport in features_status_app_to_transport_map.items()}


# This is used for blocking on flow changes.
# flow_sem = multiprocessing.Semaphore()
neo4j_update_lock = threading.RLock()


def update_config():
    config_node = Node('config', name='config')
    graph.merge(config_node)
    for feature, name in features_status_app_to_transport_map.items():
        config_node[name] = features_status[feature]
    config_node.push()
    return True


def read_config():
    config_node = graph.find_one('config')
    if config_node is not None:
        for feature, name in features_status_app_to_transport_map.items():
            features_status[feature] = config_node[name]

read_config()


class MessageItem(object):
    def __init__(self, **kwargs):
        self.type = kwargs.get("clazz")
        self.timestamp = str(kwargs.get("timestamp"))
        self.payload = kwargs.get("payload", {})
        self.destination = kwargs.get("destination","")
        self.correlation_id = kwargs.get("correlation_id", "admin-request")
        self.reply_to = kwargs.get("reply_to", "")

    def to_json(self):
        return json.dumps(
            self, default=lambda o: o.__dict__, sort_keys=True, indent=4)

    def get_type(self):
        message_type = self.get_message_type()
        command = self.get_command()
        return command if message_type == 'unknown' else message_type

    def get_command(self):
        return self.payload.get('clazz', 'unknown')

    def get_message_type(self):
        return self.payload.get('clazz', 'unknown')

    def handle(self):
        try:
            event_handled = False

            if self.get_message_type() == MT_SWITCH:
                if self.payload['state'] == "ADDED":
                    event_handled = self.create_switch()
                elif self.payload['state'] == "ACTIVATED":
                    event_handled = self.activate_switch()
                elif self.payload['state'] == "DEACTIVATED":
                    event_handled = self.deactivate_switch()
                elif self.payload['state'] == "REMOVED":
                    event_handled = self.remove_switch()

            elif self.get_message_type() == MT_ISL:
                if self.payload['state'] == "DISCOVERED":
                    event_handled = self.create_isl()
                elif self.payload['state'] == "FAILED":
                    event_handled = self.isl_discovery_failed()

            elif self.get_message_type() == MT_PORT:
                if self.payload['state'] == "DOWN":
                    event_handled = self.port_down()
                else:
                    event_handled = True
            # Cache topology expects to receive OFE events
            if event_handled:
                message_utils.send_cache_message(self.payload,
                                                 self.correlation_id)
                self.handle_topology_change()

            elif self.get_message_type() == MT_FLOW_INFODATA:
                event_handled = self.flow_operation()

            elif self.get_command() == CD_FLOWS_SYNC_REQUEST:
                self.handle_flow_topology_sync()
                event_handled = True

            elif self.get_command() == CD_NETWORK:
                event_handled = self.dump_network()

            elif self.get_message_type() == MT_STATE_TOGGLE:
                event_handled = self.get_feature_toggle_state()
            elif self.get_message_type() == MT_TOGGLE:
                event_handled = self.update_feature_toggles()

            elif self.get_message_type() == MT_SWITCH_EXTENDED:
                if features_status[FEATURE_SYNC_OFRULES]:
                    event_handled = self.validate_switch()
                else:
                    event_handled = True

            elif self.get_message_type() == MT_SYNC_REQUEST:
                event_handled = self.send_dump_rules_request()

            elif self.get_message_type() == MT_SWITCH_RULES:
                event_handled = self.validate_switch(self.payload)

            if not event_handled:
                logger.error('Message was not handled correctly: message=%s',
                             self.payload)

            return event_handled
        except Exception as e:
            logger.exception("Exception during handling message")
            return False

#        finally:
#            return True

    def activate_switch(self):
        switch_id = self.payload['switch_id']

        logger.info('Switch %s activation request: timestamp=%s',
                    switch_id, self.timestamp)

        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switch_id))
        if switch:
            graph.merge(switch)
            switch['state'] = "active"
            switch.push()
            logger.info('Activating switch: %s', switch_id)
        return True

    def create_switch(self):
        switch_id = self.payload['switch_id']

        logger.info('Switch %s creation request: timestamp=%s',
                    switch_id, self.timestamp)

        # ensure it exists
        switch = Node("switch",name=switch_id)
        graph.merge(switch)

        # now update it
        switch['address'] = self.payload['address']
        switch['hostname'] = self.payload['hostname']
        switch['description'] = self.payload['description']
        switch['controller'] = self.payload['controller']
        switch['state'] = 'active'
        switch.push()

        logger.info("Successfully created switch %s", switch_id)
        return True

    def deactivate_switch(self):
        switch_id = self.payload['switch_id']

        logger.info('Switch %s deactivation request: timestamp=%s, ',
                    switch_id, self.timestamp)

        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switch_id))
        if switch:
            graph.merge(switch)
            switch['state'] = "inactive"
            switch.push()
            logger.info('Deactivating switch: %s', switch_id)
            return True

        else:
            logger.warn('Switch %s does not exist in topology', switch_id)
        return True

    def remove_switch(self):
        switch_id = self.payload['switch_id']

        logger.info('Switch %s removing request: timestamp=%s',
                    switch_id, self.timestamp)

        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switch_id))
        if switch:
            graph.merge(switch)
            switch['state'] = "removed"
            switch.push()
            logger.info('Removing switch: %s', switch_id)
            self.delete_isl(switch_id, None)

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

    @staticmethod
    def delete_isl(src_switch, src_port):
        """
        Delete the ISL if it exists.

        Ideally, the result of this function is whether the relationship is gone (true if it is)

        :return: True always, unless an exception occurs
        """

        logger.info('Removing ISL, if it exists: src_switch=%s, src_port=%s',
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

    @staticmethod
    def deactivate_isl(src_switch, src_port):
        """
        Update the ISL, if it exists, to a state of inactive

        Ideally, the result of this function is whether the relationship is gone (true if it is)

        :return: True always, unless an exception occurs
        """
        logger.info('Deactivating ISL: src_switch=%s, src_port=%s',
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
        :return: Ideally, this should return true IFF discovery is deleted or deactivated.
        """
        path = self.payload['path']
        switch_id = path[0]['switch_id']
        port_id = int(path[0]['port_no'])

        effective_policy = config.get("isl_failover_policy", "effective_policy")
        logger.info('Isl failure: %s_%d -- apply policy %s: timestamp=%s',
                    switch_id, port_id, effective_policy, self.timestamp)

        if effective_policy == 'deactivate':
            self.deactivate_isl(switch_id, port_id)
        else: # effective_policy == 'delete':
            # The last option is to delete .. which is a catchall, ie unknown policy
            self.delete_isl(switch_id, port_id)
        return True # the event was "handled"

    def port_down(self):
        switch_id = self.payload['switch_id']
        port_id = int(self.payload['port_no'])

        logger.info('Port %s_%d deletion request: timestamp=%s',
                    switch_id, port_id, self.timestamp)

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
            logger.info('ISL %s_%d create or update request: timestamp=%s',
                        a_switch, a_port, self.timestamp)

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
            flow_utils.update_isl_bandwidth(src_sw, src_pt, dst_sw, dst_pt)

            logger.info('ISL between %s and %s updated', a_switch, b_switch)

        except Exception as e:
            logger.exception('ISL between %s and %s creation error: %s',
                             a_switch, b_switch, e.message)
            return False

        return True

    def handle_topology_change(self):
        if self.get_message_type() != MT_ISL:
            return
        if self.payload['state'] != "DISCOVERED":
            return
        if not features_status[FEATURE_REROUTE_ON_ISL_DISCOVERY]:
            return

        path = self.payload['path']
        node = path[0]

        payload = {
            'clazz': MT_NETWORK_TOPOLOGY_CHANGE,
            'type': 'ENDPOINT_ADD',
            'switch_id': node['switch_id'],
            'port_number': node['port_no']}

        message_utils.send_cache_message(
                payload, self.correlation_id)

    @staticmethod
    def create_flow(flow_id, flow, correlation_id, tx, propagate=True, from_nb=False):
        """
        :param propagate: If true, send to switch
        :param from_nb: If true, send response to NORTHBOUND API; otherwise to FLOW_TOPOLOGY
        :return:
        """

        try:
            rules = flow_utils.build_rules(flow)

            logger.info('Flow rules were built: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            flow_utils.store_flow(flow, tx)

            logger.info('Flow was stored: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            if propagate:
                message_utils.send_install_commands(rules, correlation_id)
                logger.info('Flow rules INSTALLED: correlation_id=%s, flow_id=%s', correlation_id, flow_id)

            if not from_nb:
                message_utils.send_info_message({'payload': flow, 'clazz': MT_FLOW_RESPONSE}, correlation_id)
            else:
                # The request is sent from Northbound .. send response back
                logger.info('Flow rules NOT PROPAGATED: correlation_id=%s, flow_id=%s', correlation_id, flow_id)
                data = {"payload":{"flowid": flow_id,"status": "UP"},
                        "clazz": message_utils.MT_INFO_FLOW_STATUS}
                message_utils.send_to_topic(
                    payload=data,
                    correlation_id=correlation_id,
                    message_type=message_utils.MT_INFO,
                    destination="NORTHBOUND",
                    topic=config.KAFKA_NORTHBOUND_TOPIC
                )

        except Exception as e:
            logger.exception('Can not create flow: %s', flow_id)
            if not from_nb:
                # Propagate is the normal scenario, so send response back to FLOW
                message_utils.send_error_message(correlation_id, "CREATION_FAILURE", e.message, flow_id)
            else:
                # This means we tried a PUSH, send response back to NORTHBOUND
                message_utils.send_error_message(correlation_id, "PUSH_FAILURE", e.message, flow_id,
                    destination="NORTHBOUND", topic=config.KAFKA_NORTHBOUND_TOPIC)
            raise

        return True

    @staticmethod
    def delete_flow(flow_id, flow, correlation_id, parent_tx=None, propagate=True, from_nb=False):
        """
        Simple algorithm - delete the stuff in the DB, send delete commands, send a response.
        Complexity - each segment in the path may have a separate cookie, so that information needs to be gathered.
        NB: Each switch in the flow should get a delete command.

        # TODO: eliminate flowpath as part of delete_flow request; rely on flow_id only
        # TODO: Add state to flow .. ie "DELETING", as part of refactoring project to add state
        - eg: flow_utils.update_state(flow, DELETING, parent_tx)

        :param parent_tx: If there is a larger transaction to use, then use it.
        :return: True, unless an exception is raised.
        """
        try:
            # All flows .. single switch or multi .. will start with deleting based on the src and flow cookie; then
            # we'll have a delete per segment based on the destination. Consequently, the "single switch flow" is
            # automatically addressed using this algorithm.
            flow_cookie = int(flow['cookie'])
            nodes = [
                {'switch_id': flow['src_switch'], 'flow_id': flow_id, 'cookie': flow_cookie, 'meter_id': flow['meter_id']}]
            segments = flow_utils.fetch_flow_segments(flow_id, flow_cookie)
            for segment in segments:
                # every segment should have a cookie field, based on merge_segment; but just in case..
                segment_cookie = segment.get('cookie', flow_cookie)
                nodes.append({
                    'switch_id': segment['dst_switch'], 'flow_id': flow_id, 'cookie': segment_cookie,
                    'meter_id': None})

            if propagate:
                logger.info('Flow rules remove start: correlation_id=%s, flow_id=%s, path=%s', correlation_id, flow_id,
                            nodes)
                message_utils.send_delete_commands(nodes, correlation_id)
                logger.info('Flow rules removed end : correlation_id=%s, flow_id=%s', correlation_id, flow_id)

            if from_nb:
                # The request is sent from Northbound .. send response back
                logger.info('Flow rules from NB: correlation_id=%s, flow_id=%s', correlation_id, flow_id)
                data = {"payload":{"flowid": flow_id,"status": "DOWN"},
                        "clazz": message_utils.MT_INFO_FLOW_STATUS}
                message_utils.send_to_topic(
                    payload=data,
                    correlation_id=correlation_id,
                    message_type=message_utils.MT_INFO,
                    destination="NORTHBOUND",
                    topic=config.KAFKA_NORTHBOUND_TOPIC
                )

            flow_utils.remove_flow(flow, parent_tx)

            logger.info('Flow was removed: correlation_id=%s, flow_id=%s', correlation_id, flow_id)

        except Exception as e:
            logger.exception('Can not delete flow: %s', e.message)
            if not from_nb:
                # Propagate is the normal scenario, so send response back to FLOW
                message_utils.send_error_message(correlation_id, "DELETION_FAILURE", e.message, flow_id)
            else:
                # This means we tried a UNPUSH, send response back to NORTHBOUND
                message_utils.send_error_message( correlation_id, "UNPUSH_FAILURE", e.message, flow_id,
                    destination="NORTHBOUND", topic=config.KAFKA_NORTHBOUND_TOPIC)
            raise

        return True

    @staticmethod
    def update_flow(flow_id, flow, correlation_id, tx):
        try:
            old_flow = flow_utils.get_old_flow(flow)

            #
            # Start the transaction to govern the create/delete
            #
            logger.info('Flow rules were built: correlation_id=%s, flow_id=%s', correlation_id, flow_id)
            rules = flow_utils.build_rules(flow)
            # TODO: add tx to store_flow
            flow_utils.store_flow(flow, tx)
            logger.info('Flow was stored: correlation_id=%s, flow_id=%s', correlation_id, flow_id)
            message_utils.send_install_commands(rules, correlation_id)

            MessageItem.delete_flow(old_flow['flowid'], old_flow, correlation_id, tx)

            payload = {'payload': flow, 'clazz': MT_FLOW_RESPONSE}
            message_utils.send_info_message(payload, correlation_id)

        except Exception as e:
            logger.exception('Can not update flow: %s', e.message)
            message_utils.send_error_message(
                correlation_id, "UPDATE_FAILURE", e.message, flow_id)
            raise

        return True

    def not_allow_flow_operation(self):
        op = self.payload['operation'].upper()

        # (nmarchenko) I do that for readability and to avoid double binary
        # negation
        allow = False

        if op == "CREATE" and features_status[FEATURE_CREATE_FLOW]:
            return allow
        if op == "PUSH" and features_status[FEATURE_PUSH_FLOW]:
            return allow
        if op == "PUSH_PROPAGATE" and features_status[FEATURE_CREATE_FLOW]:
            return allow
        if op == "DELETE" and features_status[FEATURE_DELETE_FLOW]:
            return allow
        if op == "UNPUSH" and features_status[FEATURE_UNPUSH_FLOW]:
            return allow
        if op == "UNPUSH_PROPAGATE" and features_status[FEATURE_DELETE_FLOW]:
            return allow
        if op == "UPDATE" and features_status[FEATURE_UPDATE_FLOW]:
            return allow

        return not allow

    def flow_operation(self):
        correlation_id = self.correlation_id
        timestamp = self.timestamp
        payload = self.payload

        operation = payload['operation']
        flows = payload['payload']
        forward = flows['forward']
        reverse = flows['reverse']
        flow_id = forward['flowid']

        if self.not_allow_flow_operation():
            logger.info('Flow %s request is not allow: '
                    'timestamp=%s, correlation_id=%s, payload=%s',
                    operation, timestamp, correlation_id, payload)

            # TODO: We really should use the reply-to field, at least in NB, so that we know to send the response.
            op = payload['operation'].upper()
            if op == "PUSH" or op == "PUSH_PROPAGATE" or op == "UNPUSH" or op == "UNPUSH_PROPAGATE":
                message_utils.send_error_message(
                    correlation_id, 'REQUEST_INVALID', op+"-FAILURE - NOT ALLOWED RIGHT NOW - Toggle the feature to allow this behavior", "",
                    destination="NORTHBOUND", topic=config.KAFKA_NORTHBOUND_TOPIC)

            return True

        logger.info('Flow %s request processing: '
                    'timestamp=%s, correlation_id=%s, payload=%s',
                    operation, timestamp, correlation_id, payload)

        tx = None
        # flow_sem.acquire(timeout=10)  # wait 10 seconds .. then proceed .. possibly causing some issue.
        neo4j_update_lock.acquire()
        try:
            OP = operation.upper()
            if OP == "CREATE" or OP == "PUSH" or OP == "PUSH_PROPAGATE":
                propagate = (OP == "CREATE" or OP == "PUSH_PROPAGATE")
                from_nb = (OP == "PUSH" or OP == "PUSH_PROPAGATE")
                tx = graph.begin()
                self.create_flow(flow_id, forward, correlation_id, tx, propagate, from_nb)
                self.create_flow(flow_id, reverse, correlation_id, tx, propagate, from_nb)
                tx.commit()
                tx = None

            elif OP == "DELETE" or OP == "UNPUSH" or OP == "UNPUSH_PROPAGATE":
                tx = graph.begin()
                propagate = (OP == "DELETE" or OP == "UNPUSH_PROPAGATE")
                from_nb = (OP == "UNPUSH" or OP == "UNPUSH_PROPAGATE")
                MessageItem.delete_flow(flow_id, forward, correlation_id, tx, propagate, from_nb)
                MessageItem.delete_flow(flow_id, reverse, correlation_id, tx, propagate, from_nb)
                if not from_nb:
                    message_utils.send_info_message({'payload': forward, 'clazz': MT_FLOW_RESPONSE}, correlation_id)
                    message_utils.send_info_message({'payload': reverse, 'clazz': MT_FLOW_RESPONSE}, correlation_id)
                tx.commit()
                tx = None

            elif OP == "UPDATE":
                tx = graph.begin()
                MessageItem.update_flow(flow_id, forward, correlation_id, tx)
                MessageItem.update_flow(flow_id, reverse, correlation_id, tx)
                tx.commit()
                tx = None

            else:
                logger.warn('Flow operation is not supported: '
                            'operation=%s, timestamp=%s, correlation_id=%s,',
                            operation, timestamp, correlation_id)
        except Exception:
            if tx is not None:
                tx.rollback()
            # flow_sem.release()
            raise

        finally:
            #flow_sem.release()
            neo4j_update_lock.release()

        logger.info('Flow %s request processed: '
                    'timestamp=%s, correlation_id=%s, payload=%s',
                    operation, timestamp, correlation_id, payload)

        return True


    @staticmethod
    def get_switches():
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

            logger.info('Got switches: %s', switches)

        except Exception as e:
            logger.exception('Can not get switches', e.message)
            raise

        return switches


    @staticmethod
    def get_isls():
        try:
            result = MessageItem.fetch_isls()

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
            logger.info('Got isls: %s', isls)

        except Exception as e:
            logger.exception('Can not get isls', e.message)
            raise

        return isls


    @staticmethod
    def fetch_isls(pull=True,sort_key='src_switch'):
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
            logger.exception('FAILED to get ISLs from the DB ', e.message)
            raise

    def dump_network(self):
        correlation_id = self.correlation_id
        step = "Init"
        logger.info('Dump network request: timestamp=%s, correlation_id=%s',
                    self.timestamp, correlation_id)

        try:
            step = "Switches"
            switches = self.get_switches()
            logger.debug("%s: %s", step, switches)

            step = "ISLs"
            isls = self.get_isls()
            logger.debug("%s: %s", step, isls)

            step = "Flows"
            flows = flow_utils.get_flows()
            logger.debug("%s: %s", step, flows)

            step = "Send"
            message_utils.send_network_dump(
                correlation_id, switches, isls, flows)

        except Exception as e:
            logger.exception('Can not dump network: %s', e.message)
            message_utils.send_error_message(
                correlation_id, "INTERNAL_ERROR", e.message, step,
                "WFM_CACHE", config.KAFKA_CACHE_TOPIC)
            raise

        return True

    def handle_flow_topology_sync(self):
        payload = {
            'switches': [],
            'isls': [],
            'flows': flow_utils.get_flows(),
            'clazz': message_utils.MT_NETWORK}
        message_utils.send_to_topic(
            payload, self.correlation_id, message_utils.MT_INFO,
            destination="WFM_FLOW_LCM", topic=config.KAFKA_FLOW_TOPIC)

    def get_feature_toggle_state(self):
        payload = message_utils.make_features_status_response()
        for feature, status in features_status.items():
            transport_key = features_status_app_to_transport_map[feature]
            setattr(payload, transport_key, status)

        message_utils.send_to_topic(payload, self.correlation_id,
                                    message_type=message_utils.MT_INFO,
                                    destination="NORTHBOUND",
                                    topic=config.KAFKA_NORTHBOUND_TOPIC)
        return True

    def update_feature_toggles(self):
        for transport_key in features_status_transport_to_app_map:
            app_key = features_status_transport_to_app_map[transport_key]
            try:
                status = self.payload[transport_key]
            except KeyError:
                continue

            current = features_status[app_key]
            logger.info(
                    'Set feature %s status to %s, previous value %s',
                    app_key, status, current)
            features_status[app_key] = status

        update_config()

        return True

    # todo(Nikita C): refactor/move to separate class
    def validate_switch(self, dumped_rules=None):
        switch_id = self.payload['switch_id']
        query = "MATCH p = (sw:switch)-[segment:flow_segment]-() " \
                "WHERE sw.name='{}' " \
                "RETURN segment"
        result = graph.run(query.format(switch_id)).data()

        if dumped_rules:
            cookies = [x['cookie'] for x in dumped_rules['flows']]
        else:
            cookies = [x['cookie'] for x in self.payload['flows']]

        commands = []
        # define three types of rules with cookies
        missed_rules = set()
        excess_rules = set()
        proper_rules = set()

        # group flow_segments by parent cookie, it is helpful for building
        # transit switch rules
        segment_pairs = collections.defaultdict(list)
        for relationship in result:
            flow_segment = relationship['segment']
            segment_pairs[flow_segment['parent_cookie']].append(flow_segment)

        # check whether the switch has all necessary cookies
        for pair in segment_pairs.values():
            cookie = pair[0]['parent_cookie']
            cookie_hex = flow_utils.cookie_to_hex(cookie)
            if pair[0]['parent_cookie'] not in cookies:
                logger.warn('Rule %s is not found on switch %s', cookie_hex,
                             switch_id)
                commands.extend(MessageItem.command_from_segment(pair,
                                                                 switch_id))
                missed_rules.add(cookie_hex)
            else:
                proper_rules.add(cookie_hex)

        # check whether the switch has one-switch flows.
        # since one-switch flows don't have flow_segments we have to validate
        # such flows separately
        query = "MATCH (sw:switch)-[r:flow]->(sw:switch) " \
                "WHERE sw.name='{}' RETURN r"
        result = graph.run(query.format(switch_id)).data()
        for item in result:
            flow = flow_utils.hydrate_flow(item)
            cookie_hex = flow_utils.cookie_to_hex(flow['cookie'])

            if flow['cookie'] not in cookies:
                logger.warn("Found missed one-switch flow %s for switch %s",
                             cookie_hex, switch_id)
                missed_rules.add(cookie_hex)
                output_action = flow_utils.choose_output_action(
                    flow['src_vlan'], flow['dst_vlan'])

                commands.append(message_utils.build_one_switch_flow_from_db(
                    switch_id, flow, output_action))
            else:
                proper_rules.add(cookie_hex)

        # check whether the switch has redundant rules
        for flow in self.payload['flows']:
            hex_cookie = flow_utils.cookie_to_hex(flow['cookie'])
            if hex_cookie not in proper_rules and \
                    hex_cookie not in flow_utils.ignored_rules:
                logger.error('Rule %s is obsolete for the switch %s',
                             hex_cookie, switch_id)
                excess_rules.add(hex_cookie)

        message_utils.send_force_install_commands(switch_id, commands,
                                                  self.correlation_id)

        if dumped_rules:
            message_utils.send_sync_rules_response(missed_rules, excess_rules,
                                                   proper_rules,
                                                   self.correlation_id)
        return True


    @staticmethod
    def command_from_segment(segment_pair, switch_id):
        left_segment = segment_pair[0]
        query = "match ()-[r:flow]->() where r.flowid='{}' " \
                "and r.cookie={} return r"
        result = graph.run(query.format(left_segment['flowid'],
                                        left_segment['parent_cookie'])).data()

        if not result:
            logger.error("Flow with id %s was not found",
                         left_segment['flowid'])
            return

        flow = flow_utils.hydrate_flow(result[0])
        output_action = flow_utils.choose_output_action(flow['src_vlan'],
                                                        flow['dst_vlan'])

        # check if the flow is one-switch flow
        if left_segment['src_switch'] == left_segment['dst_switch']:
            yield message_utils.build_one_switch_flow_from_db(switch_id, flow,
                                                              output_action)
        # check if the switch is not source and not destination of the flow
        if flow['src_switch'] != switch_id \
                and flow['dst_switch'] != switch_id:
            right_segment = segment_pair[1]
            # define in_port and out_port for transit switch
            if left_segment['dst_switch'] == switch_id and \
                    left_segment['src_switch'] == switch_id:
                in_port = left_segment['dst_port']
                out_port = right_segment['src_port']
            else:
                in_port = right_segment['dst_port']
                out_port = left_segment['src_port']

            yield message_utils.build_intermediate_flows(
                switch_id, in_port, out_port, flow['transit_vlan'],
                flow['flowid'], left_segment['parent_cookie'])

        elif left_segment['src_switch'] == switch_id:
            yield message_utils.build_ingress_flow_from_db(flow, output_action)
        else:
            yield message_utils.build_egress_flow_from_db(flow, output_action)

    def send_dump_rules_request(self):
        message_utils.send_dump_rules_request(self.payload['switch_id'],
                                              self.correlation_id)
        return True
