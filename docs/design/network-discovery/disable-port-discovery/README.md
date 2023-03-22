# Disable Port Discovery Feature

##  Overview

Some switch ports should not be used in the network discovery process. By default,
all ports on all switches are available for discovery. Admin has the ability to
enable or disable discovery on a specific port on a switch using Northbound REST API.

## Persistence layer

On a DB layer, enable or disable discovery property is stored in PortProperties object.

![workflow](domain-model.png) 

## Workflow

Northbound sends update port properties request directly to the network topology.
  
For each port up event, Port Fsm should check database and start network discovery
process only if the port discovery feature is enabled.

![workflow](../port-FSM.png)

When port properties changed, the corresponding Port Fsm should be notified according
to the next diagram:

![workflow](disable-port-discovery.png)
