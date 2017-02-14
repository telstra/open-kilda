import json
from jsonschema import validate


class MessageItem(object):
    def __init__(self, j):
        self.__dict__ = json.loads(j)
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=True, indent=4)

