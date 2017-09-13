#!/usr/bin/python
import json
from threading import Lock

import traceback
from py2neo import Node

import message_utils
import flow_utils
from flow_utils import graph
from logger import get_logger


isl_lock = Lock()
logger = get_logger()


class MessageItem(object):
    def __init__(self, **kwargs):
        self.type = kwargs.get("type")
        self.timestamp = kwargs.get("timestamp")
        self.payload = kwargs.get("payload", {})
        self.destination = kwargs.get("destination")
        self.correlation_id = kwargs.get("correlation_id", "admin-request")

    def to_json(self):
        return json.dumps(
            self, default=lambda o: o.__dict__, sort_keys=True, indent=4)

    def get_type(self):
        message_type = self.get_message_type()
        command = self.get_command()
        return command if message_type == 'unknown' else message_type

    def get_command(self):
        return self.payload.get('command', 'unknown')

    def get_message_type(self):
        return self.payload.get('message_type', 'unknown')

    def handle(self):
        try:
            event_handled = False

            if self.get_message_type() == "switch"\
                    and self.payload['state'] == "ADDED":
                event_handled = self.create_switch()
            if self.get_message_type() == "switch"\
                    and self.payload['state'] == "ACTIVATED":
                event_handled = self.activate_switch()
            if self.get_message_type() == "switch"\
                    and self.payload['state'] == "DEACTIVATED":
                event_handled = self.deactivate_switch()
            if self.get_message_type() == "switch"\
                    and self.payload['state'] == "REMOVED":
                event_handled = self.remove_switch()

            if self.get_message_type() == "isl"\
                    and self.payload['state'] == "DISCOVERED":
                event_handled = self.create_isl()
            if self.get_message_type() == "isl"\
                    and self.payload['state'] == "FAILED":
                event_handled = self.isl_discovery_failed()

            if self.get_message_type() == "port":
                if self.payload['state'] == "DOWN":
                    event_handled = self.port_down()
                else:
                    event_handled = True

            if self.get_command() == "flow_create":
                event_handled = self.create_flow()
            if self.get_command() == "flow_delete":
                event_handled = self.delete_flow()
            if self.get_command() == "flow_update":
                event_handled = self.update_flow()

            if self.get_command() == "network":
                event_handled = self.dump_network()

            if not event_handled:
                logger.error('Message was not handled correctly: message=%s',
                             self.payload)

        except Exception as e:
            print e
            traceback.print_exc()

        finally:
            return True

    def activate_switch(self):
        switch_id = self.payload['switch_id']
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
        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switch_id))
        if not switch:
            new_switch = Node("switch",
                              name="{}".format(switch_id),
                              state="active",
                              address=self.payload['address'],
                              hostname=self.payload['hostname'],
                              description=self.payload['description'])
            graph.create(new_switch)
            logger.info('Adding switch: %s', switch_id)
            return True
        else:
            graph.merge(switch)
            switch['state'] = "active"
            switch['address'] = self.payload['address']
            switch['hostname'] = self.payload['hostname']
            switch['description'] = self.payload['description']
            switch.push()
            logger.info('Activating switch: %s', switch_id)
            return True

    def deactivate_switch(self):
        switch_id = self.payload['switch_id']
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
        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switch_id))
        if switch:
            graph.merge(switch)
            switch['state'] = "removed"
            switch.push()
            logger.info('Removing switch: %s', switch_id)

            if self.isl_exists(switch_id, None):
                self.delete_isl(switch_id, None)

        return True

    @staticmethod
    def isl_exists(src_switch, src_port):
        if src_port:
            exists_query = ("MATCH (a:switch)-[r:isl {{"
                            "src_switch: '{}', "
                            "src_port: {}}}]->(b:switch) return r")
            return graph.run(exists_query.format(src_switch, src_port)).data()
        else:
            exists_query = ("MATCH (a:switch)-[r:isl {{"
                            "src_switch: '{}'}}]->(b:switch) return r")
            return graph.run(exists_query.format(src_switch)).data()

    @staticmethod
    def delete_isl(src_switch, src_port):
        logger.info('Removing ISL: src_switch=%s, src_port=%s',
                    src_switch, src_port)

        if src_port:
            delete_query = ("MATCH (a:switch)-[r:isl {{"
                            "src_switch: '{}', "
                            "src_port: {}}}]->(b:switch) delete r")
            result = graph.run(
                delete_query.format(src_switch, src_port)).data()
        else:
            delete_query = ("MATCH (a:switch)-[r:isl {{"
                            "src_switch: '{}'}}]->(b:switch) delete r")
            result = graph.run(delete_query.format(src_switch)).data()

        logger.info('Removed ISL: %s', result)

        return True

    def isl_discovery_failed(self):
        path = self.payload['path']
        switch_id = path[0]['switch_id']
        port_id = int(path[0]['port_no'])
        if self.isl_exists(switch_id, port_id):
            return self.delete_isl(switch_id, port_id)
        else:
            return True

    def port_down(self):
        switch_id = self.payload['switch_id']
        port_id = int(self.payload['port_no'])
        if self.isl_exists(switch_id, port_id):
            return self.delete_isl(switch_id, port_id)
        else:
            return True

    def create_isl(self):
        path = self.payload['path']
        latency = int(self.payload['latency_ns'])
        a_switch = path[0]['switch_id']
        a_port = int(path[0]['port_no'])
        b_switch = path[1]['switch_id']
        b_port = int(path[1]['port_no'])
        speed = int(self.payload['speed'])
        available_bandwidth = int(self.payload['available_bandwidth'])

        a_switch_node = graph.find_one('switch',
                                       property_key='name',
                                       property_value='{}'.format(a_switch))
        b_switch_node = graph.find_one('switch',
                                       property_key='name',
                                       property_value='{}'.format(b_switch))

        if not a_switch_node or not b_switch_node:
            return False

        isl_lock.acquire()
        try:
            isl_exists_query = ("MATCH (a:switch)-[r:isl {{"
                                "src_switch: '{}', "
                                "src_port: {}, "
                                "dst_switch: '{}', "
                                "dst_port: {}}}]->(b:switch) return r")
            isl_exists = graph.run(isl_exists_query.format(a_switch,
                                                           a_port,
                                                           b_switch,
                                                           b_port)).data()

            if not isl_exists:
                isl_query = ("MATCH (u:switch {{name:'{}'}}), "
                             "(r:switch {{name:'{}'}}) "
                             "MERGE (u)-[:isl {{"
                             "src_port: {}, "
                             "dst_port: {}, "
                             "src_switch: '{}', "
                             "dst_switch: '{}', "
                             "latency: {}, "
                             "speed: {}, "
                             "available_bandwidth: {}}}]->(r)")
                graph.run(isl_query.format(a_switch_node['name'],
                                           b_switch_node['name'],
                                           a_port,
                                           b_port,
                                           a_switch,
                                           b_switch,
                                           latency,
                                           speed,
                                           available_bandwidth))

                logger.info('ISL between %s and %s created',
                            a_switch_node['name'], b_switch_node['name'])
            else:
                isl_update_query = ("MATCH (a:switch)-[r:isl {{"
                                    "src_switch: '{}', "
                                    "src_port: {}, "
                                    "dst_switch: '{}', "
                                    "dst_port: {}}}]->(b:switch) "
                                    "set r.latency = {} return r")
                graph.run(isl_update_query.format(a_switch,
                                                  a_port,
                                                  b_switch,
                                                  b_port,
                                                  latency)).data()

                logger.debug('ISL between %s and %s updated',
                             a_switch_node['name'], b_switch_node['name'])

        except Exception as e:
            logger.exception('ISL between %s and %s creation error: %s',
                             a_switch_node['name'], b_switch_node['name'],
                             e.message)

        finally:
            isl_lock.release()

        return True

    def create_flow(self):
        correlation_id = self.correlation_id
        flow = self.payload['payload']
        flow_id = flow['flowid']

        logger.info('Flow create request: correlation_id=%s, flow=%s',
                    correlation_id, flow)

        try:
            rules = flow_utils.build_rules(flow)

            logger.info('Flow rules were built: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            flow_utils.store_flow(flow)

            logger.info('Flow was stored: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            message_utils.send_install_commands(rules, correlation_id)

            logger.info('Flow rules installed: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            payload = {'payload': flow, 'message_type': "flow"}
            message_utils.send_message(payload, correlation_id, "INFO")

        except Exception as e:
            logger.exception('Can not create flow: %s', flow_id)
            message_utils.send_error_message(
                correlation_id, "CREATION_FAILURE", e.message, flow_id)
            raise

        return True

    def delete_flow(self):
        correlation_id = self.correlation_id
        flow = self.payload['payload']
        flow_id = flow['flowid']

        try:
            logger.info('Flow delete request: correlation_id=%s, flow=%s',
                        correlation_id, flow)

            flow_utils.remove_flow(flow, flow['flowpath'])

            logger.info('Flow was removed: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            message_utils.send_delete_commands(
                flow['flowpath'], flow_id, correlation_id, int(flow['cookie']))

            logger.info('Flow rules removed: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            payload = {'payload': flow, 'message_type': "flow"}
            message_utils.send_message(payload, correlation_id, "INFO")

        except Exception as e:
            logger.exception('Can not delete flow: %s', e.message)
            message_utils.send_error_message(
                correlation_id, "DELETION_FAILURE", e.message, flow_id)
            raise

        return True

    def update_flow(self):
        correlation_id = self.correlation_id
        flow = self.payload['payload']
        flow_id = flow['flowid']

        logger.info('Flow update request: correlation_id=%s, flow=%s',
                    correlation_id, flow)

        try:

            old_flow = flow_utils.get_old_flow(flow)
            old_flow_path = json.loads(old_flow['flowpath'])

            flow_utils.remove_flow(old_flow, old_flow_path)

            logger.info('Flow was removed: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            rules = flow_utils.build_rules(flow)

            logger.info('Flow rules were built: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            flow_utils.store_flow(flow)

            logger.info('Flow was stored: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            message_utils.send_install_commands(rules, correlation_id)

            logger.info('Flow rules installed: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            message_utils.send_delete_commands(
                old_flow_path, old_flow['flowid'],
                correlation_id, int(old_flow['cookie']))

            logger.info('Flow rules removed: correlation_id=%s, flow_id=%s',
                        correlation_id, flow_id)

            payload = {'payload': flow, 'message_type': "flow"}
            message_utils.send_message(payload, correlation_id, "INFO")

        except Exception as e:
            logger.exception('Can not update flow: %s', e.message)
            message_utils.send_error_message(
                correlation_id, "UPDATE_FAILURE", e.message, flow_id)
            raise

        return True

    @staticmethod
    def get_flows():
        try:
            flows = []
            for data in flow_utils.get_flows():
                flows.append(data['r'])

            print 'Got flows={}'.format(flows)

        except Exception as exception:

            print "Error: " \
                  "could not get flows: {}".format(exception.message)
            traceback.print_exc()
            raise

        return flows

    @staticmethod
    def get_switches():
        try:
            query = "MATCH (n:switch) RETURN n"
            result = graph.run(query).data()

            switches = []
            for data in result:
                switch = data['n']
                if switch:
                    switches.append(switch)

            print 'Got switches={}'.format(switches)

        except Exception as exception:
            print "Error: " \
                  "could not get switches: {}".format(exception.message)
            traceback.print_exc()
            raise

        return switches

    @staticmethod
    def get_isls():
        try:
            query = "MATCH (r:isl) RETURN r"
            result = graph.run(query).data()

            isls = []
            for data in result:
                isl = data['r']
                if isl:
                    isls.append(isl)

            print 'Got isls={}'.format(isls)

        except Exception as exception:
            print "Error: " \
                  "could not get isls: {}".format(exception.message)
            traceback.print_exc()
            raise

        return isls

    def dump_network(self):
        correlation_id = self.correlation_id
        step = "Init"
        logger.info('Dump network request: correlation_id=%s', correlation_id)

        try:
            step = "Switches"
            switches = self.get_switches()
            logger.debug("%s: %s", step, switches)

            step = "ISLs"
            isls = self.get_isls()
            logger.debug("%s: %s", step, isls)

            step = "Flows"
            flows = self.get_flows()
            logger.debug("%s: %s", step, flows)

            step = "Send"
            payload = {
                'payload': {
                    'switches': switches,
                    'isls': isls,
                    'flows': flows},
                'message_type': "network"}
            message_utils.send_message(
                payload, correlation_id, "INFO", "WFM_CACHE")

        except Exception as e:
            logger.exception('Can not dump network: %s', e.message)
            message_utils.send_error_message(
                correlation_id, "INTERNAL_ERROR", e.message, step, "WFM_CACHE")
            raise

        return True
