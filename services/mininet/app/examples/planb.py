#!/usr/bin/env python

#
# Plan B shows how to interact with Mininet directly, without using the MininetRunner class.
# It replaces these two commands:
#
#   1) export gateway=`netstat -rn | grep "^0.0.0.0" | awk '{print $2}'`
#   2) mn --topo linear,k=2,n=2 --switch ovsk,protocols=OpenFlow13 --controller=remote,ip=${gateway}:6653
#
# Still need to:
#   3) mininet> sh ./h1s1_h2s1_rules.sh
#   4) mininet> h1s1 ping h2s2
#


from mininet.net import Mininet
from mininet.node import OVSSwitch, Controller, RemoteController
from mininet.topo import LinearTopo
from mininet.log import setLogLevel
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


c0 = RemoteController( 'c0', ip=gateway, port=6653 )
topo = LinearTopo( k=2, n=2 )
net = Mininet( controller=c0, topo=topo, switch=KildaSwitch, build=False )
net.build()
net.start()
CLI( net )
net.stop()
