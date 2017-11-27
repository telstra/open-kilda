#OpenKilda Simulator

Provides a switch simulator to OpenKilda.  The simulator basically acts as both a 
speaker and a switch.  It listens to a "simulator-topic" in Kafka for command
and control messages, and then simulates a switch by posting and listening to
messages in the same manner as a speaker.

##Supported Functions

These messages can be sent to the "kilda-simulator" topic to control the
simulated topology.

###Topology
Create a network topology in a single document.

####Message Format
`{
   "type": "TOPOLOGY",
   "switches": [
     {
       "dpid": "00:00:00:00:00:01",
       "num_of_ports": 1,
       "links": [
         {
           "latency": 10,
           "local_port": 0,
           "peer_switch": "00:00:00:00:02",
           "peer_port": 0
         }
       ]
     },
     {
       "dpid": "00:00:00:00:00:02",
       "num_of_ports": 1,
       "links": [
         {
           "latency": 10,
           "local_port": 0,
           "peer_switch": "00:00:00:00:01",
           "peer_port": 0
         }
       ]
     }
   ]
 }`

###AddLink
Adds a link (ISL) between two of the switches (switches must already be defined
in the topology).

####Message Format
`{
   "type": "DO_ADD_LINK",
   "dpid": "00:00:00:00:00:06",
   "link": {
     "latency": 10,
     "local_port": 5,
     "peer_switch": "00:00:00:00:01",
     "peer_port": 5
   }
 }`

###PortMod
Modify the state of a switch port.  Ports have two variables which determine
if they are up and forwarding traffic:

- active: if `true` then port is considered UP
- forwarding: if `true` then port is forwarding traffic

Setting active to `false` will send a notification to the Controller that the
port is DOWN and setting it to `true` sends an UP notification.

Setting forwarding to `true` results in ISL's being "active" on that port, 
provided it is an ISL port as created in AddLink.  Setting to `false` will
prevent the port from responding to DiscoverISL messages from the controller.
Regardless of the value, changing `forwarding` will not notify the controller
of a change of port state.  This can be used to test failure cases where
link-loss-forwarding is not enabled on the ISL.

####Message Format
`{
  "type": "DO_PORT_MOD",
  "dpid": "00:00:00:00:00:06",
  "port-num": 5,
  "active": true,
  "forwarding": true
}`

###AddSwitch
Add a new switch to the topology.

####Message Format
`{
   "type": "DO_ADD_SWITCH",
   "dpid": "00:00:00:00:00:03",
   "num_of_ports": 52
 }`

