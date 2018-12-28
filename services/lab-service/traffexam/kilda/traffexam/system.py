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

import pyroute2

from kilda.traffexam import context as context_module
from kilda.traffexam import exc


class _IpMixin(context_module.ContextConsumer):
    def get_ipdb(self):
        raise NotImplementedError


class RootIPDBMixin(_IpMixin):
    def get_ipdb(self):
        return self.context.shared_registry.fetch(IPDBRoot)


class NSIPDBMixin(_IpMixin):
    def get_ipdb(self):
        return self.context.shared_registry.fetch(NetworkNamespace)


class Abstract(context_module.ContextConsumer):
    def __init__(self, context):
        super().__init__(context)

        try:
            self.create()
            self.configure()
        except exc.SystemResourceExistsError:
            try:
                self.configure()
            except exc.SystemResourceError:
                self.release_acquire()
                self.configure()

    def create(self):
        raise NotImplementedError

    def configure(self):
        pass

    def release(self):
        raise NotImplementedError

    def release_acquire(self):
        try:
            self.release()
        except exc.SystemResourceError:
            pass
        self.create()


class _IfaceManager(_IpMixin):
    def iface_drop(self, name):
        klass = type(self)
        try:
            with self.get_ipdb().interfaces[name] as iface:
                iface.remove()
        except OSError as e:
            raise exc.SystemResourceError(klass, name) from e
        except KeyError:
            raise exc.SystemResourceNotFoundError(klass, name)

    def iface_get_info(self, name):
        try:
            iface = self.get_ipdb().interfaces[name].ro
        except KeyError:
            raise exc.SystemResourceNotFoundError(type(self), name)
        return iface

    def iface_set_up(self, name):
        klass = type(self)
        try:
            with self.get_ipdb().interfaces[name] as iface:
                iface.up()
        except OSError as e:
            raise exc.SystemResourceError(klass, name) from e
        except KeyError:
            raise exc.SystemResourceNotFoundError(klass, name)

    def iface_drop_all_addresses(self, name):
        klass = type(self)
        try:
            with self.get_ipdb().interfaces[name] as iface:
                for addr in tuple(iface.ipaddr):
                    iface.del_ip(*addr)
        except OSError as e:
            raise exc.SystemResourceError(klass, name) from e
        except KeyError:
            raise exc.SystemResourceNotFoundError(
                    klass, self.context.iface)


class _IPDBAllocator(Abstract):
    def release(self):
        try:
            ip = self.context.shared_registry.fetch(type(self), remove=True)
        except exc.RegistryLookupError:
            pass
        else:
            ip.release()


class IPDBRoot(_IPDBAllocator):
    def create(self):
        instance = pyroute2.IPDB()
        self.context.shared_registry.add(type(self), instance)


class NetworkNamespace(_IPDBAllocator):
    def create(self):
        name = self.context.make_network_namespace_name()
        klass = type(self)
        try:
            pyroute2.netns.create(name)
        except OSError as e:
            if e.errno == errno.EEXIST:
                raise exc.SystemResourceExistsError(klass, name)
            raise exc.SystemResourceError(klass, name) from e

    def release(self):
        super().release()

        name = self.context.make_network_namespace_name()
        try:
            pyroute2.netns.remove(name)
        except OSError as e:
            raise exc.SystemResourceError(type(self), name) from e

    def configure(self):
        name = self.context.make_network_namespace_name()
        nsip = pyroute2.IPDB(nl=pyroute2.NetNS(name))
        self.context.shared_registry.add(type(self), nsip)


GWDescriptor = collections.namedtuple('GWDescription', ('root', 'ns'))


class VEthPair(RootIPDBMixin, _IfaceManager, Abstract):
    ns_gw_name = 'gw'

    def create(self):
        name = self.make_name()
        left, right = name
        klass = type(self)

        try:
            with self.get_ipdb().create(kind='veth', ifname=left, peer=right):
                # don't do any extra configuration here
                pass
        except pyroute2.CreateException:
            raise exc.SystemResourceExistsError(klass, name)
        except Exception as e:
            raise exc.SystemResourceError(klass, name) from e

    def release(self):
        name = self.make_name()
        left, right = name
        self.iface_drop(left)

    def configure(self):
        ns_name = self.context.make_network_namespace_name()
        name = self.make_name()
        left, right = name
        ip = self.get_ipdb()
        klass = type(self)

        self.iface_set_up(left)

        try:
            with ip.interfaces[right] as iface:
                iface.net_ns_fd = ns_name
        except OSError as e:
            raise exc.SystemResourceError(klass, name) from e
        except KeyError:
            # perhaps someone already move iface into our NS
            pass

        nsip = self.context.shared_registry.fetch(NetworkNamespace)
        try:
            with nsip.interfaces[right] as iface:
                iface.ifname = self.ns_gw_name
                iface.up()
        except OSError as e:
            raise exc.SystemResourceError(klass, name) from e
        except KeyError:
            # perhaps someone already rename iface, check it
            try:
                with ip.interfaces[left].ro as iface_left:
                    with nsip.interfaces[self.ns_gw_name].ro as iface_right:
                        ok = iface_left['kind'] == iface_right['kind'] == 'veth'
                        ok = ok and iface_left['peer'] == right

                        if not ok:
                            raise exc.SystemResourceDamagedError(
                                    klass, name,
                                    'is not a veth pair created in previous '
                                    'step')
            except KeyError as e:
                raise exc.SystemResourceNotFoundError(klass, str(e))

        self.context.shared_registry.add(
                klass, GWDescriptor(left, self.ns_gw_name))

    def make_name(self):
        base = self.context.make_veth_base_name()
        return ('.'.join((base, tail)) for tail in 'AB')


