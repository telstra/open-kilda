# Floodlight: separate processing of ISL discovery messages from other commands

## Solution overview
The `kilda.speaker` topic is split into the following incoming topics:
* `kilda.speaker` - incoming general requests (dump rules, dump meters, reset rules, list ports, configure ports, etc.)
* `kilda.speaker.disco` - incoming ISL discovery requests.
* `kilda.speaker.flow` - incoming install / delete flow requests.
* `kilda.speaker.flow.ping` - incoming flow ping requests.

Floodlight Kilda Modules has multiple reactor threads running and each of them assigned to one or more topics. 

### Sequence Diagrams

#### Current implementation
![Floodlight Communication diagram (Old)](./floodlight-communication-old.png "Floodlight Collaboration diagram (Old)")

#### New implementation
![Floodlight Communication diagram (New)](./floodlight-communication-new.png "Floodlight Collaboration diagram (New)")
