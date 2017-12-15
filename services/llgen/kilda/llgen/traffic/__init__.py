from . import mod_scapy


def module_lookup(name):
    try:
        mod_class = {
            'scapy': mod_scapy.ScapyModule
        }[name]
    except KeyError:
        raise NotFoundError(name) from None

    return mod_class()


class NotFoundError(Exception):
    def __init__(self, name):
        super().__init__('There is no module {!r}.'.format(name), name)

    @property
    def message(self):
        return self.args[0]

    @property
    def name(self):
        return self.args[1]
