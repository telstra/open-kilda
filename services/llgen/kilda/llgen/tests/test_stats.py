#
import itertools
import pathlib

import pytest

from kilda.llgen import stats as llgen_stats


class TestStats(object):
    def test_descriptor_offset(self):
        data = [
            ((3, None, None), 7, [0, 1, 2, 0, 1, 2, 0]),
            ((3, 0, None), 7, [0, 1, 2, 0, 1, 2, 0]),
            ((3, 1, None), 7, [1, 2, 0, 1, 2, 0, 1]),
            ((3, 2, None), 7, [2, 0, 1, 2, 0, 1, 2]),
        ]

        for opts, limit, expect in data:
            descriptor = llgen_stats.Descriptor(*opts)
            actual = list(itertools.islice(descriptor, limit))
            assert actual == expect

    def test_descriptor_offset_and_limit(self):
        data = [
            ((5, None, 5), [0, 1, 2, 3, 4]),
            ((5, 2, 5), [2, 3, 4, 0, 1])
        ]
        for opts, expect in data:
            descriptor = llgen_stats.Descriptor(*opts)
            actual = list(descriptor)
            assert actual == expect

    def test_descriptor_constraints(self):
        data = [
            (5, None, 6),
            (5, 6, None),
            (5, 6, 6)]
        for opts in data:
            with pytest.raises(ValueError):
                llgen_stats.Descriptor(*opts)

    def test_pack_unpack(self):
        write_descriptor = llgen_stats.Descriptor(10)
        expected = [x[0] for x in zip(range(5), write_descriptor)]

        transition = write_descriptor.pack()
        read_descriptor = llgen_stats.Descriptor.unpack(transition)

        assert expected == list(read_descriptor)

    def test_write_read(self, tmpdir):
        payload = [
            {'data': 0},
            {'data': 1},
            {'data': 2},
            {'data': 3},
            {'data': 4},
            {'data': 5},
            {'data': 6},
            {'data': 7},
            {'data': 8},
            {'data': 9},
            {'data': 0xA},
            {'data': 0xB},
            {'data': 0xC},
            {'data': 0xD}
        ]

        root = pathlib.Path(str(tmpdir.join('write-read')))
        root.mkdir(mode=0o755, exist_ok=True)

        writer = llgen_stats.StatsWriter(root, 10)
        for chunk in payload:
            writer.write(chunk)

        reader = llgen_stats.StatsReader(root)
        actual = [x for x in iter(reader.read, None)]

        assert actual == payload[-10:]
