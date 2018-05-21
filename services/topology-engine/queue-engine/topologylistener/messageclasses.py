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

import json
import logging
import textwrap
import threading

from py2neo import Node
from topologylistener import model

from topologylistener import config
from topologylistener import exc
from topologylistener import isl_utils
import flow_utils
import message_utils

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
MT_VALID_REQUEST = "org.openkilda.messaging.command.switches.SwitchRulesValidateRequest"
MT_SYNC_REQUEST = "org.openkilda.messaging.command.switches.SwitchRulesSyncRequest"
MT_NETWORK = "org.openkilda.messaging.info.discovery.NetworkInfoData"
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
                elif self.payload['state'] in ("DEACTIVATED", "REMOVED"):
                    self.switch_unplug()
                    event_handled = True

            elif self.get_message_type() == MT_ISL:
                if self.payload['state'] == "DISCOVERED":
                    event_handled = self.create_isl()
                elif self.payload['state'] in ("FAILED", "MOVED"):
                    event_handled = self.isl_discovery_failed()

            elif self.get_message_type() == MT_PORT:
                if self.payload['state'] == "DOWN":
                    self.port_down()
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

            elif self.get_message_type() == MT_STATE_TOGGLE:
                event_handled = self.get_feature_toggle_state()
            elif self.get_message_type() == MT_TOGGLE:
                event_handled = self.update_feature_toggles()

            elif self.get_message_type() == MT_SWITCH_EXTENDED:
                if features_status[FEATURE_SYNC_OFRULES]:
                    event_handled = self.validate_and_sync_switch_rules()
                else:
                    event_handled = True

            elif self.get_message_type() == MT_VALID_REQUEST:
                event_handled = self.send_dump_rules_request()

            elif self.get_message_type() == MT_SWITCH_RULES:
                event_handled = self.validate_switch_rules()

            elif self.get_message_type() == MT_SYNC_REQUEST:
                event_handled = self.sync_switch_rules()

            if not event_handled:
                logger.error('Message was not handled correctly: message=%s',
                             self.payload)

            return event_handled
        except Exception as e:
            logger.exception("Exception during handling message")
            return False

    def activate_switch(self):
        switch_id = self.payload['switch_id']

        logger.info('Switch %s activation request: timestamp=%s',
                    switch_id, self.timestamp)

        # FIXME(surabujin): avoid usage of graph.find_* functions - due to lack of transaction support
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

    def switch_unplug(self):
        switch_id = self.payload['switch_id']

        logger.info('Switch %s deactivation request: timestamp=%s, ',
                    switch_id, self.timestamp)

        with graph.begin() as tx:
            logger.info('Deactivating switch: %s', switch_id)
            flow_utils.precreate_switches(tx, switch_id)

            q = ('MATCH (target:switch {name: $dpid}) '
                 'SET target.state="inactive"')
            tx.run(q, {'dpid': switch_id})

            isl_utils.switch_unplug(tx, switch_id)

    def isl_discovery_failed(self):
        """
        :return: Ideally, this should return true IFF discovery is deleted or deactivated.
        """
        path = self.payload['path']
        switch_id = path[0]['switch_id']
        port = int(path[0]['port_no'])

        effective_policy = config.get("isl_failover_policy", "effective_policy")
        logger.info('Isl failure: %s_%d -- apply policy %s: timestamp=%s',
                    switch_id, port, effective_policy, self.timestamp)

        is_moved = self.payload['state'] == 'MOVED'
        try:
            with graph.begin() as tx:
                isl_utils.disable_by_endpoint(
                    tx, model.NetworkEndpoint(switch_id, port), is_moved)
        except exc.DBRecordNotFound:
            logger.error('There is no ISL on %s_%s', switch_id, port)

        return True

    def port_down(self):
        switch_id = self.payload['switch_id']
        port_id = int(self.payload['port_no'])

        logger.info('Port %s_%d deletion request: timestamp=%s',
                    switch_id, port_id, self.timestamp)

        try:
            with graph.begin() as tx:
                for isl in isl_utils.disable_by_endpoint(
                        tx, model.NetworkEndpoint(switch_id, port_id)):
                    # TODO(crimi): should be policy / toggle based
                    isl_utils.set_cost(
                        tx, isl, config.ISL_COST_WHEN_PORT_DOWN)
                    isl_utils.set_cost(
                        tx, isl.reversed(), config.ISL_COST_WHEN_PORT_DOWN)
        except exc.DBRecordNotFound:
            logger.info("There is no ISL on %s_%s", switch_id, port_id)

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

        isl = model.InterSwitchLink.new_from_isl_data(self.payload)
        isl.ensure_path_complete()

        logger.info('ISL %s create request', isl)
        with graph.begin() as tx:
            flow_utils.precreate_switches(
                tx, isl.source.dpid, isl.dest.dpid)
            isl_utils.create_if_missing(tx, isl)

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
                "i.actual = 'active', "
                "i.status = 'inactive'"
            ).format(
                a_switch,
                b_switch,
                a_switch, a_port,
                b_switch, b_port,
                latency,
                speed,
                available_bandwidth
            )
            tx.run(isl_create_or_update)

            isl_utils.update_status(tx, isl)
            isl_utils.resolve_conflicts(tx, isl)

        #
        # Now handle the second part .. pull properties from link_props if they exist
        #

        src_sw, src_pt, dst_sw, dst_pt = a_switch, a_port, b_switch, b_port  # use same names as TER code
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

        logger.info('ISL %s have been created/updated', isl)

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
            transit_vlan = int(flow['transit_vlan'])

            current_node = {'switch_id': flow['src_switch'], 'flow_id': flow_id, 'cookie': flow_cookie,
                       'meter_id': flow['meter_id'], 'in_port': flow['src_port'], 'in_vlan': flow['src_vlan']}
            nodes = [current_node]

            segments = flow_utils.fetch_flow_segments(flow_id, flow_cookie)
            for segment in segments:
                current_node['out_port'] = segment['src_port']

                # every segment should have a cookie field, based on merge_segment; but just in case..
                segment_cookie = segment.get('cookie', flow_cookie)
                current_node = {'switch_id': segment['dst_switch'], 'flow_id': flow_id, 'cookie': segment_cookie,
                    'meter_id': None, 'in_port': segment['dst_port'], 'in_vlan': transit_vlan,
                    'out_port': segment['dst_port']}
                nodes.append(current_node)

            current_node['out_port'] = flow['dst_port']

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

    def handle_flow_topology_sync(self):
        payload = {
            'switches': [],
            'isls': [],
            'flows': flow_utils.get_flows(),
            'clazz': MT_NETWORK}
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

    def validate_switch_rules(self):
        diff = flow_utils.validate_switch_rules(self.payload['switch_id'],
                                                self.payload['flows'])
        logger.debug('Switch rules validation result: %s', diff)

        message_utils.send_validation_rules_response(diff["missing_rules"],
                                                     diff["excess_rules"],
                                                     diff["proper_rules"],
                                                     self.correlation_id)
        return True

    def validate_and_sync_switch_rules(self):
        switch_id = self.payload['switch_id']

        diff = flow_utils.validate_switch_rules(switch_id,
                                                self.payload['flows'])
        logger.debug('Switch rules validation result: %s', diff)

        sync_actions = flow_utils.build_commands_to_sync_rules(switch_id,
                                                               diff["missing_rules"])
        commands = sync_actions["commands"]
        if commands:
            logger.info('Install commands for switch %s are to be sent: %s',
                        switch_id, commands)
            message_utils.send_force_install_commands(switch_id, commands,
                                                      self.correlation_id)

        return True

    def sync_switch_rules(self):
        switch_id = self.payload['switch_id']
        rules_to_sync = self.payload['rules']

        logger.debug('Switch rules synchronization for rules: %s', rules_to_sync)

        sync_actions = flow_utils.build_commands_to_sync_rules(switch_id,
                                                           rules_to_sync)
        commands = sync_actions["commands"]
        if commands:
            logger.info('Install commands for switch %s are to be sent: %s',
                        switch_id, commands)
            message_utils.send_force_install_commands(switch_id, commands,
                                                      self.correlation_id)

        message_utils.send_sync_rules_response(sync_actions["installed_rules"],
                                               self.correlation_id)
        return True

    def send_dump_rules_request(self):
        message_utils.send_dump_rules_request(self.payload['switch_id'],
                                              self.correlation_id)
        return True
