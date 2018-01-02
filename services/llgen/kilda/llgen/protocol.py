#

import collections
import json
import socket
import inspect


class JsonIO(object):
    CHUNK_SIZE = 2048

    def __init__(self, conn):
        self.conn = conn

    def _send(self, payload):
        message = json.dumps(payload)
        message = message.encode('utf-8')
        self.conn.sendall(message)
        self.conn.shutdown(socket.SHUT_WR)

    def _recv(self):
        chunks = [b'dummy']
        while chunks[-1]:
            chunks.append(self.conn.recv(self.CHUNK_SIZE))
        chunks.pop(0)

        self.conn.shutdown(socket.SHUT_RD)

        raw = b''.join(chunks)
        return raw.decode('utf-8')


class Abstract(object):
    def __init__(self, data):
        self._raw = data

    def __ne__(self, other):
        return not self.__eq__(other)

    def __eq__(self, other):
        if not isinstance(other, Abstract):
            return NotImplemented
        return self.pack() == other.pack()

    @staticmethod
    def enforce_type(value, type_):
        if not isinstance(value, type_):
            raise ValueError('Invalid node type - {!r}, expect "{!r}"'.format(
                type(value).__name__, type_))

    @classmethod
    def unpack(cls, raw):
        try:
            return cls(raw)
        except (ValueError, TypeError) as e:
            raise DataValidationError('Invalid field value: {}'.format(e))
        except KeyError as e:
            raise DataValidationError('Field {!r} is missing'.format(e.args[0]))

    def pack(self):
        result = {}

        for attr in dir(self):
            if attr.startswith('_'):
                continue

            value = getattr(self, attr)
            if inspect.ismethod(value) or inspect.isfunction(value):
                continue

            if isinstance(value, Abstract):
                value = self._pack_item(value)
            elif isinstance(value, str):
                pass
            elif isinstance(value, collections.Sequence):
                value = [self._pack_item(x) for x in value]

            result[attr] = value

        return result

    @staticmethod
    def _pack_item(item):
        if isinstance(item, Abstract):
            item = item.pack()
        return item


class AbstractAddress(Abstract):
    address = None
    is_defined = False


class EthernetAddress(AbstractAddress):
    def __init__(self, data):
        super().__init__(data)
        if data is None:
            return

        self.address = data
        self.enforce_type(self.address, str)
        self.is_defined = True


class IPv4Address(AbstractAddress):
    def __init__(self, data):
        super().__init__(data)
        if data is None:
            return

        self.address = data
        self.enforce_type(self.address, str)
        self.is_defined = True


class AbstractNetLayer(Abstract):
    source = dest = None

    def __init__(self, data):
        super().__init__(data)
        if data is not None:
            self.enforce_type(data, collections.Mapping)
            source = data.get('source')
            dest = data.get('dest')
        else:
            source = dest = None

        self.source = self._get_source(source)
        self.dest = self._get_dest(dest)

    def _get_source(self, data):
        raise NotImplementedError

    def _get_dest(self, data):
        raise NotImplementedError


class EthernetLayer(AbstractNetLayer):
    source = dest = EthernetAddress(None)

    def _get_source(self, data):
        return EthernetAddress(data)

    def _get_dest(self, data):
        return EthernetAddress(data)


class IP4Layer(AbstractNetLayer):
    source = dest = IPv4Address(None)

    def _get_source(self, data):
        return IPv4Address(data)

    def _get_dest(self, data):
        return IPv4Address(data)


class AbstractItem(Abstract):
    def __init__(self, data):
        super().__init__(data)

        self.kind = data['kind']


class ProducerItem(AbstractItem):
    vlan = None

    def __init__(self, data):
        super().__init__(data)

        self.name = data['name']
        self.iface = data.get('iface')
        if self.iface is not None:
            self.enforce_type(self.iface, str)

        self.payload = data.get('payload', '')
        self.enforce_type(self.payload, str)

        self.ip4 = IP4Layer(data.get('ip4'))

        try:
            self.vlan = data['vlan']
            self.enforce_type(self.vlan, int)
        except KeyError:
            pass

        self.ethernet = EthernetLayer(data.get('ether'))


class ConsumerItem(ProducerItem):
    def __init__(self, data):
        super().__init__(data)

        self.stats_period = float(data.get('stats_period', 1.0))


class DropItem(AbstractItem):
    def __init__(self, data):
        super().__init__(data)
        self.filter = data['filter']


class ListItem(AbstractItem):
    pass


class StatsItem(AbstractItem):
    pass


class TimeToLiveItem(AbstractItem):
    def __init__(self, data):
        super().__init__(data)
        self.stop_in_seconds = data['stop_in_seconds']


class ItemDescriptor(AbstractItem):
    def __call__(self):
        try:
            class_ = {
                'producer': ProducerItem,
                'consumer': ConsumerItem,
                'drop': DropItem,
                'list': ListItem,
                'stats': StatsItem,
                'time-to-live': TimeToLiveItem
            }[self.kind]
        except KeyError:
            raise ValueError('Unsupported item kind: {!r}'.format(self.kind))

        return class_(self._raw)


class InputMessage(Abstract):
    module = 'scapy'
    time_to_live = None

    def __init__(self, data):
        super().__init__(data)

        self.module = data['module']
        self.time_to_live = data.get('time_to_live')
        if self.time_to_live is not None:
            self.time_to_live = float(self.time_to_live)
        self.items = [self._unpack_item(x) for x in data['items']]

    @staticmethod
    def _unpack_item(item):
        descriptor = ItemDescriptor(item)
        return descriptor()


class OutputMessage(Abstract):
    def __init__(self, item_results):
        super().__init__(None)

        success = True
        for item in item_results:
            if not isinstance(item, ItemErrorResult):
                continue
            success = False
            break

        self.success = success
        self.items = item_results


class OutputErrorMessage(OutputMessage):
    def __init__(self, error):
        super().__init__([])

        self.success = False
        self.error_message = error


class AbstractItemResult(Abstract):
    success = True


class ItemErrorResult(AbstractItemResult):
    success = False

    def __init__(self, message):
        super().__init__(None)

        self.error_message = message


class UnhandledItemResult(ItemErrorResult):
    def __init__(self):
        super().__init__('No handling attempt was applied.')


ACTION_ADD = 'add'
ACTION_UPDATE = 'update'


class ItemResult(AbstractItemResult):
    action = ACTION_ADD

    def __init__(self, **data):
        super().__init__(None)

        vars(self).update(data)


class ListItemResult(AbstractItemResult):
    def __init__(self, consumers, producers):
        super().__init__(None)

        self.consumers = consumers
        self.producers = producers


class DropItemResult(AbstractItemResult):
    def __init__(self, dropped, failed):
        super().__init__(None)

        self.success = bool(failed)
        self.dropped = list(dropped)
        self.failed = list(failed)


class StatsItemResult(AbstractItemResult):
    def __init__(self, items):
        super().__init__(None)

        self.items = items


class DataValidationError(Exception):
    @property
    def message(self):
        return self.args[0]
