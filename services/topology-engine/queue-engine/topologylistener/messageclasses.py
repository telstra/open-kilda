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
import threading

from topologylistener import model

from topologylistener import config
from topologylistener import db
from topologylistener import exc
from topologylistener import isl_utils
from topologylistener import link_props_utils
from neo4j.exceptions import CypherSyntaxError
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
MT_SYNC_REQUEST = "org.openkilda.messaging.command.switches.SwitchRulesSyncRequest"
MT_NETWORK = "org.openkilda.messaging.info.discovery.NetworkInfoData"
#feature toggle is the functionality to turn off/on specific features
MT_STATE_TOGGLE = "org.openkilda.messaging.command.system.FeatureToggleStateRequest"
MT_TOGGLE = "org.openkilda.messaging.command.system.FeatureToggleRequest"
MT_NETWORK_TOPOLOGY_CHANGE = (
    "org.openkilda.messaging.info.event.NetworkTopologyChange")
CD_NETWORK = "org.openkilda.messaging.command.discovery.NetworkCommandData"
CD_FLOWS_SYNC_REQUEST = 'org.openkilda.messaging.command.FlowsSyncRequest'
CD_LINK_PROPS_PUT = 'org.openkilda.messaging.te.request.LinkPropsPut'
CD_LINK_PROPS_DROP = 'org.openkilda.messaging.te.request.LinkPropsDrop'

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
    q = 'MERGE (target:config {name: "config"})\n'
    q += db.format_set_fields(db.escape_fields(
            {x: '$' + x for x in features_status_app_to_transport_map.values()},
            raw_values=True), field_prefix='target.')
    p = {
        y: features_status[x]
        for x, y in features_status_app_to_transport_map.items()}

    with graph.begin() as tx:
        db.log_query('CONFIG update', q, p)
        tx.run(q, p)


def read_config():
    q = 'MATCH (target:config {name: "config"}) RETURN target LIMIT 2'
    db.log_query('CONFIG read', q, None)
    with graph.begin() as tx:
        cursor = tx.run(q)
        try:
            config_node = db.fetch_one(cursor)['target']
            for feature, name in features_status_app_to_transport_map.items():
                features_status[feature] = config_node[name]
        except exc.DBEmptyResponse:
            logger.info(
                    'There is no persistent config in DB, fallback to'
                    ' builtin defaults')


read_config()


