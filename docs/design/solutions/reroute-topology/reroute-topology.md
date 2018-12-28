# Reroute topology: eliminate Cache topology

## The problem 
One of the things that blocks Hub&Spoke is Cach topology. The main task of Cache topology is rerouting the flow. We need to implement this functionality as a new topology, and the part related to processing events (SwitchInfoData, IslInfoData, PortInfoData) should be implemented in the Event topology.
### Use case
![Use case of Reroute topology](./use-case-reroute-topology.png "Use case of Reroute topology")

### Sequence diagram
![Sequence diagram of event handling](./sequence-event-handling.png "Sequence diagram of event handling")
![Sequence diagram of Reroute topology](./sequence-reroute-topology.png "Sequence diagram of Reroute topology")

### Implementation
Pull request: #1520 (Implementation of the Reroute topology and NetworkTopologyBolt in the Event topology)