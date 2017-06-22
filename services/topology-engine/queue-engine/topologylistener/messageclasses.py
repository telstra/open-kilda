#!/usr/bin/python
import json
import requests
import traceback
import datetime
from py2neo import Node

from flow import *


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
        content = self.payload['payload']
        flow_id = content['flowid']
        cor_id = self.correlation_id
        source = content['source']
        destination = content['destination']
        timestamp = datetime.datetime.utcnow().isoformat()

        print "Flow create request process: correlation_id={}, flow_id={}".format(cor_id, flow_id)

        if source['switch-id'] == destination['switch-id']:
            cookie = allocate_cookie()
            transit_vlan_forward = transit_vlan_reverse = 0
        else:
            cookie, transit_vlan_forward, transit_vlan_reverse = allocate_resources()

        if cookie is None or transit_vlan_forward is None or transit_vlan_reverse is None:
            print "ERROR: resource allocation failure"
            deallocate_resources(cookie, transit_vlan_forward, transit_vlan_reverse)
            send_error_message(cor_id, "CREATION_FAILURE", flow_id)

        try:
            (all_flows, forward_flow_switches, reverse_flow_switches, forward_links, reverse_links) = \
                create_flows(content, transit_vlan_forward, transit_vlan_reverse, cookie)

            if not forward_flow_switches or not reverse_flow_switches:
                print "ERROR: could not find path: all_flows={}, flow_path f={} r={}, isl_path f={} r={}".format(
                    all_flows, forward_flow_switches, reverse_flow_switches, forward_links, reverse_links)
                deallocate_resources(cookie, transit_vlan_forward, transit_vlan_reverse)
                send_error_message(cor_id, "CREATION_FAILURE", flow_id)
                return False

            print "Flow was prepared: correlation_id={}, flow_id={}, flow_path f={} r={}, isl_path f={} r={}".format(
                cor_id, flow_id, forward_flow_switches, reverse_flow_switches, forward_links, reverse_links)

            (start, end) = find_nodes(source, destination)

            if not start or not end:
                print "ERROR: switches were not found: start_node={}, end_node={}".format(start, end)
                deallocate_resources(cookie, transit_vlan_forward, transit_vlan_reverse)
                send_error_message(cor_id, "CREATION_FAILURE", flow_id)
                return False

            print "Nodes were found: correlation_id={}, flow_id={}, start={}, end={}".format(cor_id, flow_id, start, end)

            store_flows(start, end, content, cookie, transit_vlan_forward, transit_vlan_reverse, timestamp,
                        forward_flow_switches, reverse_flow_switches, forward_links, reverse_links)

            print 'Flow was stored: correlation_id={}, flow_id={}'.format(cor_id, flow_id)

            send_install_commands(all_flows, cor_id)

            print 'Flow rules were installed: correlation_id={}, flow_id={}'.format(cor_id, flow_id)

            content['cookie'] = cookie
            content['last-updated'] = timestamp
            payload = {'payload': content, 'message_type': "flow"}
            send_message(payload, cor_id, "INFO")

        except Exception:
            deallocate_resources(cookie, transit_vlan_forward, transit_vlan_reverse)
            traceback.print_exc()
            raise

        return True

    def delete_flow(self):
        content = self.payload['payload']
        flow_id = content['flowid']
        cor_id = self.correlation_id

        try:
            print "Flow delete request process: correlation_id={}, flow_id={}".format(cor_id, flow_id)

            (data, cookie, bandwidth, transit_vlan_forward, transit_vlan_reverse,
             forward_flow_switches, reverse_flow_switches, forward_links, reverse_links) = find_flow_path(flow_id)

            if not forward_flow_switches or not reverse_flow_switches:
                print "ERROR: could not find path: flow_path f={} r={}, isl_path f={} r={}".format(
                    forward_flow_switches, reverse_flow_switches, forward_links, reverse_links)
                send_error_message(cor_id, "DELETION_FAILURE", flow_id)
                return False

            print "Flow path was found: correlation_id={}, flow_id={}".format(cor_id, flow_id)

            delete_flows_from_database_by_flow_id(flow_id, bandwidth, forward_links, reverse_links)

            print "Flow was removed from database: correlation_id={}, flow_id={}, bandwidth={}, isl_paths f={} r={}".format(
                cor_id, flow_id, bandwidth, forward_links, reverse_links)

            switches = list(set(forward_flow_switches + reverse_flow_switches))
            send_remove_commands(switches, flow_id, cor_id, cookie)

            print "Flow rules were removed: correlation_id={}, flow_id={}, cookie={}, switches={},".format(
                cor_id, flow_id, cookie, switches)

            deallocate_resources(cookie, transit_vlan_forward, transit_vlan_reverse)

            flow = flow_response(data)
            payload = {'payload': flow, 'message_type': "flow"}
            send_message(payload, cor_id, "INFO")

        except Exception:
            traceback.print_exc()
            raise

        return True

    def update_flow(self):
        content = self.payload['payload']
        flow_id = content['flowid']
        cor_id = self.correlation_id
        source = content['source']
        destination = content['destination']
        timestamp = datetime.datetime.utcnow().isoformat()

        print "Flow update request process: correlation_id={}, flow_id={}".format(cor_id, flow_id)

        if source['switch-id'] == destination['switch-id']:
            new_cookie = allocate_cookie()
            new_transit_vlan_forward = new_transit_vlan_reverse = 0
        else:
            new_cookie, new_transit_vlan_forward, new_transit_vlan_reverse = allocate_resources()

        if new_cookie is None or new_transit_vlan_forward is None or new_transit_vlan_reverse is None:
            print "ERROR: resource allocation failure"
            deallocate_resources(new_cookie, new_transit_vlan_forward, new_transit_vlan_reverse)
            send_error_message(cor_id, "UPDATE_FAILURE", flow_id)

        try:
            (data, old_cookie, old_bandwidth, old_transit_vlan_forward, old_transit_vlan_reverse, old_forward_flow_switches,
             old_reverse_flow_switches, old_forward_links, old_reverse_links) = find_flow_path(flow_id)

            if not old_forward_flow_switches or not old_reverse_flow_switches:
                print "ERROR: could not find path: flow_path f={} r={}, isl_path f={} r={}".format(
                    old_forward_flow_switches, old_reverse_flow_switches, old_forward_links, old_reverse_links)
                deallocate_resources(new_cookie, new_transit_vlan_forward, new_transit_vlan_reverse)
                send_error_message(cor_id, "UPDATE_FAILURE", flow_id)
                return False

            print "Flow path was found: correlation_id={}, flow_id={}".format(cor_id, flow_id)

            relationships_ids = find_flow_relationships_ids(flow_id)
            delete_flows_from_database_by_relationship_ids(relationships_ids, old_forward_links,
                                                           old_reverse_links, old_bandwidth)

            print "Flow was removed from database: correlation_id={}, flow_id={}, bandwidth={}, isl_paths f={} r={}".format(
                cor_id, flow_id, old_bandwidth, old_forward_links, old_reverse_links)

            (all_flows, new_forward_flow_switches, new_reverse_flow_switches, new_forward_links, new_reverse_links) = \
                create_flows(content, new_transit_vlan_forward, new_transit_vlan_reverse, new_cookie)

            if not new_forward_flow_switches or not new_reverse_flow_switches:
                deallocate_resources(new_cookie, new_transit_vlan_forward, new_transit_vlan_reverse)
                print "ERROR: could not find path: all_flows={}, flow_path f={} r={}, isl_path f={} r={}".format(
                    all_flows, new_forward_flow_switches, new_reverse_flow_switches, new_forward_links, new_reverse_links)
                send_error_message(cor_id, "UPDATE_FAILURE", flow_id)
                return False

            print "Flow was prepared: correlation_id={}, flow_id={}, flow_path f={} r={}, isl_path f={} r={}".format(
                cor_id, flow_id, new_forward_flow_switches, new_reverse_flow_switches, new_forward_links, new_reverse_links)

            (start, end) = find_nodes(source, destination)

            if not start or not end:
                print "ERROR: switches were not found: start_node={}, end_node={}".format(start, end)
                deallocate_resources(new_cookie, new_transit_vlan_forward, new_transit_vlan_reverse)
                send_error_message(cor_id, "UPDATE_FAILURE", flow_id)
                return False

            print "Nodes were found: correlation_id={}, flow_id={}, start={}, end={}".format(cor_id, flow_id, start, end)

            store_flows(start, end, content, new_cookie, new_transit_vlan_forward, new_transit_vlan_reverse, timestamp,
                        new_forward_flow_switches, new_reverse_flow_switches, new_forward_links, new_reverse_links)

            print 'Flow was stored: correlation_id={}, flow_id={}'.format(cor_id, flow_id)

            send_install_commands(all_flows, cor_id)

            print 'Flow rules were installed: correlation_id={}, flow_id={}'.format(cor_id, flow_id)

            old_switches = list(set(old_forward_flow_switches + old_reverse_flow_switches))
            send_remove_commands(old_switches, flow_id, cor_id, old_cookie)

            print "Flow rules were removed: correlation_id={}, flow_id={}, cookie={}, switches={},".format(
                cor_id, flow_id, old_cookie, old_switches)

            deallocate_resources(old_cookie, old_transit_vlan_forward, old_transit_vlan_reverse)

            content['cookie'] = new_cookie
            content['last-updated'] = timestamp
            payload = {'payload': content, 'message_type': "flow"}
            send_message(payload, cor_id, "INFO")

        except Exception:
            deallocate_resources(new_cookie, new_transit_vlan_forward, new_transit_vlan_reverse)
            traceback.print_exc()
            raise

        return True

    def get_flow(self):
        flow_id = self.payload['payload']['flowid']

        try:
            flows = find_flow_by_id(flow_id)

            for data in flows:
                flow = flow_response(data['r'])
                if flow:
                    print 'Got flow={}'.format(flow)
                    payload = {'payload': flow, 'message_type': "flow"}
                    send_message(payload, self.correlation_id, "INFO")

        except Exception:
            traceback.print_exc()
            raise

        return True

    def get_flow_path(self):
        flow_id = self.payload['payload']['flowid']

        try:
            found_flow = find_flow_by_id(flow_id)
            flow = found_flow[0]['r']

            print 'Got flow={}'.format(flow)

            payload = {'payload': {'flowid': flow_id, 'flowpath': flow['flowpath']}, 'message_type': "flow_path"}
            send_message(payload, self.correlation_id, "INFO")

        except Exception:
            traceback.print_exc()
            raise

        return True

    def dump_flows(self):
        try:
            query = "MATCH (a:switch)-[r:flow ]->(b:switch) {} r"
            result = graph.run(query.format("return")).data()

            flows = []
            for flow in result:
                flows.append(flow_response(flow['r']))
            print 'Got flows={}'.format(flows)

            payload = {'payload': {'flow-list': flows}, 'message_type': "flows"}
            send_message(payload, self.correlation_id, "INFO")

        except Exception:
            traceback.print_exc()
            raise

        return True
