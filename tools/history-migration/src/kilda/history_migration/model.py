#  Copyright 2021 Telstra Open Source
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.


class _EntityBase:
    def __init__(self, time_create=None, time_modify=None):
        self.time_create = time_create
        self.time_modify = time_modify


class FlowEvent(_EntityBase):
    def __init__(
            self, event_id, event_time, flow_id, event_name,
            actor=None, details=None, actions=(), dumps=(), **kwargs):
        super().__init__(**kwargs)
        self.event_id = event_id
        self.event_time = event_time
        self.flow_id = flow_id
        self.event_name = event_name
        self.actor = actor
        self.details = details

        self.actions = tuple(actions)
        self.dumps = tuple(dumps)


class FlowEventAction(_EntityBase):
    def __init__(self, action_time, action_name, details=None, **kwargs):
        super().__init__(**kwargs)
        self.action_time = action_time
        self.action_name = action_name
        self.details = details


class FlowEventDump(_EntityBase):
    def __init__(
            self, kind, flow_id, bandwidth, max_latency, ignore_bandwidth,
            allocate_protected_path, encapsulation_type,
            path_computation_strategy,
            a_to_z_cookie, z_to_a_cookie,
            a_end_switch, a_end_port, a_end_vlan, a_end_inner_vlan,
            z_end_switch, z_end_port, z_end_vlan, z_end_inner_vlan,
            a_to_z_path, a_to_z_status,
            z_to_a_path, z_to_a_status,
            pinned=False, periodic_ping=False,
            diverse_group_id=None, affinity_group_id=None, loop_switch=None,
            a_to_z_meter_id=None, z_to_a_meter_id=None, **kwargs):
        super().__init__(**kwargs)
        self.kind = kind
        self.flow_id = flow_id
        self.bandwidth = bandwidth
        self.max_latency = max_latency
        self.ignore_bandwidth = ignore_bandwidth
        self.allocate_protected_path = allocate_protected_path
        self.encapsulation_type = encapsulation_type
        self.path_computation_strategy = path_computation_strategy

        self.a_to_z_cookie = a_to_z_cookie
        self.z_to_a_cookie = z_to_a_cookie

        self.a_end_switch = a_end_switch
        self.a_end_port = a_end_port
        self.a_end_vlan = a_end_vlan
        self.a_end_inner_vlan = a_end_inner_vlan

        self.z_end_switch = z_end_switch
        self.z_end_port = z_end_port
        self.z_end_vlan = z_end_vlan
        self.z_end_inner_vlan = z_end_inner_vlan

        self.a_to_z_path = a_to_z_path
        self.a_to_z_status = a_to_z_status

        self.z_to_a_path = z_to_a_path
        self.z_to_a_status = z_to_a_status

        self.pinned = pinned
        self.periodic_ping = periodic_ping

        self.diverse_group_id = diverse_group_id
        self.affinity_group_id = affinity_group_id
        self.loop_switch = loop_switch

        self.a_to_z_meter_id = a_to_z_meter_id
        self.z_to_a_meter_id = z_to_a_meter_id


class PortEvent(_EntityBase):
    def __init__(
            self, event_id, event_time, switch_id, port_number, event_kind,
            up_count=0, down_count=0, **kwargs):
        super().__init__(**kwargs)
        self.event_id = event_id
        self.event_time = event_time
        self.switch_id = switch_id
        self.port_number = port_number
        self.event_kind = event_kind
        self.up_count = up_count
        self.down_count = down_count


class TypeTag:
    def __init__(self, tag):
        self.tag = tag


FLOW_EVENT_TAG = TypeTag('flow-event')
PORT_EVENT_TAG = TypeTag('port-event')