class MessageItem(model.JsonSerializable):
    def __init__(self, message):
        self._raw_message = message

        self.type = message.get("clazz")
        self.payload = message.get("payload", {})
        self.destination = message.get("destination","")
        self.correlation_id = message.get("correlation_id", "admin-request")
        self.reply_to = message.get("reply_to", "")

        try:
            timestamp = message['timestamp']
            timestamp = model.TimeProperty.new_from_java_timestamp(timestamp)
        except KeyError:
            timestamp = model.TimeProperty.now()
        self.timestamp = timestamp

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
                    self.create_switch()
                elif self.payload['state'] == "ACTIVATED":
                    self.activate_switch()
                elif self.payload['state'] in ("DEACTIVATED", "REMOVED"):
                    self.switch_unplug()
                event_handled = True

            elif self.get_message_type() == MT_ISL:
                if self.payload['state'] == "DISCOVERED":
                    self.create_isl()
                elif self.payload['state'] in ("FAILED", "MOVED"):
                    self.isl_discovery_failed()
                event_handled = True

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

            elif self.get_message_type() == MT_SYNC_REQUEST:
                event_handled = self.sync_switch_rules()

            elif self.get_message_type() in (
                    CD_LINK_PROPS_PUT, CD_LINK_PROPS_DROP):
                self.handle_link_props()
                event_handled = True

            if not event_handled:
                logger.error('Message was not handled correctly: message=%s',
                             self.payload)

            return event_handled
        except Exception as e:
            logger.exception("Exception during handling message")
            return False

    def handle_link_props(self):
        try:
            if self.get_message_type() == CD_LINK_PROPS_PUT:
                self.link_props_put()
            elif self.get_message_type() == CD_LINK_PROPS_DROP:
                self.link_props_drop()
            else:
                raise exc.NotImplementedError(
                    'link props request {}'.format(self.get_message_type()))
        except CypherSyntaxError as e:
            logger.exception('Invalid request: ', e)
            payload = message_utils.make_link_props_response(
                self.payload, None, 'Invalid request')
            message_utils.send_link_props_response(
                payload, self.correlation_id,
                self.get_message_type() == CD_LINK_PROPS_DROP)
        except Exception as e:
            payload = message_utils.make_link_props_response(
                self.payload, None, error=str(e))
            message_utils.send_link_props_response(
                payload, self.correlation_id,
                self.get_message_type() == CD_LINK_PROPS_DROP)

    def activate_switch(self):
        switch_id = self.payload['switch_id']

        logger.info('Switch %s activation request: timestamp=%s',
                    switch_id, self.timestamp)

        with graph.begin() as tx:
            flow_utils.precreate_switches(tx, switch_id)

            q = 'MATCH (target:switch {name: $dpid}) SET target.state="active"'
            p = {'dpid': switch_id}
            db.log_query('SWITCH activate', q, p)
            tx.run(q, p)

    def create_switch(self):
        switch_id = self.payload['switch_id']

        logger.info('Switch %s creation request: timestamp=%s',
                    switch_id, self.timestamp)

        with graph.begin() as tx:
            flow_utils.precreate_switches(tx, switch_id)

            p = {
                'address': self.payload['address'],
                'hostname': self.payload['hostname'],
                'description': self.payload['description'],
                'controller': self.payload['controller'],
                'state': 'active'}
            q = 'MATCH (target:switch {name: $dpid})\n' + db.format_set_fields(
                    db.escape_fields(
                            {x: '$' + x for x in p}, raw_values=True),
                    field_prefix='target.')
            p['dpid'] = switch_id

            db.log_query('SWITCH create', q, p)
            tx.run(q, p)

    def switch_unplug(self):
        switch_id = self.payload['switch_id']
        logger.info('Switch %s deactivation request', switch_id)

        with graph.begin() as tx:
            flow_utils.precreate_switches(tx, switch_id)

            q = ('MATCH (target:switch {name: $dpid}) '
                 'SET target.state="inactive"')
            tx.run(q, {'dpid': switch_id})

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
                updated = isl_utils.disable_by_endpoint(
                        tx, model.IslPathNode(switch_id, port), is_moved)
                updated.sort(key=lambda x: (x.source, x.dest))
                for isl in updated:
                    # we can get multiple records for one port
                    # but will use lifecycle data from first one
                    life_cycle = isl_utils.get_life_cycle_fields(tx, isl)
                    self.update_payload_lifecycle(life_cycle)
                    break

        except exc.DBRecordNotFound:
            logger.error('There is no ISL on %s_%s', switch_id, port)

    def port_down(self):
        switch_id = self.payload['switch_id']
        port_id = int(self.payload['port_no'])

        logger.info('Port %s_%d deletion request: timestamp=%s',
                    switch_id, port_id, self.timestamp)

        try:
            with graph.begin() as tx:
                for isl in isl_utils.disable_by_endpoint(
                        tx, model.IslPathNode(switch_id, port_id)):
                    # TODO(crimi): should be policy / toggle based
                    isl_utils.increase_cost(
                        tx, isl,
                        config.ISL_COST_WHEN_PORT_DOWN,
                        config.ISL_COST_WHEN_PORT_DOWN)
                    isl_utils.increase_cost(
                        tx, isl.reversed(),
                        config.ISL_COST_WHEN_PORT_DOWN,
                        config.ISL_COST_WHEN_PORT_DOWN)
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

        isl = model.InterSwitchLink.new_from_java(self.payload)
        isl.ensure_path_complete()

        logger.info('ISL %s create request', isl)
        with graph.begin() as tx:
            flow_utils.precreate_switches(
                tx, isl.source.dpid, isl.dest.dpid)
            isl_utils.create_if_missing(tx, self.timestamp, isl)
            isl_utils.set_props(tx, isl, {
                'latency': latency,
                'speed': speed,
                'max_bandwidth': available_bandwidth,
                'default_max_bandwidth': available_bandwidth,
                'actual': 'active'})

            isl_utils.update_status(tx, isl, mtime=self.timestamp)
            isl_utils.resolve_conflicts(tx, isl)

            life_cycle = isl_utils.get_life_cycle_fields(tx, isl)
            self.update_payload_lifecycle(life_cycle)

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

            logger.info('Flow rules were built: correlation_id=%s, flow_id=%s', correlation_id, flow_id)
            rules = flow_utils.build_rules(flow)

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

    def validate_and_sync_switch_rules(self):
        switch_id = self.payload['switch_id']

        diff = flow_utils.validate_switch_rules(switch_id,
                                                self.payload['flows'])
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

    def update_payload_lifecycle(self, life_cycle):
        for key, value in (
                ('time_create', life_cycle.ctime),
                ('time_modify', life_cycle.mtime)):
            if not value:
                continue
            self.payload[key] = value.as_java_timestamp()

    def link_props_put(self):
        link_props = self._unpack_link_props()
        protected = link_props.extract_protected_props()
        if protected:
            raise exc.UnacceptableDataError(
                link_props, 'property(es) %s is can\'t be changed'.format(
                    ', '.join(repr(x) for x in sorted(protected))))

        with graph.begin() as tx:
            link_props_utils.create_if_missing(tx, link_props)
            link_props_utils.set_props_and_propagate_to_isl(tx, link_props)

            actual_link_props = link_props_utils.read(tx, link_props)

        payload = message_utils.make_link_props_response(
            self.payload, actual_link_props)
        message_utils.send_link_props_response(payload, self.correlation_id)

    def link_props_drop(self):
        lookup_mask = self._unpack_link_props(key='lookup_mask')
        with graph.begin() as tx:
            removed_records = link_props_utils.drop_by_mask(tx, lookup_mask)
            for link_props in removed_records:
                isl = model.InterSwitchLink.new_from_link_props(link_props)
                isl_utils.del_props(tx, isl, link_props.props)

        response_batch = [
            message_utils.make_link_props_response(self.payload, x)
            for x in removed_records]
        message_utils.send_link_props_chunked_response(
            response_batch, self.correlation_id)

    def _unpack_link_props(self, key='link_props'):
        try:
            link_props = model.LinkProps.new_from_java(
                self.payload[key])
        except (KeyError, ValueError, TypeError) as e:
            raise exc.MalformedInputError(self._raw_message, e)
        return link_props
