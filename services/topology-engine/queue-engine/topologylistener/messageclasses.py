#!/usr/bin/python
import json
import requests
import traceback
import datetime
from py2neo import Node

import flow_utils
from flow_utils import graph


available_bandwidth_limit_factor = 0.9


def repair_flows(switchid):
    flows = (graph.run("MATCH (n)-[r:flow]-(m) where any(i in r.flowpath where i = '{}') return r".format(switchid))).data()
    repairedFlowIDs = []
    for flow in flows:
        if flow['r']['flowid'] not in repairedFlowIDs:
            repairedFlowIDs.append(flow['r']['flowid'])
            url = "http://topology-engine-rest/api/v1/flow"
            headers = {'Content-Type': 'application/json'}
            j_data = {"src_switch":"{}".format(flow['r']['src_switch']),
                      "src_port":1,
                      "src_vlan":0,
                      "dst_switch":"{}".format(flow['r']['dst_switch']),
                      "dst_port":1, "dst_vlan":0, "bandwidth": 2000}
            result = requests.post(url, json=j_data, headers=headers)
            if result.status_code == 200:
                deleteURL = url + "/" + flow['r']['flowid']
                result = requests.delete(deleteURL)
            else:
                #create logic to alert on failed reroute
                print "Unable to reroute flow: {}".format(flow['r']['flowid'])
    return True



