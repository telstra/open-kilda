# Teoretical consequences of floodlight outage

# Network healthcheck/discovery
Our OFEventWFMTopology topology trace "discovery" events from floodlight and produce control actions
from this events... If there is no "events" for some "known" port(switch) - it decide that port is down. 
(only absolute time is considered). In this case it will reroute all flows tied to this died port...

In case when floodlight lost connection to kafka(or will be shooted down)... all known ports will be 
marked as "down". This will be reflected into neo4j DB and stored into kafka topics...

But network should stay operatinal, because floodlight is "missing" and can't apply control actions 
produced by our storm topologies.

When floodlight will be recovered, it reads kafka topics from first unread message i.e. all messages
produced during floodlight outage.

Floodlight will apply commands from outdated messages... So - consequences:
* At least it will turn off and redicover all network
* All flows reroute attempt
* Possible conflict benween existing(previous) flow rules inside switches and new(added after outage)
* unpredictable network state during transition period
