# llgen - traffic genration tool

llgen it a tool desinged to use in our tests to generate/consume/account traffic. 

It is represented by daemon with CLI interface to start/stop/configure.

Usage example:
Create traffic generator and traffic consume
```bash
llgen --debug --logging-config="llgen-log.ini" --root="/tmp/llgen-test" first setup << 'PAYLOAD'
{
  "module": "scapy",
  "time_to_live": 30,
  "items": [
    {
      "kind": "producer",
      "name": "qwe",
      "iface": "lo",
      "payload": "bytes",
      "ip4": {
        "dest": "127.0.0.1"
      },
      "vlan": 123,
      "ether": {
        "source": "00:11:22:33:44:55",
        "dest": "66:77:88:99:aa:bb"
      }
    },
    {
      "kind": "consumer",
      "name": "qwe",
      "iface": "lo",
      "payload": "bytes"
    }
  ]
}
PAYLOAD

sleep 10

llgen --debug --logging-config="llgen-log.ini" --root="/tmp/llgen-test" first setup << 'PAYLOAD'
{
    "module": "scapy",
    "items": [{"kind": "stats"}]
}
PAYLOAD
```

llgen-log.ini - is a generic python's logging configuration file. You can use this one if you don't want to read docs
about it.
```ini
[loggers]
keys=root

[handlers]
keys=file

[formatters]
keys=generic

[logger_root]
level=DEBUG
handlers=file

[handler_file]
class: logging.FileHandler
args: ('/tmp/llgen.log', 'at')
formatter: generic

[formatter_generic]
format: '%(asctime)s %(levelname)-8s %(name)-15s %(message)s'
```

"module" - to choose concrete tool that will be used to generate/consume traffic
"time_to_live" - kill time - daemon will terminate in this amount of seconts
"items" - set of commands to daemon.

Inside items. This main "key" in each item is a "kind" key. It define exact action
that must be done. Other keys depends from "kind" - each kind define mandatory and optional keys.

Kinds:
* "producer" - create a traffic generator.
* "consume" - crate traffic consumer
* "stats" - request statics

At this moment "producer" and "consume" use same format of "item" i.e. all keys passed to producer can be passed to
consumer.

!!! Right now it don't use most of the fields passed into consumer. It is a technical debt to implement correct packet
generation/filtering.
