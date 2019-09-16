import collections
import shutil
import tempfile
import itertools
import unittest
import unittest.mock

from kilda.traffexam import model
from kilda.traffexam import context as context_module
from kilda.traffexam import service as service_module
from kilda.traffexam import system

IpIfaceStub = collections.namedtuple('IpIfaceStub', ('index', 'name'))


class ContextStub(context_module.Context):
    root = tempfile.mktemp()


class TestVlanService(unittest.TestCase):
    ns_gw_iface = system.GWDescriptor('external', 'gw')
    service = None
    interfaces = {}

    def setUp(self) -> None:
        ipdb = unittest.mock.Mock()
        ipdb.interfaces = self.interfaces

        self.interfaces.clear()

        iface_index_factory = itertools.count()

        def create_iface_stub(ifname=None, **kwargs):
            iface = unittest.mock.MagicMock()
            iface.ro = IpIfaceStub(next(iface_index_factory), ifname)
            ipdb.interfaces[ifname] = iface
            iface.__enter__.return_value = iface
            return iface
        ipdb.create.side_effect = create_iface_stub

        context = ContextStub('lo0', '127.0.0.1:8080')
        context.shared_registry.add(system.VEthPair, self.ns_gw_iface)
        context.shared_registry.add(system.NetworkNamespace, ipdb)

        self.service = service_module.VLANService(context)

    def tearDown(self) -> None:
        if self.service is not None:
            context = self.service.context
            try:
                shutil.rmtree(str(context.root))
            except (OSError, IOError):
                # do not fail on cleanup errors
                pass

    def test_create(self):
        # create VLAN tag 1 iface
        target_vlan = model.VLAN(1)
        self.service.create(target_vlan)
        self.verify_vlan_create(target_vlan, 1, 'vlan.1')

    def test_create_clean_stack(self):
        last = self.service.allocate_stack((1, 2))
        first = last.parent

        self.assertIsNotNone(first)
        self.assertIsNone(first.parent)
        self.verify_vlan_create(first, 1, 'vlan.1')
        self.verify_vlan_create(
            last, 2, 'vlan.1.2', parent_iface_name='vlan.1')

    def test_create_partial_stack(self):
        ipdb = self.service.get_ipdb()

        first = model.VLAN(2)
        self.service.create(first)
        self.assertEqual(1, ipdb.create.call_count)
        first_ip_iface = self.interfaces[first.iface.name]

        second = self.service.allocate_stack((2, 3))

        self.assertEqual(2, ipdb.create.call_count)
        # first interface must not be recreated
        self.assertIs(first_ip_iface, self.interfaces[first.iface.name])

        self.verify_vlan_create(
            second, 3, 'vlan.2.3', parent_iface_name='vlan.2')

    def verify_vlan_create(self, vlan, tag, iface_name, parent_iface_name=None):
        if parent_iface_name is None:
            parent_iface_name = self.ns_gw_iface.ns

        ipdb = self.service.get_ipdb()
        self.assertIn(unittest.mock.call(
            kind='vlan', ifname=iface_name, vlan_id=tag,
            link=parent_iface_name), ipdb.create.mock_calls)
        ip_iface = ipdb.interfaces[iface_name]
        ip_iface.up.assert_called_once_with()

        app_iface = ipdb.interfaces[iface_name].ro

        self.assertEqual(ipdb.interfaces[iface_name].ro.index,
                         app_iface.index)
        self.assertEqual(iface_name, vlan.iface.name)
        self.assertEqual(tag, vlan.iface.vlan_tag)
