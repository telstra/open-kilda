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
import errno
import json
import subprocess
import threading
import time

import ipaddress

from kilda.traffexam import context as context_module
from kilda.traffexam import exc
from kilda.traffexam import model
from kilda.traffexam import system


class Abstract(system.NSIPDBMixin, context_module.ContextConsumer):
    def __init__(self, context):
        super().__init__(context)
        self._pool = {}
        self._lock = threading.Lock()

    def create(self, subject):
        with self._lock:
            try:
                item = self._create(subject)
            except exc.ServiceError:
                raise
            except Exception as e:
                raise exc.ServiceCreateError(self, subject) from e
            self._pool[self.key(item)] = item

            return item

    def register(self, subject):
        self._pool[self.key(subject)] = subject

    def list(self):
        return tuple(self._pool.values())

    def lookup(self, key):
        try:
            item = self._pool[key]
        except KeyError:
            raise exc.ServiceLookupError(self, key) from None
        return item

    def delete(self, key, ignore_missing=False):
        with self._lock:
            try:
                subject = self._pool.pop(key)
            except KeyError:
                if not ignore_missing:
                    raise exc.ServiceLookupError(self, key) from None
                return

            try:
                self._delete(subject)
            except exc.ServiceError:
                raise
            except Exception as e:
                self._pool[key] = subject
                raise exc.ServiceDeleteError(self, key, subject) from e

    def _create(self, subject):
        raise NotImplementedError

    def _delete(self, subject):
        raise NotImplementedError

    def key(self, subject):
        raise NotImplementedError

    def get_gw_iface(self):
        return self.context.shared_registry.fetch(
            system.GatewayIfaceDescriptor).name


class VLANService(Abstract):
    def allocate_stack(self, stack):
        if not stack:
            raise ValueError('Create request for empty VLAN stack')
        return self._allocate_stack(self._unify_tag_definition(stack))

    def lookup(self, raw):
        return super().lookup(self._unify_tag_definition(raw))

    def key(self, subject):
        return self._unify_tag_definition(subject.tag)

    def _allocate_stack(self, stack):
        if not stack:
            return

        try:
            vlan = self.lookup(stack)
        except exc.ServiceLookupError:
            parent = self._allocate_stack(stack[:-1])
            vlan = model.VLAN(stack, parent=parent)
            vlan = self.create(vlan)
        return vlan

    def _create(self, subject):
        tag_chain = self.key(subject)
        ifname = self.make_iface_name(tag_chain)
        tag = tag_chain[-1]
        if subject.parent:
            link = self.make_iface_name(subject.parent.tag)
        else:
            link = self.get_gw_iface()

        iface = self.make_vlan_iface(ifname, link, tag)
        subject.set_iface(model.NetworkIface(
                ifname, index=iface.index, vlan=subject, vlan_tag=tag))

        return subject

    def _delete(self, subject):
        tag = self.key(subject)
        ifname = self.make_iface_name(tag)
        with self.get_ipdb().interfaces[ifname] as iface:
            iface.remove()

    @staticmethod
    def _unify_tag_definition(raw):
        if isinstance(raw, collections.Sequence):
            vlan_stack = tuple(raw)
        else:
            vlan_stack = (raw,)
        return vlan_stack

    def make_vlan_iface(self, name, link, vlan_id):
        ip = self.get_ipdb()
        with ip.create(
                kind='vlan', ifname=name, vlan_id=vlan_id,
                link=link) as iface:
            iface.up()
        return ip.interfaces[name].ro

    def make_iface_name(self, tag_raw):
        tag = self._unify_tag_definition(tag_raw)
        return 'vlan.{}'.format('.'.join(str(x) for x in tag))


class IpAddressService(Abstract):
    def __init__(self, context):
        super().__init__(context)
        existing_addresses = self._collect_existing_addresses(
            self.get_ipdb(), self.get_gw_iface())
        for entry in existing_addresses:
            self._pool[self.key(entry)] = entry

    def key(self, subject):
        return subject.idnr

    def _create(self, subject):
        self._check_collision(subject)
        if subject.iface is None:
            subject.iface = model.NetworkIface(self.get_gw_iface())

        name = subject.iface.get_ipdb_key()
        with self.get_ipdb().interfaces[name] as iface:
            iface.add_ip(subject.address, mask=subject.prefix)
        return subject

    def _delete(self, subject):
        name = subject.iface.get_ipdb_key()
        with self.get_ipdb().interfaces[name] as iface:
            iface.del_ip(subject.address, mask=subject.prefix)

    def _check_collision(self, subject):
        network = subject.network
        for address in self._pool.values():
            if not address.network.overlaps(network):
                continue

            raise exc.ServiceCreateCollisionError(self, subject, address)

    @staticmethod
    def _collect_existing_addresses(ipdb, iface_ref):
        master_iface = model.NetworkIface(iface_ref)
        results = []
        with ipdb.interfaces[iface_ref].ro as iface:
            for addr, prefix in iface.ipaddr:
                try:
                    results.append(model.IpAddress(
                        addr, prefix=prefix, iface=master_iface))
                except ipaddress.AddressValueError:
                    pass  # ignore not ipv4 addresses
        return results