class MessageItem(object):
    def __init__(self, **kwargs):
        self.type = kwargs.get("type")
        self.timestamp = kwargs.get("timestamp")
        self.payload = kwargs.get("payload")
        # make message processable in case of no destination in body
        self.destination = kwargs.get("destination", "TOPOLOGY_ENGINE")
        self.correlation_id = kwargs.get("correlation_id", "admin-request")

    def to_json(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=True, indent=4)

    def get_type(self):
        message_type = self.get_message_type()
        if message_type == "unknown":
            message_type = self.get_command()
        return message_type

    def get_command(self):
        try:
            return self.payload['command']
        except:
            return "unknown"

    def get_message_type(self):
        try:
            return self.payload['message_type']
        except:
            return "unknown"

    def handle(self):
        try:
            eventHandled = False
            if self.get_message_type() == "switch" and self.payload['state'] == "ADDED":
                eventHandled = self.create_switch()
            if self.get_message_type() == "switch" and self.payload['state'] == "ACTIVATED":
                eventHandled = self.activate_switch()
            if self.get_message_type() == "isl":
                eventHandled = self.create_isl()
            if self.get_message_type() == "port":
                eventHandled = True #needs to handled correctly
            if self.get_message_type() == "switch" and self.payload['state'] == "DEACTIVATED":
                eventHandled = self.deactivate_switch()
            if self.get_message_type() == "switch" and self.payload['state'] == "REMOVED":
                eventHandled = self.remove_switch()
            if self.get_command() == "flow_create":
                eventHandled = self.create_flow()
            if self.get_command() == "flow_delete":
                eventHandled = self.delete_flow()
            if self.get_command() == "flow_update":
                eventHandled = self.update_flow()
            if self.get_command() == "flow_path":
                eventHandled = self.get_flow_path()
            if self.get_command() == "flow_get":
                eventHandled = self.get_flow()
            if self.get_command() == "flows_get":
                eventHandled = self.dump_flows()
            return eventHandled
        except Exception as e:
            print e

    def activate_switch(self):
        switchid = self.payload['switch_id']
        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switchid))
        if switch:
            graph.merge(switch)
            switch['state'] = "active"
            switch.push()
            print "Activating switch: {}".format(switchid)
        return True

    def create_switch(self):
        switchid = self.payload['switch_id']
        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switchid))
        if not switch:
            newSwitch = Node("switch",
                             name="{}".format(switchid),
                             state="active",
                             address=self.payload['address'],
                             hostname=self.payload['hostname'],
                             description=self.payload['description'])
            graph.create(newSwitch)
            print "Adding switch: {}".format(switchid)
            return True
        else:
            graph.merge(switch)
            switch['state'] = "active"
            switch['address'] = self.payload['address']
            switch['hostname'] = self.payload['hostname']
            switch['description'] = self.payload['description']
            switch.push()
            print "Activating switch: {}".format(switchid)
            return True

    def deactivate_switch(self):
        switchid = self.payload['switch_id']
        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switchid))
        if switch:
            graph.merge(switch)
            switch['state'] = "inactive"
            switch.push()
            print "Deactivating switch: {}".format(switchid)
            if repair_flows(switchid):
                return True
            else:
                return False

        else:
            print "Switch '{0}' does not exist in topology.".format(switchid)
            return True

    def remove_switch(self):
        switchid = self.payload['switch_id']
        switch = graph.find_one('switch',
                                property_key='name',
                                property_value='{}'.format(switchid))
        if switch:
            graph.merge(switch)
            switch['state'] = "removed"
            switch.push()
            print "Removing switch: {}".format(switchid)
        return True

    def create_isl(self):

        path = self.payload['path']
        latency = self.payload['latency_ns']
        a_switch = path[0]['switch_id']
        a_port = path[0]['port_no']
        b_switch = path[1]['switch_id']
        b_port = path[1]['port_no']
        speed = self.payload['speed']
        available_bandwidth = int(speed * available_bandwidth_limit_factor)

        a_switchNode = graph.find_one('switch',
                                      property_key='name',
                                      property_value='{}'.format(a_switch))
        b_switchNode = graph.find_one('switch',
                                      property_key='name',
                                      property_value='{}'.format(b_switch))

        if not a_switchNode or not b_switchNode:
            return False

        islExistsQuery = "MATCH (a:switch)-[r:isl {{src_switch: '{}', src_port: '{}', dst_switch: '{}', dst_port: '{}'}}]->(b:switch) return r"
        islExists = graph.run(islExistsQuery.format(a_switch,
                                                    a_port,
                                                    b_switch,
                                                    b_port)).data()

        if not islExists:
            islQuery = "MATCH (u:switch {{name:'{}'}}), (r:switch {{name:'{}'}}) " \
                       "MERGE (u)-[:isl {{" \
                       "src_port: '{}', " \
                       "dst_port: '{}', " \
                       "src_switch: '{}', " \
                       "dst_switch: '{}', " \
                       "latency: '{}', " \
                       "speed: '{}', " \
                       "available_bandwidth: {}}}]->(r)"
            graph.run(islQuery.format(a_switchNode['name'],
                                      b_switchNode['name'],
                                      a_port,
                                      b_port,
                                      a_switch,
                                      b_switch,
                                      latency,
                                      speed,
                                      int(available_bandwidth)))

            print "ISL between {} and {} created".format(a_switchNode['name'],
                                                         b_switchNode['name'])
        else:
            islUpdateQuery = "MATCH (a:switch)-[r:isl {{src_switch: '{}', src_port: '{}', dst_switch: '{}', dst_port: '{}'}}]->(b:switch) set r.latency = {} return r"
            graph.run(islUpdateQuery.format(a_switch,
                                            a_port,
                                            b_switch,
                                            b_port,
                                            latency)).data()
            #print "ISL between {} and {} updated".format(a_switchNode['name'],
            #                                             b_switchNode['name'])
        return True




    def create_flow(self):
        cor_id = self.correlation_id
        content = self.payload['payload']
        flow_id = content['flowid']
        source = content['source']
        destination = content['destination']

        timestamp = datetime.datetime.utcnow().isoformat()

        print "Flow create request: " \
              "correlation_id={}, flow_id={}".format(cor_id, flow_id)

        if source['switch-id'] == destination['switch-id']:
            cookie = flow_utils.allocate_cookie()
            forward_vlan = reverse_vlan = 0
        else:
            cookie, forward_vlan, reverse_vlan = \
                flow_utils.allocate_resources()

        if cookie is None or forward_vlan is None or reverse_vlan is None:
            msg = "Resource allocation failure: " \
                  "cookie={}, transit_vlans f={} r={}".format(
                    cookie, forward_vlan, reverse_vlan)
            print "ERROR: {}".format(msg)

            flow_utils.deallocate_resources(
                cookie, forward_vlan, reverse_vlan)
            flow_utils.send_error_message(
                cor_id, "CREATION_FAILURE", msg, flow_id)

            return True

        try:
            (all_flows,
             forward_switches, reverse_switches,
             forward_links, reverse_links) = flow_utils.create_flows(
                    content, forward_vlan, reverse_vlan, cookie)

            if not forward_switches or not reverse_switches:
                msg = "Flow path was not created: " \
                      "flow_path f={} r={}, isl_path f={} r={}".format(
                        forward_switches, reverse_switches,
                        forward_links, reverse_links)
                print "ERROR: {}, all_flows={}".format(msg, all_flows)

                flow_utils.deallocate_resources(
                    cookie, forward_vlan, reverse_vlan)
                flow_utils.send_error_message(
                    cor_id, "CREATION_FAILURE", msg, flow_id)

                return True

            print "Flow was prepared: " \
                  "correlation_id={}, flow_id={}, " \
                  "flow_path f={} r={}, isl_path f={} r={}".format(
                    cor_id, flow_id, forward_switches, reverse_switches,
                    forward_links, reverse_links)

            (start, end) = flow_utils.find_nodes(source, destination)

            if not start or not end:
                msg = "Switches were not found: " \
                      "source={}, destination={}, start={}, end={}".format(
                        source, destination, start, end)
                print "ERROR: {}".format(msg)

                flow_utils.deallocate_resources(
                    cookie, forward_vlan, reverse_vlan)
                flow_utils.send_error_message(
                    cor_id, "CREATION_FAILURE", msg, flow_id)

                return True

            print "Nodes were found: " \
                  "correlation_id={}, flow_id={}, start={}, end={}".format(
                    cor_id, flow_id, start, end)

            flow_utils.store_flows(
                start, end, content, timestamp,
                cookie, forward_vlan, reverse_vlan,
                forward_switches, reverse_switches,
                forward_links, reverse_links)

            print 'Flow was stored: ' \
                  'correlation_id={}, flow_id={}'.format(cor_id, flow_id)

            flow_utils.send_install_commands(all_flows, cor_id)

            print "Flow rules were installed: " \
                  "correlation_id={}, flow_id={}".format(cor_id, flow_id)

            content['cookie'] = cookie
            content['last-updated'] = timestamp
            payload = {'payload': content, 'message_type': "flow"}
            flow_utils.send_message(payload, cor_id, "INFO")

        except Exception as exception:
            flow_utils.deallocate_resources(
                cookie, forward_vlan, reverse_vlan)
            flow_utils.send_error_message(
                cor_id, "CREATION_FAILURE", exception.message, flow_id)

            print "Error: " \
                  "could not create flow: {}".format(exception.message)
            traceback.print_exc()
            raise

        return True

    def delete_flow(self):
        cor_id = self.correlation_id
        content = self.payload['payload']
        flow_id = content['flowid']

        try:
            print "Flow delete request process: " \
                  "correlation_id={}, flow_id={}".format(cor_id, flow_id)

            (data, cookie, bandwidth,
             forward_vlan, reverse_vlan,
             forward_switches, reverse_switches,
             forward_links, reverse_links) = \
                flow_utils.find_flow_path(flow_id)

            if not forward_switches or not reverse_switches:
                msg = "Flow path was not found: " \
                      "flow_path f={} r={}, isl_path f={} r={}".format(
                        forward_switches, reverse_switches,
                        forward_links, reverse_links)
                print "ERROR: {}".format(msg)

                flow_utils.send_error_message(
                    cor_id, "DELETION_FAILURE", msg, flow_id)

                return True

            print "Flow path was found: " \
                  "correlation_id={}, flow_id={}".format(cor_id, flow_id)

            flow_utils.delete_flows_from_database_by_flow_id(
                flow_id, bandwidth, forward_links, reverse_links)

            print "Flow was removed from database: " \
                  "correlation_id={}, flow_id={}, bandwidth={}," \
                  "isl_paths f={} r={}".format(
                    cor_id, flow_id, bandwidth, forward_links, reverse_links)

            switches = \
                list(set(forward_switches + reverse_switches))
            flow_utils.send_remove_commands(
                switches, flow_id, cor_id, cookie)

            print "Flow rules were removed: " \
                  "correlation_id={}, flow_id={}," \
                  "cookie={}, switches={}".format(
                    cor_id, flow_id, cookie, switches)

            flow_utils.deallocate_resources(cookie, forward_vlan, reverse_vlan)

            flow = flow_utils.flow_response(data)
            payload = {'payload': flow, 'message_type': "flow"}
            flow_utils.send_message(payload, cor_id, "INFO")

        except Exception as exception:
            flow_utils.send_error_message(
                cor_id, "DELETION_FAILURE", exception.message, flow_id)

            print "Error: " \
                  "could not delete flow: {}".format(exception.message)
            traceback.print_exc()
            raise

        return True

    def update_flow(self):
        cor_id = self.correlation_id
        content = self.payload['payload']
        flow_id = content['flowid']
        source = content['source']
        destination = content['destination']

        timestamp = datetime.datetime.utcnow().isoformat()

        print "Flow update request process: " \
              "correlation_id={}, flow_id={}".format(cor_id, flow_id)

        if source['switch-id'] == destination['switch-id']:
            new_cookie = flow_utils.allocate_cookie()
            new_forward_vlan = new_reverse_vlan = 0
        else:
            new_cookie, new_forward_vlan, new_reverse_vlan = \
                flow_utils.allocate_resources()

        if new_cookie is None \
                or new_forward_vlan is None \
                or new_reverse_vlan is None:
            msg = "Resource allocation failure: " \
                  "cookie={}, transit_vlans f={} r={}".format(
                    new_cookie, new_forward_vlan, new_reverse_vlan)
            print "ERROR: {}".format(msg)

            flow_utils.deallocate_resources(
                new_cookie, new_forward_vlan, new_reverse_vlan)
            flow_utils.send_error_message(
                cor_id, "UPDATE_FAILURE", msg, flow_id)

            return True

        try:
            (data, old_cookie, old_bandwidth,
             old_forward_vlan, old_reverse_vlan,
             old_forward_switches, old_reverse_switches,
             old_forward_links, old_reverse_links) = \
                flow_utils.find_flow_path(flow_id)

            if not old_forward_switches or not old_reverse_switches:
                msg = "Flow path was not found: " \
                      "flow_path f={} r={}, isl_path f={} r={}".format(
                        old_forward_switches, old_reverse_switches,
                        old_forward_links, old_reverse_links)
                print "ERROR: {}".format(msg)

                flow_utils.deallocate_resources(
                    new_cookie, new_forward_vlan, new_reverse_vlan)
                flow_utils.send_error_message(
                    cor_id, "UPDATE_FAILURE", msg, flow_id)

                return True

            print "Flow path was found: " \
                  "correlation_id={}, flow_id={}".format(cor_id, flow_id)

            relationships_ids = flow_utils.find_flow_relationships_ids(flow_id)
            flow_utils.delete_flows_from_database_by_relationship_ids(
                relationships_ids, old_forward_links,
                old_reverse_links, old_bandwidth)

            print "Flow was removed from database: " \
                  "correlation_id={}, flow_id={}, bandwidth={}, " \
                  "isl_paths f={} r={}".format(
                    cor_id, flow_id, old_bandwidth,
                    old_forward_links, old_reverse_links)

            (all_flows,
             new_forward_switches, new_reverse_switches,
             new_forward_links, new_reverse_links) = flow_utils.create_flows(
                content, new_forward_vlan, new_reverse_vlan, new_cookie)

            if not new_forward_switches or not new_reverse_switches:
                msg = "Flow path was not created: " \
                      "flow_path f={} r={}, isl_path f={} r={}".format(
                        new_forward_switches, new_reverse_switches,
                        new_forward_links, new_reverse_links)
                print "ERROR: {}, all_flows={}".format(msg, all_flows)

                flow_utils.deallocate_resources(
                    new_cookie, new_forward_vlan, new_reverse_vlan)
                flow_utils.send_error_message(
                    cor_id, "UPDATE_FAILURE", msg, flow_id)

                return True

            print "Flow was prepared: " \
                  "correlation_id={}, flow_id={}, " \
                  "flow_path f={} r={}, isl_path f={} r={}".format(
                    cor_id, flow_id, new_forward_switches,
                    new_reverse_switches, new_forward_links, new_reverse_links)

            (start, end) = flow_utils.find_nodes(source, destination)

            if not start or not end:
                msg = "Switches were not found: " \
                      "source={}, destination={}, start={}, end={}".format(
                        source, destination, start, end)
                print "ERROR: {}".format(msg)

                flow_utils.deallocate_resources(
                    new_cookie, new_forward_vlan, new_reverse_vlan)
                flow_utils.send_error_message(
                    cor_id, "UPDATE_FAILURE", msg, flow_id)

                return True

            print "Nodes were found: " \
                  "correlation_id={}, flow_id={}, start={}, end={}".format(
                    cor_id, flow_id, start, end)

            flow_utils.store_flows(
                start, end, content, timestamp,
                new_cookie, new_forward_vlan, new_reverse_vlan,
                new_forward_switches, new_reverse_switches,
                new_forward_links, new_reverse_links)

            print "Flow was stored: " \
                  "correlation_id={}, flow_id={}".format(cor_id, flow_id)

            flow_utils.send_install_commands(all_flows, cor_id)

            print "Flow rules were installed: " \
                  "correlation_id={}, flow_id={}".format(cor_id, flow_id)

            old_switches = \
                list(set(old_forward_switches + old_reverse_switches))
            flow_utils.send_remove_commands(
                old_switches, flow_id, cor_id, old_cookie)

            print "Flow rules were removed: " \
                  "correlation_id={}, flow_id={}, " \
                  "cookie={}, switches={},".format(
                    cor_id, flow_id, old_cookie, old_switches)

            flow_utils.deallocate_resources(
                old_cookie, old_forward_vlan, old_reverse_vlan)

            content['cookie'] = new_cookie
            content['last-updated'] = timestamp
            payload = {'payload': content, 'message_type': "flow"}
            flow_utils.send_message(payload, cor_id, "INFO")

        except Exception as exception:
            flow_utils.deallocate_resources(
                new_cookie, new_forward_vlan, new_reverse_vlan)
            flow_utils.send_error_message(
                cor_id, "UPDATE_FAILURE", exception.message, flow_id)

            print "Error: " \
                  "could not update flow: {}".format(exception.message)
            traceback.print_exc()
            raise

        return True

    def get_flow(self):
        cor_id = self.correlation_id
        flow_id = self.payload['payload']['flowid']

        print "Flow get request: " \
              "correlation_id={}, flow_id={}".format(cor_id, flow_id)

        try:
            found_flow = flow_utils.find_flow_by_id(flow_id)

            if not found_flow:
                msg = "Flow was not found: " \
                      "correlation_id={}, flow_id={}".format(cor_id, flow_id)
                print "ERROR: {}".format(msg)

                flow_utils.send_error_message(
                    cor_id, "NOT_FOUND", msg, flow_id)

                return True

            for data in found_flow:
                flow = flow_utils.flow_response(data['r'])

                if flow:
                    print 'Flow was found: flow={}'.format(flow)
                    payload = {'payload': flow, 'message_type': "flow"}
                    flow_utils.send_message(payload, cor_id, "INFO")

        except Exception as exception:
            flow_utils.send_error_message(
                cor_id, "INTERNAL_ERROR", exception.message, flow_id)

            print "Error: " \
                  "could not get flow: {}".format(exception.message)
            traceback.print_exc()
            raise

        return True

    def get_flow_path(self):
        cor_id = self.correlation_id
        flow_id = self.payload['payload']['flowid']

        print "Flow path request: " \
              "correlation_id={}, flow_id={}".format(cor_id, flow_id)

        try:
            found_flow = flow_utils.find_flow_by_id(flow_id)

            if not found_flow:
                msg = "Flow was not found: " \
                      "correlation_id={}, flow_id={}".format(cor_id, flow_id)
                print "ERROR: {}".format(msg)

                flow_utils.send_error_message(
                    cor_id, "NOT_FOUND", msg, flow_id)

                return True

            for data in found_flow:
                flow = data['r']
                if flow and flow_utils.is_forward_cookie(flow['cookie']):
                    print 'Flow was found: flow={}'.format(flow)
                    payload = {
                        'payload': {
                            'flowid': flow_id,
                            'flowpath': flow['flowpath']},
                        'message_type': "flow_path"}
                    flow_utils.send_message(payload, cor_id, "INFO")

        except Exception as exception:
            flow_utils.send_error_message(
                cor_id, "INTERNAL_ERROR", exception.message, flow_id)

            print "Error: " \
                  "could not get flow path: {}".format(exception.message)
            traceback.print_exc()
            raise

        return True

    def dump_flows(self):
        cor_id = self.correlation_id

        print "Flows dump request: correlation_id={}".format(cor_id)

        try:
            query = "MATCH (a:switch)-[r:flow ]->(b:switch) {} r"
            result = graph.run(query.format("return")).data()

            flows = []
            for data in result:
                flow = flow_utils.flow_response(data['r'])
                if flow:
                    flows.append(flow)

            print 'Got flows={}'.format(flows)

            payload = {
                'payload': {
                    'flow-list': flows},
                'message_type': "flows"}
            flow_utils.send_message(payload, cor_id, "INFO")

        except Exception as exception:
            flow_utils.send_error_message(
                cor_id, "INTERNAL_ERROR", exception.message, "")

            print "Error: " \
                  "could not dump flows: {}".format(exception.message)
            traceback.print_exc()
            raise

        return True
