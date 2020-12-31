# Zero Downtime Upgrades for Open Kilda

## Rationale
Current deployment process requires time slot with no reactions from contol plane for any network events as long
as no flow/switch ops are available as well. To improve this new deployment procedure is proposed.
It's details are described in this document.

## Solution

Provide ability to deploy kilda in so called blue/green mode. Which will allow to quickly switch
system between different release versions. However, due to event-driven nature of kilda and a bunch of
limitations from Apache Storm there are changes made in original Blue/Green approach.

### Shared Transport

Kilda uses Kafka as a Message broker and messages in it to communicate between components of the system.
To provide backward compatibility between green and new versions of kilda within the same kafka topic
we need to split messages by some value between new and old ones. For this purpose will be used kafka message
header with run-id encoded in it. Run id should be unique within a single deployment.
Each component will emit messages with run-id header that is valid by the deployment. All receiver parts
should validate message header first and verify that run id matches with its configuration in that case component
will handle the message. Kafka provides API to write custom interceptors for both consumer and producer. This
interceptor will be responsible for verifying deployment id.

### Zookeeper to store the state

Since storm has limitations on lifecycle of its topologies, new mechanism is required to deal with topologies states. 
Also it should be responsible to handle graceful shutdown procedure for topologies. For this role Apache Zookeeper
looks like a good fit.

#### Node structure for Zookeeper

`/kilda/{component_type}/{env_id}` - root for every component process, where:
`component_type` - topology or service name, e.g. `floodlight`, `network`, `nb_worker`
`env_id` - flag of blue or green env

#### Signal, States and Build-Version

Each component, except Northbound and GRPC, root node will have 3 children zNodes:
- signal - input field, can be `START` or `SHUTDOWN`, the way to make component start emittin processing new events
- state - int, number of active subcomponents of a component, when `SHUTDOWN` is emitted, should be `0`, positive otherwise.
- build-version - string field with run id, could be changed on fly, see #Shared transport for details
For the long running task such as hub in hub and spoke topologies, there should be a way to stop receiving new
requests, finishing up existing, and after that decrementing counter by 1 in a state field.

#### Basic Deployment Scenario:

*Note*
For this upgrade procedure each switch should be connected to 2 floodlights simultaneously.
 
Since floodlights could be distinguished by region let's assume, that all odd regions are green and even are blue
For the scenario, below suppose that `blue` is a current env and `green` is the one to be deployed.
Based on that the process should look like:

- Ensure that `signal` for the `green` components is set to `SHUTDOWN` and the `build-version` is updated  
- Deploy green topologies  
- Set `SHUTDOWN` signal for `green` floodlights 
- Redeploy floodlight containers of `green` color
- Send `START` for the following `green` components:
  - NB Worker
  - Switch Manager
  - Flow HS
  - Reroute
  - Ping
  - Floodlight Router
  - Connected Devices
  - ISL Latency
  - Port State
  - Server 42 Control
  - Stats
- Send `START` for `green` Floodlights
- Emit `SHUTDOWN` for the network blue
- Emit `START` for the network green
- Deploy Green Northbound(forward new requests to it from customer)
- Redeploy grpc containers of `green` color
- Important!!!(Decision Point): if everything works and env upgraded is accepted, the following steps are required
- Set `SHUTDOWN` signal for `blue` floodlights
- Set new `build-version` for the left floodlights
- Redeploy `blue` fl containers
- Set `START` signal for `blue` floodlights (now they are become `green`)
- Terminate `blue` topologies

If during the process something goes wrong at a decision point, fallback to blue network topology is required,
the following rollback:
- Redeploy `blue` northbound
- Rollback `green` floodlight containers back to `blue` version
- Rollback GRPC to `blue` version 

#### Post Deployment phase

Once a deployment is finished the last thing to be made is removal of consumer group offsets for the `blue` environment
so the next release recreate them for the `blue`.
