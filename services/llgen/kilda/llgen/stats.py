#
import collections
import json

from kilda.llgen import utils


class Descriptor(collections.Iterator):
    _iteration = 0

    def __init__(self, size, offset=None, count=None):
        self.size = size
        if offset is None:
            offset = 0
        self.offset = offset

        if self.size <= self.offset:
            raise ValueError(
                    'size({}) < offset({})'.format(self.size, self.offset))

        self.count = count
        if self.count is not None:
            if self.size < self.count:
                raise ValueError(
                        'size({}) < count({})'.format(self.size, self.count))

    def __next__(self):
        if self.count is not None and self.count <= self._iteration:
            raise StopIteration
        self._iteration += 1

        value = self.offset

        self.offset += 1
        if self.offset == self.size:
            self.offset = 0

        return value

    @classmethod
    def unpack(cls, packed):
        args = (packed.pop('size'), )
        return cls(*args, **packed)

    def pack(self):
        result = vars(self).copy()
        result['count'] = min(result.pop('_iteration', 0), self.size)

        offset = self._iteration - result['count']
        if offset < 0:
            offset += self.size
        result['offset'] = offset
        return result


class _RWBase(object):
    _index_name = '_index.json'

    def __init__(self, root, limit):
        self.root = root
        self.limit = limit

        limit_str_len = len(str(limit))
        self._item_name_format = '{' + ':0{}d'.format(limit_str_len) + '}.json'

    def item_name(self, idx):
        return self._item_name_format.format(idx)


class StatsWriter(_RWBase):
    file_mode = 0o644

    def __init__(self, root, limit, offset=None):
        super().__init__(root, limit)
        self.descriptor = Descriptor(self.limit, offset=offset)
        self._update_index()

    @classmethod
    def resume_or_new(cls, root, limit):
        offset = None
        try:
            reader = StatsReader(root)
            offset = reader.descriptor.offset
        except (OSError, json.JSONDecodeError):
            pass

        return cls(root, limit, offset)

    def write(self, payload):
        name = self.item_name(next(self.descriptor))
        with utils.FileAtomicPut(
                self.root.joinpath(name), mode=self.file_mode,
                text_io=True) as stream:
            json.dump(payload, stream)
        self._update_index()

    def _update_index(self):
        data = self.descriptor.pack()
        name = self.root.joinpath(self._index_name)
        with utils.FileAtomicPut(
                name, mode=self.file_mode, text_io=True) as stream:
            json.dump(data, stream)


class StatsReader(_RWBase):
    def __init__(self, root):
        name = root.joinpath(self._index_name)
        with open(str(name), 'rt') as stream:
            data = json.load(stream)
        descriptor = Descriptor.unpack(data)

        super().__init__(root, descriptor.size)

        self.descriptor = descriptor

    def read(self):
        try:
            idx = next(self.descriptor)
        except StopIteration:
            return None

        name = self.item_name(idx)
        with self.root.joinpath(name).open(mode='rt') as stream:
            data = json.load(stream)

        return data


class Record(object):
    packet_count = 0
    byte_count = 0

    def add_packets(self, count):
        self.packet_count += count

    def add_bytes(self, count):
        self.byte_count += count

    def pack(self):
        return vars(self).copy()

    @classmethod
    def unpack(cls, data):
        obj = cls()
        vars(obj).update(data)

        return obj
