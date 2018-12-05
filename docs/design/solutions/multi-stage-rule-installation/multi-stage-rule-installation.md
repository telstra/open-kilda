# Multi-stage process of switch rule installation 

## Goals
Introduce check-points in the process of switch rule installation that eliminate cases 
when a flow becomes broken due to unsuccessful rule setup.  

The current reroute implementation performs removal of old rules 
and installation of new ones as simultaneous and non-correlated operations. 
So that a failure in installation makes a new flow uncompleted and  
at the same time doesn't prevent removal of old rules.  
 
## Solution overview
CrudBolt should prepare several groups of install and remove commands, 
and send them as a single message to TrasactionBolt. 

The groups are built in accordance to the designed execution plan: 
all commands of the 1st group are to be delivered right after the message received by TrasactionBolt.
The 2nd group is processed only when TrasactionBolt receives acknowledge messages for all commands
of the previous group. And so on until the last group commands have been sent and confirmed.

CrudBolt for the reroute operation will send 4 groups:
1. InstallTransitFlow + InstallEgressFlow for the new flow
2. InstallIngressFlow for the new flow
3. RemoveFlow for the old ingress rule
4. RemoveFlow for the rest old rules

This process keeps the old flow operable until all rules of the new flow are installed. 

### Diagrams
#### Current implementation
![Rule installation (Old)](./rule-installation-old.png "Rule installation (Old)")

#### New implementation
![Multi-stage rule installation (New)](multi-stage-rule-installation.png "Multi-stage rule installation  (New)")
