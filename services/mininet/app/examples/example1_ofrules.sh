#!/usr/bin/env bash

#
# The steps to use this file are:
#   1) mn --topo linear,8
#   2) sh /app/examples/example1_ofrules.sh
#   3) xx h1 ping h4 (replaced by h2..h4
#   4) h2 ping h4
#   5) h5 ping h6
#   6) h7 ping h8
#

echo "Setting up openflow rules on s1..."
ovs-ofctl -O OpenFlow13 add-flow s1 idle_timeout=0,priority=1,actions=drop
ovs-ofctl -O OpenFlow13 add-flow s1 idle_timeout=0,priority=2,dl_type=0x88cc,action=output:controller
ovs-ofctl -O OpenFlow13 add-flow s1 idle_timeout=0,priority=1001,in_port=2,dl_vlan=11,actions=strip_vlan,output:1
ovs-ofctl -O OpenFlow13 add-flow s1 idle_timeout=0,priority=1000,in_port=1,actions=push_vlan:0x8100,mod_vlan_vid:10,output:2
echo "Setting up openflow rules on s2:"
ovs-ofctl -O OpenFlow13 add-flow s2 idle_timeout=0,priority=1,actions=drop
ovs-ofctl -O OpenFlow13 add-flow s2 idle_timeout=0,priority=2,dl_type=0x88cc,action=output:controller
ovs-ofctl -O OpenFlow13 add-flow s2 idle_timeout=0,priority=1001,in_port=3,dl_vlan=11,actions=output:2
ovs-ofctl -O OpenFlow13 add-flow s2 idle_timeout=0,priority=1000,in_port=2,dl_vlan=10,actions=output:3
ovs-ofctl -O OpenFlow13 add-flow s2 idle_timeout=0,priority=1003,in_port=1,actions=push_vlan:0x8100,mod_vlan_vid:12,output:3
ovs-ofctl -O OpenFlow13 add-flow s2 idle_timeout=0,priority=1004,in_port=3,dl_vlan=13,actions=strip_vlan,output:1
echo "Setting up openflow rules on s3:"
ovs-ofctl -O OpenFlow13 add-flow s3 idle_timeout=0,priority=1,actions=drop
ovs-ofctl -O OpenFlow13 add-flow s3 idle_timeout=0,priority=2,dl_type=0x88cc,action=output:controller
ovs-ofctl -O OpenFlow13 add-flow s3 idle_timeout=0,priority=1001,in_port=3,dl_vlan=11,actions=output:2
ovs-ofctl -O OpenFlow13 add-flow s3 idle_timeout=0,priority=1000,in_port=2,dl_vlan=10,actions=output:3
ovs-ofctl -O OpenFlow13 add-flow s3 idle_timeout=0,priority=1002,in_port=2,dl_vlan=12,actions=output:3
ovs-ofctl -O OpenFlow13 add-flow s3 idle_timeout=0,priority=1003,in_port=3,dl_vlan=13,actions=output:2
echo "Setting up openflow rules on s4:"
ovs-ofctl -O OpenFlow13 add-flow s4 idle_timeout=0,priority=1,actions=drop
ovs-ofctl -O OpenFlow13 add-flow s4 idle_timeout=0,priority=2,dl_type=0x88cc,action=output:controller
ovs-ofctl -O OpenFlow13 add-flow s4 idle_timeout=0,priority=1001,in_port=1,actions=push_vlan:0x8100,mod_vlan_vid:11,output:2
ovs-ofctl -O OpenFlow13 add-flow s4 idle_timeout=0,priority=1000,in_port=2,dl_vlan=10,actions=strip_vlan,output:1
ovs-ofctl -O OpenFlow13 add-flow s4 idle_timeout=0,priority=1002,in_port=2,dl_vlan=12,actions=strip_vlan,output:1
ovs-ofctl -O OpenFlow13 add-flow s4 idle_timeout=0,priority=1003,in_port=1,actions=push_vlan:0x8100,mod_vlan_vid:13,output:2
echo "Setting up openflow rules on s5 .. two switch, no vlan example:"
ovs-ofctl -O OpenFlow13 add-flow s5 idle_timeout=0,priority=1,actions=drop
ovs-ofctl -O OpenFlow13 add-flow s5 idle_timeout=0,priority=2,dl_type=0x88cc,action=output:controller
ovs-ofctl -O OpenFlow13 add-flow s5 idle_timeout=0,priority=1000,in_port=1,actions=output:3
ovs-ofctl -O OpenFlow13 add-flow s5 idle_timeout=0,priority=1001,in_port=3,actions=output:1
echo "Setting up openflow rules on s6 .. two switch, no vlan example:"
ovs-ofctl -O OpenFlow13 add-flow s6 idle_timeout=0,priority=1,actions=drop
ovs-ofctl -O OpenFlow13 add-flow s6 idle_timeout=0,priority=2,dl_type=0x88cc,action=output:controller
ovs-ofctl -O OpenFlow13 add-flow s6 idle_timeout=0,priority=1000,in_port=1,actions=output:2
ovs-ofctl -O OpenFlow13 add-flow s6 idle_timeout=0,priority=1001,in_port=2,actions=output:1
echo "Setting up openflow rules on s7 .. two switch, with vlan example:"
ovs-ofctl -O OpenFlow13 add-flow s7 idle_timeout=0,priority=1,actions=drop
ovs-ofctl -O OpenFlow13 add-flow s7 idle_timeout=0,priority=2,dl_type=0x88cc,action=output:controller
ovs-ofctl -O OpenFlow13 add-flow s7 idle_timeout=0,priority=1000,in_port=1,actions=push_vlan:0x8100,mod_vlan_vid:50,output:3
ovs-ofctl -O OpenFlow13 add-flow s7 idle_timeout=0,priority=1001,in_port=3,dl_vlan=60,actions=strip_vlan,output:1
echo "Setting up openflow rules on s8 .. two switch, with vlan example:"
ovs-ofctl -O OpenFlow13 add-flow s8 idle_timeout=0,priority=1,actions=drop
ovs-ofctl -O OpenFlow13 add-flow s8 idle_timeout=0,priority=2,dl_type=0x88cc,action=output:controller
ovs-ofctl -O OpenFlow13 add-flow s8 idle_timeout=0,priority=1000,in_port=1,actions=push_vlan:0x8100,mod_vlan_vid:60,output:2
ovs-ofctl -O OpenFlow13 add-flow s8 idle_timeout=0,priority=1001,in_port=2,dl_vlan=50,actions=strip_vlan,output:1

