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

### Example of local zero downtime deployment

# build stable and latest images. Deploy stable images

```
make clean build-stable GRADLE_COMPILE_PARAMS="-x test"  build-latest GRADLE_COMPILE_PARAMS="-x test" up-stable
```

# send START signal to blue components

```
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight/1/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight/2/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight_router/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/network/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/flow_hs/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/reroute/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/connecteddevices/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/swmanager/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/isllatency/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/nb_worker/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/opentsdb/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/ping/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/portstate/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/stats/blue/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/server42_control_topology/blue/signal START
```

# ensure that state > 0

```
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/floodlight/1/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/floodlight/2/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/floodlight_router/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/network/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/flow_hs/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/reroute/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/connecteddevices/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/swmanager/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/isllatency/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/nb_worker/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/opentsdb/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/ping/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/portstate/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/stats/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/server42_control_topology/blue/state
```

# check that all blue components have same version

```
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/floodlight/1/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/floodlight/2/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/floodlight_router/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/network/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/flow_hs/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/reroute/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/connecteddevices/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/swmanager/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/isllatency/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/nb_worker/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/opentsdb/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/ping/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/portbuild-version/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/stats/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/server42_control_topology/blue/build-version
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/common_component/common_run_id/build-version
```

# create a topology or run func tests

```
make func-tests PARAMS='--tests ConfigurationSpec'
```

# create a flow via Northbound


# shutdown blue floodlight (floodlight 1)

```
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight/2/signal SHUTDOWN
```

# deploy green components from latest images

```
make up-green
```

# set version for green components

```
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight/2/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight_router/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/network/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/flow_hs/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/reroute/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/connecteddevices/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/swmanager/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/isllatency/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/nb_worker/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/opentsdb/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/ping/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/portstate/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/stats/green/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/server42_control_topology/green/build-version green
```

# send START signal to green components

```
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight/2/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight_router/green/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/flow_hs/green/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/reroute/green/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/connecteddevices/green/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/swmanager/green/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/isllatency/green/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/nb_worker/green/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/opentsdb/green/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/ping/green/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/portstate/green/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/stats/green/signal START
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/server42_control_topology/green/signal START
```

# turn off blue network and turn on green network

```
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/common_component/common_run_id/build-version green
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/network/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/network/green/signal START
```

# send shutdown signal to all blue components

```
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight/1/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight_router/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/flow_hs/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/reroute/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/connecteddevices/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/swmanager/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/isllatency/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/nb_worker/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/opentsdb/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/ping/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/portstate/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/stats/blue/signal SHUTDOWN
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/server42_control_topology/blue/signal SHUTDOWN
```

# wait till state of all blue components will became 0

```
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/floodlight/1/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/floodlight_router/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/network/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/flow_hs/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/reroute/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/connecteddevices/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/swmanager/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/isllatency/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/nb_worker/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/opentsdb/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/ping/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/portstate/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/stats/blue/state
../zookeeper-3.4.0/bin/zkCli.sh get /kilda/server42_control_topology/blue/state
```

# change messaging version for floodlight_1 in zookeeper

```
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight/1/build-version green
```

# start floodlight_1 using `latest` image and emit start signal

```
../zookeeper-3.4.0/bin/zkCli.sh set /kilda/floodlight/1/signal START
```

# turn off all blue topologies

