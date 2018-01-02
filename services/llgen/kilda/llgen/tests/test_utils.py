#
from kilda.llgen import utils


class TestUtils(object):
    def test_worker_state(self):
        state = utils.WorkerState()

        assert state.read_term() is False
        assert state.read_tick() is False

        state.register_tick()
        assert state.read_tick() is True
        assert state.read_tick() is False
        assert state.read_tick() is False

        state.register_tick()
        assert state.read_tick() is True
        assert state.read_tick() is False
        assert state.read_tick() is False

        state.register_term()
        assert state.read_term() is True
