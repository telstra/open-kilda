# Copyright 2018 Telstra Open Source
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

from service.cmd import vsctl, ofctl, run_cmd
from urllib.parse import urlparse
import socket
import logging

logger = logging.getLogger()

A_SW_DEF = {'name': 'aswitch', 'dp_id': '00:00:00:00:00:00:00:00', 'status': 'active'}
A_SW_NAME = A_SW_DEF['name']


def pname(sw, port):
    return '{}-{}'.format(sw, port)


def resolve_host(uri):
    parts = uri.split(":", 3)
    assert len(parts) == 3, "Controller uri '%s' is incorrect" % uri

    try:
        ip = socket.gethostbyname(parts[1])
    except socket.gaierror as e:
        raise Exception("Couldn't resolve host '%s', seems like Floodlight is down" % parts[1]) from e # noqa
    return parts[0] + ":" + ip + ":" + parts[2]


class Switch:
    def __init__(self, name, of_ver):
        self.name = name
        self.of_ver = of_ver
        self.vscmd = []

    def get_and_flush_batch_cmd(self):
        cmd = " -- ".join(self.vscmd)
        self.vscmd = []
        return cmd

    @classmethod
    def create(cls, sw_def):
        # force OF 1.3
        of_ver = 'OpenFlow13'
        name = sw_def['name']
        switch = cls(name, of_ver)

        cmd = [
            '--if-exists del-br %s' % name,
            'add-br %s' % name,
            'set bridge {} other-config:datapath-id={}'.format(name, sw_def['dp_id'].replace(':', '')),
            'set bridge %s fail_mode=secure' % name,
            'set bridge {} protocols={}'.format(name, of_ver)
        ]

        switch.vscmd.extend(cmd)
        return switch

    @classmethod
    def create_with_ports(cls, sw_def):
        sw = Switch.create(sw_def)
        sw.define_ports(range(1, sw_def['max_port'] + 1))
        return sw

    def destroy(self):
        self.vscmd.extend((['del-br {}'.format(self.name)]))

    def define_ports(self, ports):
        cmd = []
        for pnum in ports:
            cmd.append('add-port {} {}'.format(self.name, pname(self.name, str(pnum))))
        self.vscmd.extend(cmd)

    def setup_port(self, p_name, p_num, bandwidth=None):
        cmd = [
            'set interface {} ofport_request={}'.format(p_name, p_num),
        ]
        if bandwidth:
            cmd.append('set interface {} ingress_policing_rate={}'.format(p_name, bandwidth))
        self.vscmd.extend(cmd)

    def dump_flows(self):
        return ofctl(["dump-flows {sw} -O {of_ver}".format(sw=self.name, of_ver=self.of_ver)])[0]

    def mod_port_state(self, ports, port_state):
        if ports and port_state:
            ofctl(["mod-port {sw} -O {of_ver} {port} {port_state}"
                  .format(sw=self.name, of_ver=self.of_ver, port=str(port), port_state=port_state) for port in ports])

    def add_controller(self, controller, batch=True):
        cmd = [
            'set-controller {} {}'.format(self.name, controller),
            'set controller {} connection-mode=out-of-band'.format(self.name)
        ]
        self.vscmd.extend(cmd) if batch else vsctl(cmd)


class ASwitch(Switch):
    def add_route_flows(self, mappings):
        if mappings:
            ofctl(['add-flow {sw} -O {of_ver} in_port={in_port},actions=output={out_port}'
                  .format(sw=self.name, of_ver=self.of_ver, **flow) for flow in mappings])

    def del_route_flows(self, mappings):
        if mappings:
            ofctl(['del-flows {sw} -O {of_ver} in_port={in_port}'
                  .format(sw=self.name, of_ver=self.of_ver, **flow) for flow in mappings])


class Link:
    def __init__(self, src, src_port, dst, dst_port, bandwidth, is_isl):
        self.src = src
        self.src_port = src_port
        self.dst = dst
        self.dst_port = dst_port
        self.bandwidth = bandwidth
        self.is_isl = is_isl

    def src_name(self):
        return pname(self.src, self.src_port)

    def dst_name(self):
        return pname(self.dst, self.dst_port)

    @classmethod
    def create(cls, src, src_port, dst, dst_port, bandwidth=None, is_isl=True):
        link = cls(src, src_port, dst, dst_port, bandwidth, is_isl)

        run_cmd('ip link add {} type veth peer name {}'.format(link.src_name(), link.dst_name()))
        run_cmd('ip link set up {}'.format(link.src_name()))
        run_cmd('ip link set up {}'.format(link.dst_name()))

        return link

    def destroy(self):
        run_cmd('ip link delete {}'.format(self.src_name()))

    def setup_switch_ports(self, switches):
        switches[self.src].setup_port(self.src_name(), self.src_port, self.bandwidth)
        if self.is_isl:
            switches[self.dst].setup_port(self.dst_name(), self.dst_port, self.bandwidth)


