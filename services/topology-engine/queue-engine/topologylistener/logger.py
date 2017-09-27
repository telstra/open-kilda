import sys
import logging


__all__ = ['get_logger']


FORMATTER = '%(asctime)23s | %(name)s [%(threadName)s] | %(levelname)-5s  | '\
            '%(filename)s:%(lineno)d [%(funcName)s] | %(message)s'

root_logger = logging.getLogger("queue-engine")
root_logger.setLevel(logging.DEBUG)

ch = logging.StreamHandler(sys.stdout)
ch.setLevel(logging.INFO)

formatter = logging.Formatter(FORMATTER)
ch.setFormatter(formatter)

root_logger.addHandler(ch)


def get_logger():
    return root_logger