class EndpointService(Abstract):
    def key(self, subject):
        return subject.idnr

    def get_report(self, key):
        entity = self.lookup(key)

        proc = entity.proc
        if proc.poll() is None:
            return None

        out = []
        for path in (
                self.make_report_file_name(entity),
                self.make_error_file_name(entity)):
            with open(str(path), 'rt') as stream:
                out.append(stream.read())

        if not filter(bool, out):
            return None

        report, error = self.unpack_output(out)
        return report, error

    def _create(self, subject):
        if isinstance(subject, model.ConsumerEndpoint):
            self._create_consumer(subject)
        elif isinstance(subject, model.ProducerEndpoint):
            self._create_producer(subject)
        else:
            raise ValueError('Unsupported payload {!r}'.format(subject))
        return subject

    def _delete(self, subject):
        for file in (
                self.make_report_file_name(subject),
                self.make_error_file_name(subject)):
            try:
                file.unlink()
            except FileNotFoundError:
                pass

        try:
            for attempt in range(3):
                if subject.proc.poll() is not None:
                    break
                subject.proc.terminate()
                time.sleep(1)
            else:
                subject.proc.kill()
        except OSError as e:
            if e.errno != errno.ESRCH:
                raise

        subject.proc.wait()

        if isinstance(subject, model.ConsumerEndpoint):
            subject.bind_address.free_port(subject.bind_port)

    def _create_consumer(self, subject):
        subject.bind_port = subject.bind_address.alloc_port()
        cmd = self.make_cmd_common_part(subject)
        cmd += [
            '--server',
            '--one-off',
            '--bind={}'.format(subject.bind_address.address),
            '--port={}'.format(subject.bind_port)]
        self.run_iperf(subject, cmd)

    def _create_producer(self, subject):
        bandwidth = subject.bandwidth * 1024
        if subject.burst_pkt:
            bandwidth = '{}/{}'.format(bandwidth, subject.burst_pkt)

        cmd = self.make_cmd_common_part(subject)
        cmd += [
            '--client={}'.format(subject.remote_address.address),
            '--port={}'.format(subject.remote_address.port),
            '--bandwidth={}'.format(bandwidth),
            '--time={}'.format(subject.time),
            '--interval=1']
        if subject.use_udp:
            cmd.append('--udp')
        if subject.buffer_length:
            cmd.append('--len={}'.format(subject.buffer_length))
        self.run_iperf(subject, cmd)

    def make_cmd_common_part(self, subject):
        cmd = []
        if self._is_network_namespace_used():
            cmd.extend((
                'ip', 'netns', 'exec',
                self.context.make_network_namespace_name()))
        cmd.extend((
            'iperf3', '--json', '--interval=1'))
        if subject.bind_address is not None:
            cmd.append('--bind={}'.format(subject.bind_address.address))
        return cmd

    def run_iperf(self, subject, cmd):
        report = open(str(self.make_report_file_name(subject)), 'wb')
        err = open(str(self.make_error_file_name(subject)), 'wb')
        proc = subprocess.Popen(cmd, stdout=report, stderr=err)

        subject.set_proc(proc)

    def make_report_file_name(self, subject):
        return self.context.path('{}.json'.format(subject.idnr))

    def make_error_file_name(self, subject):
        return self.context.path('{}.err'.format(subject.idnr))

    def _is_network_namespace_used(self):
        try:
            self.context.shared_registry.fetch(system.FakeNetworkNamespace)
            return False
        except exc.RegistryLookupError:
            return True

    @staticmethod
    def unpack_output(out):
        stdout, stderr = out
        if not stdout:
            return {}, stderr

        try:
            report = json.loads(stdout)
        except (ValueError, TypeError) as e:
            report = {}
            if stderr:
                stderr += '-+' * 30 + '-\n'
            stderr += 'Can\'t decode iperf3 output: {}\n'.format(e)
            stderr += 'Raw iperf3 output stats on next line\n'
            stderr += stdout
        return report, stderr


class Adapter(object):
    def __init__(self, context):
        self.address = IpAddressService(context)
        self.vlan = VLANService(context)
        self.endpoint = EndpointService(context)
