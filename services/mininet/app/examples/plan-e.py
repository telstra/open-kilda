#!/usr/bin/env python

#
# Plan E builds on Plan D, add functions and loops (many mininets) to model test executions
##

from mininet.net import Mininet
from mininet.node import OVSSwitch, Controller, RemoteController
from mininet.log import setLogLevel, info
from mininet.cli import CLI
from mininet.clean import cleanup
from mininet.util import errRun

import subprocess
import re

# globals
net = None  # mininet object
hosts1 = None
hosts2 = None

class KildaSwitch( OVSSwitch ):
    "Add the OpenFlow13 Protocol"
    def __init__(self,name,**params):
        params['protocols'] = 'OpenFlow13'
        OVSSwitch.__init__(self, name, **params)

    @classmethod
    def batchStartup( cls, switches, run=errRun ):
        """
        Mininet looks for this during stop(). It exists in OVSSwitch. Kilda, at the moment,
        doesn't like this batch operation (and it shouldn't be done in batch)
        """
        info ("\nIGNORE batchStartup()\n")
        for switch in switches:
            if switch.batch:
                info ("\n .... BATCH = TRUE !!!!!!\n")
        return switches

    @classmethod
    def batchShutdown( cls, switches, run=errRun ):
        """
        Mininet looks for this during stop(). It exists in OVSSwitch. Kilda, at the moment,
        doesn't like this batch operation (and it shouldn't be done in batch)
        """
        info ("\nIGNORE batchShutdown()\n")
        for switch in switches:
            if switch.batch:
                info ("\n .... BATCH = TRUE !!!!!!\n")
        return switches



def get_gateway():
    gateway = '127.0.0.1'
    netstat = subprocess.check_output(['netstat', '-rn']).split('\n')
    for line in netstat:
        if line.startswith('0.0.0.0'):
            gateway = re.split('\s+', line)[1]
            break
    return gateway


def add_rules():
    subprocess.Popen(["ovs-ofctl","-O","OpenFlow13","add-flow","s1",
                          "idle_timeout=0,priority=1000,in_port=1,actions=output:2"],
                         stdout=subprocess.PIPE).wait()
    subprocess.Popen(["ovs-ofctl","-O","OpenFlow13","add-flow","s1",
                          "idle_timeout=0,priority=1000,in_port=2,actions=output:1"],
                         stdout=subprocess.PIPE).wait()


def build():
    global net, hosts1, hosts2
    net = Mininet( controller=RemoteController, switch=KildaSwitch, build=False )
    gateway = get_gateway()

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
    net.start()
    add_rules()


def ping(host1, host2):
    result = host1.cmd( 'ping -c1 -w1 %s' % (host2.IP()) )
    lines = result.split("\n")
    if "1 packets received" in lines[3]:
        print "CONNECTION BETWEEN ", host1.IP(), "and", host2.IP()
    else:
        print "NO CONNECTION BETWEEN ", host1.IP(), "and", host2.IP()


def clean():
    global hosts1, hosts2, net
    hosts1 = None
    hosts2 = None
    net.stop()
    net = None
    setLogLevel( 'warning' )  # mininet cleanup is noisy
    cleanup()
    setLogLevel( 'info' )


def main():
    setLogLevel( 'info' )

    count = 4
    for i in range(count):
        info( "*** Running Loop #{} of {}".format(i+1, count) )
        build()
        ping(hosts1[0], hosts1[1]) # Connection
        ping(hosts1[0], hosts2[0]) # No connection
        info ("--> Cleanup\n")
        clean()
        info ("..\n")
        info ("..\n")


# Go for it!
main()
