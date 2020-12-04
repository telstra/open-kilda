#!/bin/bash

set -m

/opt/zookeeper/bin/zkServer.sh start-foreground &

# ensure health-checks were passed
for attemp in $(seq 1 3); do
  if jps | grep -q QuorumPeer; then
    sleep 3
  else
    echo "Zookeeper hasn't been started yet"
  fi
done

# add default zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE} ""
# TODO remove common_component node when zero downtime feature will be implemented
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/common_component ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/common_component/common_run_id ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/common_component/common_run_id/signal ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/common_component/common_run_id/build-version "v3r\$i0n"

# add northbound zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/northbound ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/northbound/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/northbound/blue/signal ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/northbound/blue/build-version "v3r\$i0n"

# add grpc zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/grpc ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/grpc/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/grpc/blue/signal ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/grpc/blue/build-version "v3r\$i0n"

# add floodlight zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/1 ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/1/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/1/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/1/build-version "v3r\$i0n"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/2 ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/2/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/2/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/2/build-version "v3r\$i0n"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/1.stats ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/1.stats/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/1.stats/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlight/1.stats/build-version "v3r\$i0n"


# add network topology zkNodes


# add floodlight router topology zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlightrouter ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlightrouter/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlightrouter/blue/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlightrouter/blue/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/floodlightrouter/blue/build-version "v3r\$i0n"

# add connected devices topology zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/connecteddevices ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/connecteddevices/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/connecteddevices/blue/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/connecteddevices/blue/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/connecteddevices/blue/build-version "v3r\$i0n"


# add flowhs topology zkNodes


# add isl latency topology zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/isllatency ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/isllatency/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/isllatency/blue/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/isllatency/blue/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/isllatency/blue/build-version "v3r\$i0n"


# add nbworker topology zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/nbworker ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/nbworker/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/nbworker/blue/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/nbworker/blue/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/nbworker/blue/build-version "v3r\$i0n"

# add opentsdb topology zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/opentsdb ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/opentsdb/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/opentsdb/blue/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/opentsdb/blue/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/opentsdb/blue/build-version "v3r\$i0n"


# add ping topology zkNodes


# add portstate topology zkNodes


# add reroute topology zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/reroute ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/reroute/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/reroute/blue/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/reroute/blue/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/reroute/blue/build-version "v3r\$i0n"

# add stats topology zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/stats ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/stats/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/stats/blue/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/stats/blue/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/stats/blue/build-version "v3r\$i0n"


# add swmanager topology zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/swmanager ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/swmanager/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/swmanager/blue/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/swmanager/blue/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/swmanager/blue/build-version "v3r\$i0n"


# add server42 control topology zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control/blue/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control/blue/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control/blue/build-version "v3r\$i0n"

# add server42 control app zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control-app ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control-app/server42-control-app-run-id ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control-app/server42-control-app-run-id/signal ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control-app/server42-control-app-run-id/build-version "v3r\$i0n"

# add server42 stats app zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-stats-app ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-stats-app/server42-stats-app-run-id ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-stats-app/server42-stats-app-run-id/signal ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-stats-app/server42-stats-app-run-id/build-version "v3r\$i0n"

# add server42 control storm stub zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control-storm-stub ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control-storm-stub/server42-control-storm-stub-run-id ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control-storm-stub/server42-control-storm-stub-run-id/signal ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/server42-control-storm-stub/server42-control-storm-stub-run-id/build-version "v3r\$i0n"

fg %1
