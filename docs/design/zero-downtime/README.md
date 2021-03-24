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
`component_type` - topology or service name, e.g. `floodlight`, `network`, `nbworker`
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

##### Build stable and latest images. Deploy stable images

```
make clean build-stable GRADLE_COMPILE_PARAMS="-x test"  build-latest GRADLE_COMPILE_PARAMS="-x test" up-stable
```

NOTE: following commands will be run with the help of ZooKeeper CLI bash script.
You can download zookeeper from [here](https://archive.apache.org/dist/zookeeper/):
* Download ZooKeeper sources (we used version 3.6.2)
* Extract sources from `zookeeper-***.tar.gz`
* Run CLI `./apache-zookeeper-3.6.2-bin/bin/zkCli.sh` to connect to local ZooKeeper
* If you need to connect to remote ZooKeeper run `./apache-zookeeper-3.6.2-bin/bin/zkCli.sh -server server1,server2`

After successfully connection to Zookeeper server you can run following commands
from CLI to manipulate Kilda components.

#### Send START signal to blue components

```
set /kilda/floodlight/1/signal START
set /kilda/floodlight/2/signal START
set /kilda/floodlightrouter/blue/signal START
set /kilda/network/blue/signal START
set /kilda/flowhs/blue/signal START
set /kilda/flowmonitoring/blue/signal START
set /kilda/reroute/blue/signal START
set /kilda/connecteddevices/blue/signal START
set /kilda/swmanager/blue/signal START
set /kilda/isllatency/blue/signal START
set /kilda/nbworker/blue/signal START
set /kilda/opentsdb/blue/signal START
set /kilda/ping/blue/signal START
set /kilda/portstate/blue/signal START
set /kilda/stats/blue/signal START
set /kilda/server42-control/blue/signal START
```

#### Ensure that state > 0

```
get /kilda/floodlight/1/state
get /kilda/floodlight/2/state
get /kilda/floodlightrouter/blue/state
get /kilda/network/blue/state
get /kilda/flowhs/blue/state
get /kilda/flowmonitoring/blue/state
get /kilda/reroute/blue/state
get /kilda/connecteddevices/blue/state
get /kilda/swmanager/blue/state
get /kilda/isllatency/blue/state
get /kilda/nbworker/blue/state
get /kilda/opentsdb/blue/state
get /kilda/ping/blue/state
get /kilda/portstate/blue/state
get /kilda/stats/blue/state
get /kilda/server42-control/blue/state
```

#### Check that all blue components have same version

```
get /kilda/northbound/blue/build-version
get /kilda/grpc/blue/build-version
get /kilda/floodlight/1/build-version
get /kilda/floodlight/2/build-version
get /kilda/floodlightrouter/blue/build-version
get /kilda/network/blue/build-version
get /kilda/flowhs/blue/build-version
get /kilda/flowmonitoring/blue/build-version
get /kilda/reroute/blue/build-version
get /kilda/connecteddevices/blue/build-version
get /kilda/swmanager/blue/build-version
get /kilda/isllatency/blue/build-version
get /kilda/nbworker/blue/build-version
get /kilda/opentsdb/blue/build-version
get /kilda/ping/blue/build-version
get /kilda/portstate/blue/build-version
get /kilda/stats/blue/build-version
get /kilda/server42-control/blue/build-version
get /kilda/server42-control-app/server42-control-app-run-id/build-version
get /kilda/server42-stats-app/server42-stats-app-run-id/build-version
get /kilda/server42-control-storm-stub/server42-control-storm-stub-run-id/build-version
get /kilda/func_test/func_test_run_id/build-version
```

#### Create a topology or run func tests

```
make func-tests PARAMS='--tests ConfigurationSpec'
```

#### Create a flow via Northbound


#### Shutdown blue floodlight (floodlight 2)

```
set /kilda/floodlight/2/signal SHUTDOWN
```

#### Deploy green components from latest images

```
make up-green
```

#### Set version for green components

```
set /kilda/northbound/green/build-version green
set /kilda/grpc/green/build-version green
set /kilda/floodlight/2/build-version green
set /kilda/floodlightrouter/green/build-version green
set /kilda/network/green/build-version green
set /kilda/flowhs/green/build-version green
set /kilda/flowmonitoring/green/build-version green
set /kilda/reroute/green/build-version green
set /kilda/connecteddevices/green/build-version green
set /kilda/swmanager/green/build-version green
set /kilda/isllatency/green/build-version green
set /kilda/nbworker/green/build-version green
set /kilda/opentsdb/green/build-version green
set /kilda/ping/green/build-version green
set /kilda/portstate/green/build-version green
set /kilda/stats/green/build-version green
set /kilda/server42-control/green/build-version green
```

#### Send START signal to green components

```
set /kilda/floodlight/2/signal START
set /kilda/floodlightrouter/green/signal START
set /kilda/flowhs/green/signal START
set /kilda/flowmonitoring/green/signal START
set /kilda/reroute/green/signal START
set /kilda/connecteddevices/green/signal START
set /kilda/swmanager/green/signal START
set /kilda/isllatency/green/signal START
set /kilda/nbworker/green/signal START
set /kilda/opentsdb/green/signal START
set /kilda/ping/green/signal START
set /kilda/portstate/green/signal START
set /kilda/stats/green/signal START
set /kilda/server42-control/green/signal START
```

#### Turn off blue network and turn on green network

```
set /kilda/func_test/func_test_run_id/build-version green
set /kilda/server42-control-app/server42-control-app-run-id/build-version green
set /kilda/server42-stats-app/server42-stats-app-run-id/build-version green
set /kilda/server42-control-storm-stub/server42-control-storm-stub-run-id/build-version green
set /kilda/network/blue/signal SHUTDOWN
set /kilda/network/green/signal START
```

#### Send shutdown signal to all blue components

```
set /kilda/floodlight/1/signal SHUTDOWN
set /kilda/floodlightrouter/blue/signal SHUTDOWN
set /kilda/flowhs/blue/signal SHUTDOWN
set /kilda/flowmonitoring/blue/signal SHUTDOWN
set /kilda/reroute/blue/signal SHUTDOWN
set /kilda/connecteddevices/blue/signal SHUTDOWN
set /kilda/swmanager/blue/signal SHUTDOWN
set /kilda/isllatency/blue/signal SHUTDOWN
set /kilda/nbworker/blue/signal SHUTDOWN
set /kilda/opentsdb/blue/signal SHUTDOWN
set /kilda/ping/blue/signal SHUTDOWN
set /kilda/portstate/blue/signal SHUTDOWN
set /kilda/stats/blue/signal SHUTDOWN
set /kilda/server42-control/blue/signal SHUTDOWN
```

#### Wait till state of all blue components will became 0

```
get /kilda/floodlight/1/state
get /kilda/floodlightrouter/blue/state
get /kilda/network/blue/state
get /kilda/flowhs/blue/state
get /kilda/flowmonitoring/blue/state
get /kilda/reroute/blue/state
get /kilda/connecteddevices/blue/state
get /kilda/swmanager/blue/state
get /kilda/isllatency/blue/state
get /kilda/nbworker/blue/state
get /kilda/opentsdb/blue/state
get /kilda/ping/blue/state
get /kilda/portstate/blue/state
get /kilda/stats/blue/state
get /kilda/server42-control/blue/state
```

#### Change messaging version for floodlight_1 in zookeeper

```
set /kilda/floodlight/1/build-version green
```

#### Start floodlight_1 using `latest` image and emit start signal

```
set /kilda/floodlight/1/signal START
```

#### Turn off all blue topologies
