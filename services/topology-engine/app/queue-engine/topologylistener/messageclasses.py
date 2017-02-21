#!/usr/bin/python
import json
from jsonschema import validate


class MessageItem(object):
    def __init__(self, type, timestamp, data):
        self.type = type
        self.timestamp = timestamp
        self.data = data

    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=True, indent=4)
