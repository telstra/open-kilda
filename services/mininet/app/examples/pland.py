#!/usr/bin/env python

#
# Plan D builds on Plan C, removing the need to load the flow rules and/or do a ping.
#
# Still need to:
#   1) mininet> sh ./h1s1_h2s1_rules.sh
#   2) mininet> h1s1 ping h2s1
#

from mininet.net import Mininet
from mininet.node import OVSSwitch, Controller, RemoteController
from mininet.log import setLogLevel, info
from mininet.cli import CLI

import subprocess
import re


class KildaSwitch( OVSSwitch ):
    "Add the OpenFlow13 Protocol"
    def __init__(self,name,**params):
        params['protocols'] = 'OpenFlow13'
        OVSSwitch.__init__(self, name, **params)


setLogLevel( 'info' )
gateway = '127.0.0.1'
netstat = subprocess.check_output(['netstat', '-rn']).split('\n')
for line in netstat:
    if line.startswith('0.0.0.0'):
        gateway = re.split('\s+', line)[1]
        break

print "gateway=", gateway


net = Mininet( controller=RemoteController, switch=KildaSwitch, build=False )

info( "*** Creating (Remote) controllers\n" )
c0 = net.addController( 'c0', ip=gateway, port=6653)

info( "*** Creating switches\n" )
s1 = net.addSwitch( 's1' )
s2 = net.addSwitch( 's2' )

info( "*** Creating hosts\n" )
hosts1 = [ net.addHost( 'h%ds1' % n ) for n in ( 1, 2 ) ]
hosts2 = [ net.addHost( 'h%ds2' % n ) for n in ( 1, 2 ) ]

info( "*** Creating links\n" )
for h in hosts1:
    net.addLink( h, s1 )
for h in hosts2:
    net.addLink( h, s2 )
net.addLink( s1, s2 )

info( "*** Starting network\n" )
net.configHosts()

# c0.start()
# s1.start( [c0] )
# s2.start( [c0] )

net.start()

p = subprocess.Popen(["ovs-ofctl","-O","OpenFlow13","add-flow","s1",
                      "idle_timeout=0,priority=1000,in_port=1,actions=output:2"],
                     stdout=subprocess.PIPE)
p = subprocess.Popen(["ovs-ofctl","-O","OpenFlow13","add-flow","s1",
                      "idle_timeout=0,priority=1000,in_port=2,actions=output:1"],
                     stdout=subprocess.PIPE)
p.wait()

result = hosts1[0].cmd( 'ping -c1 %s' % (hosts1[1].IP()) )
lines = result.split("\n")
if "1 packets received" in lines[3]:
    print "CONNECTION BETWEEN ", hosts1[0].IP(), "and", hosts1[1].IP()
else:
    print "NO CONNECTION BETWEEN ", hosts1[0].IP(), "and", hosts1[1].IP()

result = hosts1[0].cmd( 'ping -c1 %s' % (hosts2[0].IP()) )
lines = result.split("\n")
if "1 packets received" in lines[3]:
    print "CONNECTION BETWEEN ", hosts1[0].IP(), "and", hosts2[0].IP()
else:
    print "NO CONNECTION BETWEEN ", hosts1[0].IP(), "and", hosts2[0].IP()



#info( "loss=" + loss )

info( "*** Running CLI\n" )
CLI( net )

info( "*** Stopping network\n" )
net.stop()
