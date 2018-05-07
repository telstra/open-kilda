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

import errno
import json
import time
import threading
import subprocess

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
            except Exception as e:
                raise exc.ServiceCreateError(self, subject) from e
            self._pool[self.key(item)] = item

            return item

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
        return self.context.shared_registry.fetch(system.VEthPair).ns


class VLANService(Abstract):
    def key(self, subject):
        return subject.tag

    def _create(self, subject):
        tag = self.key(subject)
        ifname = self.make_iface_name(tag)
        ip = self.get_ipdb()
        with ip.create(
                kind='vlan', ifname=ifname, vlan_id=tag,
                link=self.get_gw_iface()) as iface:
            iface.up()

        iface = ip.interfaces[ifname].ro
        subject.set_iface(model.NetworkIface(
                ifname, index=iface.index, vlan_tag=tag))

        return subject

    def _delete(self, subject):
        tag = self.key(subject)
        ifname = self.make_iface_name(tag)
        with self.get_ipdb().interfaces[ifname] as iface:
            iface.remove()

    @staticmethod
    def make_iface_name(tag):
        return 'vlan.{}'.format(tag)


class IpAddressService(Abstract):
    def key(self, subject):
        return subject.idnr

    def _create(self, subject):
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

        report, error = out
        report = json.loads(report)
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
        cmd = self.make_cmd_common_part(subject)
        cmd += [
            '--client={}'.format(subject.remote_address.address),
            '--port={}'.format(subject.remote_address.port),
            '--bandwidth={}'.format(subject.bandwidth * 1024),
            '--time={}'.format(subject.time),
            '--interval=1']
        if subject.use_udp:
            cmd.append('--udp')
        self.run_iperf(subject, cmd)

    def make_cmd_common_part(self, subject):
        cmd = [
            'ip', 'netns', 'exec', self.context.make_network_namespace_name(),
            'iperf3', '--json', '--interval=1']
        if subject.bind_address is not None:
            cmd.append('--bind={}'.format(subject.bind_address.address))
        return cmd

    def run_iperf(self, subject, cmd):
        report = open(str(self.make_report_file_name(subject)), 'wb')
        err = open(str(self.make_error_file_name(subject)), 'wb')
        proc = subprocess.Popen(cmd, stdout=report, stderr=err)

        subject.set_proc(proc)
        self.context.children.add(proc)

    def make_report_file_name(self, subject):
        return self.context.path('{}.json'.format(subject.idnr))

    def make_error_file_name(self, subject):
        return self.context.path('{}.err'.format(subject.idnr))


class Adapter(object):
    def __init__(self, context):
        self.address = IpAddressService(context)
        self.vlan = VLANService(context)
        self.endpoint = EndpointService(context)
