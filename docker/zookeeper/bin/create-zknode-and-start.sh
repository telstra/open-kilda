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

# add default zkNode
/opt/zookeeper/bin/zkCli.sh -server 127.0.0.1:2181 create /${KILDA_ZKNODE} ""

# add floodlight zkNodes


# add network topology zkNodes


# add floodlight router topology zkNodes


# add connected devices topology zkNodes


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
