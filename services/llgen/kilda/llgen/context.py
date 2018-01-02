import pathlib

logging_name = 'kilda.llgen.{}'.format


class Context(object):
    debug = False
    logging_config = None

    def __init__(self, root, name):
        self.root = pathlib.Path(root)

        if not name or name.startswith('.'):
            raise ValueError('Invalid environment name: {!r}'.format(name))
        self.env = name

        self.socket_path = pathlib.Path('{}.sock'.format(self.env))

    def path(self, *args):
        return str(self.root.joinpath(*args))

    def set_logging_config(self, config):
        self.logging_config = config
        return self

    def set_debug(self, debug):
        self.debug = debug
        return self