class Traffgen:
    def __init__(self, tgen_def):
        self.name = tgen_def['name']
        self.iface = tgen_def['iface_name']
        self.sw = tgen_def['switch_connected']
        self.sw_port = tgen_def['switch_port']
        ctrl_url = urlparse(tgen_def['control_endpoint'])

        # listen rest on all interfaces
        self.endpoint = '%s:%s' % ('0.0.0.0', str(ctrl_url.port))
        self.proc = None

    def make_link(self):
        return Link.create(self.sw, self.sw_port, self.name, self.iface, is_isl=False)

    def run(self):
        self.proc = run_cmd('kilda-traffexam {} {}'.format(pname(self.name, self.iface), self.endpoint), sync=False)

    def destroy(self):
        if self.proc:
            self.proc.terminate()


class Topology:
    def __init__(self, switches, links, traffgens, controller):
        self.switches = switches
        self.links = links
        self.traffgens = traffgens
        self.controller = controller

    @classmethod
    def create(cls, topo_def):
        switches = {}
        links = []
        traffgens = []
        a_mappings = []
        a_ports = set()

        for isl_def in topo_def['isls']:
            src = isl_def['src_switch']
            src_port = isl_def['src_port']
            dst = isl_def.get('dst_switch', None)
            dst_port = isl_def.get('dst_port', None)
            bandwidth = isl_def.get('max_bandwidth', None)
            a_def = isl_def.get('aswitch', None)

            if a_def:
                if a_def.get('in_port', None):
                    a_ports.add(a_def['in_port'])
                if a_def.get('out_port', None):
                    a_ports.add(a_def['out_port'])

                links.append(Link.create(src, src_port, A_SW_NAME, a_def['in_port'], bandwidth))
                if dst and dst_port:
                    links.append(Link.create(A_SW_NAME, a_def['out_port'], dst, dst_port, bandwidth))
                    a_mappings.append(
                        {'in_port': a_def['in_port'], 'out_port': a_def['out_port']})
                    a_mappings.append(
                        {'in_port': a_def['out_port'], 'out_port': a_def['in_port']})
            else:
                links.append(Link.create(src, src_port, dst, dst_port, bandwidth))

        for tgen_def in topo_def.get('active_traff_gens', []):
            tg = Traffgen(tgen_def)
            traffgens.append(tg)
            links.append(tg.make_link())

        switches[A_SW_NAME] = ASwitch.create(A_SW_DEF)
        switches[A_SW_NAME].define_ports(a_ports)
        for sw_def in topo_def['switches']:
            sw = Switch.create_with_ports(sw_def)
            switches[sw.name] = sw

        for link in links:
            link.setup_switch_ports(switches)

        cls.batch_switch_cmd(switches)

        switches[A_SW_NAME].add_route_flows(a_mappings)
        controller = resolve_host(topo_def['controller'])
        return cls(switches, links, traffgens, controller)

    # This should be ~ 'getconf ARG_MAX' / 8
    # Empirical constant copied from Mininet
    ARG_MAX = 128000

    @classmethod
    def batch_switch_cmd(cls, switches):
        vsctl_len = len('ovs-vsctl')
        cmds = []
        cmds_len = 0
        for sw in switches.values():
            cmd = sw.get_and_flush_batch_cmd()
            # Don't exceed ARG_MAX
            if len(cmd) + cmds_len + vsctl_len >= cls.ARG_MAX:
                vsctl(cmds)
                cmds = []
                cmds_len = 0
            cmds.append(cmd)
            cmds_len += len(cmd)
        if cmds:
            vsctl(cmds)

    def run(self):
        for sw in self.switches.values():
            if sw.name != A_SW_NAME:
                sw.add_controller(self.controller)
        Topology.batch_switch_cmd(self.switches)

        for tgen in self.traffgens:
            tgen.run()

    def destroy(self):
        for tgen in self.traffgens:
            tgen.destroy()

        for sw in self.switches.values():
            sw.destroy()
        Topology.batch_switch_cmd(self.switches)

        for link in self.links:
            link.destroy()
