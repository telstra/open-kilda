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
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/common_component/common_run_id/build-version "v3r\$i0n"

# add floodlight zkNodes


# add network topology zkNodes


# add floodlight router topology zkNodes


# add connected devices topology zkNodes
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/connecteddevices ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/connecteddevices/blue ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/connecteddevices/blue/signal "START"
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/connecteddevices/blue/state ""
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE}/connecteddevices/blue/build-version "v3r\$i0n"


# add flowhs topology zkNodes


# add isl latency topology zkNodes


# add nbworker topology zkNodes


# add opentsdb topology zkNodes


# add ping topology zkNodes


# add portstate topology zkNodes


# add reroute topology zkNodes


# add stats topology zkNodes


# add swmanager topology zkNodes


fg %1
