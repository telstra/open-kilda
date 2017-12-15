import socket

from . import protocol


class Client(object):
    def __init__(self, ctx):
        self.ctx = ctx

    def __call__(self, payload):
        return Request(self.ctx, payload).message


class Request(protocol.JsonIO):
    def __init__(self, ctx, payload):
        conn = self._connect(ctx)
        super().__init__(conn)

        self._send(payload)
        self.message = self._recv()

    @staticmethod
    def _connect(ctx):
        path = ctx.path(ctx.socket_path)

        sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        sock.connect(path)

        return sock
