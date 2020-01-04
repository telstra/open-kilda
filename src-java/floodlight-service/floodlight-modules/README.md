# Floodlight - Modules

This project holds the Floodlight application.
It is used along with Floodlight SDN controller to manage OpenFlow compatible switches.

# Developers

## Meters for default (system) rules.
We added meters for our system rules to prevent switch and controller being overloaded by package floods (such flood can be a consequence of traffic loop on client side, i.e. beyond our control). Such meters are being used for dropping packets to prevent controller being killed by receiving too many packets. For every type of these rules there is a separate property to define specific rate of the meter.

Since not all switches support OFPMF_PKTPS flag (using this flag we are able to define rate value in packets per second), we install meters with OFPMF_KBPS flag and rate value is calculated by the following way:
```
rate = (ratePkts * disco_packet_size) / 1024L
where:
 ratePkts - is the rate in packets for specific type of rule
 disco_packet_size - the size of discovery packet, is being defined in properties file with the key 'org.openkilda.floodlight.switchmanager.SwitchManager.disco-packet-size'.
```
