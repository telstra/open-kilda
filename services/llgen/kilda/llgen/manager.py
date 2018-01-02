import errno
import json
import logging.config
import os
import socket
import sys

from kilda.llgen import context as llgen_context
from kilda.llgen import daemon as daemon_module
from kilda.llgen import protocol
from kilda.llgen import traffic
from kilda.llgen import utils


class Manager(daemon_module.AbstractChild):
    BACKLOG = 128
    TICK_INTERVAL = 1.0
    time_to_live = None

    def __init__(self, ctx, daemon):
        super().__init__(daemon)

        self.ctx = ctx
        self.proc_state = utils.WorkerState()

        self._modules = {}

        self.daemon.close_std_fds()

        self._setup_logging()
        self.log = logging.getLogger(llgen_context.logging_name('daemon'))

        self.sock = self._make_socket()

    def __call__(self):
        self.sock.listen(self.BACKLOG)
        self.sock.settimeout(0.5)

        self.log.debug('main loop')
        utils.TermSignal(self.proc_state)

        interval = utils.TimeDelayTracker(self.TICK_INTERVAL)
        try:
            while not self.proc_state.read_term():
                self.log.debug('iteration')
                if self.time_to_live is not None:
                    if self.time_to_live():
                        self.log.info('TTL is over, terminating')
                        break

                try:
                    conn, address = self.sock.accept()
                except socket.timeout:
                    continue

                self._handle(conn)

                if interval():
                    self._timer_event()

            self.log.debug('teardown')
            for mod in self._modules.values():
                mod.term_request()
        finally:
            self._drop_socket()

        self.log.debug('Work is over, notify all modules about termination')

        sys.exit()

    def _setup_logging(self):
        if self.ctx.logging_config:
            try:
                logging.config.fileConfig(self.ctx.logging_config)
            except (IOError, OSError) as e:
                raise self.InvalidLoggingConfigException(e)

        else:
            logging.basicConfig(
                level=logging.INFO,
                format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

        logging.getLogger().setLevel(logging.DEBUG)

    def _make_socket(self):
        sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)

        path = str(self.ctx.socket_path)

        umask = os.umask(0o017)
        try:
            try:
                os.unlink(path)
            except OSError as e:
                if e.errno != errno.ENOENT:
                    raise

            sock.bind(path)
        finally:
            os.umask(umask)

        return sock

    def _drop_socket(self):
        try:
            self.ctx.socket_path.unlink()
        except OSError as e:
            if e.errno != errno.ENOENT:
                raise

    def _handle(self, conn):
        request = JsonRequest(conn)

        item_results = []
        response = protocol.OutputMessage(item_results)
        try:
            data = protocol.InputMessage.unpack(request.message)

            if data.time_to_live is not None:
                self.time_to_live = utils.TimeDelayTracker(data.time_to_live)

            module = self._get_module(data.module)

            item_results[:] = [protocol.UnhandledItemResult()] * len(data.items)
            for idx in range(len(item_results)):
                item_results[idx] = module(data.items[idx])
        except protocol.DataValidationError as e:
            response = protocol.OutputErrorMessage(
                'Can\'t unpack/validate input request: {}'.format(e.message))
        except traffic.NotFoundError as e:
            response = protocol.OutputErrorMessage(e.message)
        except Exception:
            error_message = 'Internal error'
            self.log.error(error_message, exc_info=True)
            response = protocol.OutputErrorMessage(error_message)
        finally:
            request.reply(response.pack())

    def _timer_event(self):
        for mod in self._modules.values():
            mod.timer_housekeeping()

    def _get_module(self, name):
        try:
            module = self._modules[name]
        except KeyError:
            self._modules[name] = module = traffic.module_lookup(name)
        return module

    class InvalidLoggingConfigException(Exception):
        def __init__(self, cause: (IOError, OSError)):
            super().__init__(
                    'Unable to use "{}" as logging configuration: {}'.format(
                            cause.filename, cause.strerror))


class JsonRequest(protocol.JsonIO):
    def __init__(self, conn):
        super().__init__(conn)
        self.message = json.loads(self._recv())

    def reply(self, payload):
        self._send(payload)
