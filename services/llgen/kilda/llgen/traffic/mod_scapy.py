#
import collections
import logging
import pathlib
import multiprocessing

import scapy.all

from kilda.llgen import stats as llgen_stats
from kilda.llgen import protocol
from kilda.llgen.traffic import abstract

logging_name = abstract.logging_name('scapy.{}').format


class ScapyModule(abstract.AbstractModule):
    PREFIX_PRODUCER = 'producer'
    PREFIX_CONSUMER = 'consumer'

    def __init__(self):
        super().__init__()
        self.log = logging.getLogger(abstract.logging_name('').rstrip('.'))
        self.root = pathlib.Path('scapy')

        self.root.mkdir(mode=0o755, exist_ok=True)
        for tail in (self.PREFIX_PRODUCER, self.PREFIX_CONSUMER):
            self.root.joinpath(tail).mkdir(mode=0o755, exist_ok=True)

    def producer(self, item):
        path = self.root.joinpath(self.PREFIX_PRODUCER, item.name)
        worker = ProducerWorker(path, item)
        return self._setup_worker(path, worker)

    def consumer(self, item):
        path = self.root.joinpath(self.PREFIX_CONSUMER, item.name)
        worker = ConsumerWorker(path, item)
        return self._setup_worker(path, worker)

    def list(self, item):
        items = collections.defaultdict(list)
        for key in self.children:
            kind = key[0]
            value = pathlib.PurePath(*key[1:])
            value = str(value)
            items[kind] = value

        return protocol.ListItemResult(
            items[self.PREFIX_CONSUMER], items[self.PREFIX_PRODUCER])

    def drop(self, item):
        match = set()
        for key in self.children:
            p = pathlib.PurePath(*key)
            if not p.match(item.filter):
                continue
            match.add(key)

        dropped = set()
        for key in match:
            child = self.children[key]
            try:
                self._terminate(child)
                dropped.add(key)

                del self.children[key]
            except Exception:
                self.log.error(
                    'Unable to terminate child process ({})'.format(child.pid),
                    exc_info=True)

        failed = match - dropped
        return protocol.DropItemResult(dropped, failed)

    def stats(self, item):
        items = {}
        for key in self.children:
            path = pathlib.Path(*key)
            items[str(path)] = self._load_stats(path)

        return protocol.StatsItemResult(items)

    def ttl_renew(self, item):
        raise NotImplementedError

    def _setup_worker(self, path, worker):
        child_key = path.parts

        child = multiprocessing.Process(target=worker)
        action = protocol.ACTION_ADD
        try:
            current = self.children[child_key]
        except KeyError:
            pass
        else:
            self._terminate(current)
            action = protocol.ItemResult.ACTION_UPDATE

        self.children[child_key] = child

        child.start()

        return protocol.ItemResult(action=action)

    @staticmethod
    def _load_stats(path):
        reader = llgen_stats.StatsReader(path)
        return [x for x in iter(reader.read, None)]


class AbstractWorker(abstract.Worker):
    def __init__(self, root, item):
        super().__init__()
        self.log = logging.getLogger(logging_name(item.name))

        self.iface = item.iface

        root.mkdir(mode=0o755, exist_ok=True)

        # FIXME(surabujin): move 1000 into item field
        self.stats_writer = llgen_stats.StatsWriter.resume_or_new(root, 1000)
        self.stats_record = llgen_stats.Record()

    def timer_event_handler(self):
        self.log.debug('tick')
        stats = self.stats_record
        self.stats_record = llgen_stats.Record()

        self.stats_writer.write(stats.pack())


class ProducerWorker(AbstractWorker):
    batch_size = 64

    def __init__(self, root, item):
        super().__init__(root, item)

        packet = self._osi_l7(item)
        packet = self._osi_l4(packet, item)
        packet = self._osi_l3(packet, item)
        packet = self._osi_l2(packet, item)

        self.packet = packet

    def main_loop(self, time_frame):
        args = {
            'verbose': False,
            'count': self.batch_size
        }

        if self.iface is None:
            scapy.all.send(self.packet, **args)
        else:
            scapy.all.sendp(self.packet, iface=self.iface, **args)
        self.stats_record.add_packets(self.batch_size)

    def _osi_l7(self, item):
        return item.payload.encode('utf-8')

    def _osi_l4(self, packet, item):
        return scapy.all.ICMP() / packet

    def _osi_l3(self, packet, item):
        args = {
            'src': item.ip4.source.address,
            'dst': item.ip4.dest.address,
        }
        args = {k: v for k, v in args.items() if v is not None}
        if args:
            packet = scapy.all.IP(**args) / packet

        if item.vlan:
            packet = scapy.all.Dot1Q(vlan=item.vlan) / packet

        return packet

    def _osi_l2(self, packet, item):
        args = {
            'src': item.ethernet.source.address,
            'dst': item.ethernet.dest.address,
        }
        args = {k: v for k, v in args.items() if v is not None}
        if args:
            packet = scapy.all.Ether(**args) / packet

        return packet


class ConsumerWorker(AbstractWorker):
    def main_loop(self, time_frame):
        batch = scapy.all.sniff(timeout=time_frame, iface=self.iface)
        count = sum(1 for x in batch if x.haslayer(scapy.all.ICMP))
        self.stats_record.add_packets(count)