class BridgeToTarget(RootIPDBMixin, _IfaceManager, Abstract):
    def create(self):
        name = self.context.make_bridge_name()
        try:
            with self.get_ipdb().create(kind='bridge', ifname=name):
                pass
        except pyroute2.CreateException:
            raise exc.SystemResourceExistsError(type(self), name)

    def release(self):
        self.iface_drop(self.context.make_bridge_name())

    def configure(self):
        name = self.context.make_bridge_name()
        veth = self.context.shared_registry.fetch(VEthPair)

        need_ports = {
            self.context.iface.index,
            self.iface_get_info(veth.root).index}

        klass = type(self)
        try:
            with self.get_ipdb().interfaces[name] as iface:
                iface.up()
                ports = set(iface.ports)
                for port in ports - need_ports:
                    iface.del_port(port)
                for port in need_ports - ports:
                    iface.add_port(port)
        except OSError as e:
            raise exc.SystemResourceError(klass, name) from e
        except KeyError:
            raise exc.SystemResourceDamagedError(
                    klass, name, 'interface not found')


class _ConfigureMixin(Abstract):
    def create(self):
        pass

    def release(self):
        pass


class TargetIfaceCleanUp(RootIPDBMixin, _IfaceManager, _ConfigureMixin):
    def configure(self):
        self.iface_drop_all_addresses(self.context.iface.index)


class JoinTargetBridge(RootIPDBMixin, _IfaceManager, _ConfigureMixin):
    def configure(self):
        veth = self.context.shared_registry.fetch(VEthPair)
        veth_index = self.iface_get_info(veth.root).index

        try:
            with self.get_ipdb().interfaces[self.context.iface.index] as iface:
                iface.add_port(veth_index)
        except OSError as e:
            raise exc.SystemResourceError(type(self), veth.root) from e
        except KeyError:
            raise exc.SystemResourceDamagedError(
                    type(self), self.context.iface.name, 'interface not found')


class TargetIfaceSetUp(RootIPDBMixin, _IfaceManager, _ConfigureMixin):
    def configure(self):
        self.iface_set_up(self.context.iface.index)


class OwnedNetworksCleanUp(RootIPDBMixin, _IfaceManager, _ConfigureMixin):
    def configure(self):
        self.iface_drop_all_addresses(self.context.make_bridge_name())

        veth = self.context.shared_registry.fetch(VEthPair)
        self.iface_drop_all_addresses(veth.root)


class NSNetworksCleanUp(NSIPDBMixin, _IfaceManager, _ConfigureMixin):
    def configure(self):
        veth = self.context.shared_registry.fetch(VEthPair)

        self.cleanup_gateway_iface(veth)
        self.drop_all_ifaces(veth)

        self.iface_set_up('lo')

    def cleanup_gateway_iface(self, veth):
        self.iface_drop_all_addresses(veth.ns)

    def drop_all_ifaces(self, veth):
        keep_iface = {
            self.iface_get_info(veth.ns).index,
            self.iface_get_info('lo').index}
        keep_iface_kinds = {None, 'sit'}

        for name in tuple(self.get_ipdb().interfaces):
            if not isinstance(name, int):
                continue
            if name in keep_iface:
                continue
            if self.iface_get_info(name).kind in keep_iface_kinds:
                continue

            try:
                self.iface_drop(name)
            except pyroute2.NetlinkError as e:
                if e.code != errno.EOPNOTSUPP:
                    raise


class NSGatewaySetUp(NSIPDBMixin, _IfaceManager, _ConfigureMixin):
    def configure(self):
        self.iface_set_up(self.context.shared_registry.fetch(VEthPair).ns)


class RootIfaceInfo(RootIPDBMixin, _IfaceManager):
    def __call__(self, name):
        return self.iface_get_info(name)
