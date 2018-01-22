#!/usr/bin/env bash

#
# The steps to use this file are:
#   1) export gateway=`netstat -rn | grep "^0.0.0.0" | awk '{print $2}'`
#   2) mn --topo linear,k=2,n=2 --switch ovsk,protocols=OpenFlow13 --controller=remote,ip=${gateway}:6653
#   3) sh ./h1s1_h2s1_rules.sh
#   4) h1s1 ping -c 1 h2s1

# this next line will find the line that starts with 0.0.0.0 and capture the second field.

ovs-ofctl -O OpenFlow13 add-flow s1 idle_timeout=0,priority=1000,in_port=1,actions=output:2
ovs-ofctl -O OpenFlow13 add-flow s1 idle_timeout=0,priority=1000,in_port=2,actions=output:1
