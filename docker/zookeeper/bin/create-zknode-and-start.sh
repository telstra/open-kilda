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

fg %1
