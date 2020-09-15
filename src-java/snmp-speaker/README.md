# SNMP Speaker

This sub-project collects SNMP metrics from devices, and push them to Kafka for further processing.

It consists three components:
- An Apache Storm Topology application that gets up-to-date device list from a network state update topic, and provides
this device list to SNMP Collector
- An SNMP Collector, which queries devices using SNMP and push the metrics collected to a Kafka topic. For each device, 
it will query a list of configured metrics based on its `sysObjectId`, and write the raw metrics to a Kafka topic - 
`kilda.snmp.metrics` by default.
- Part of the `stats` topology application, which reads from the `kilda.snmp.metrics` and do further processing, before 
writing it out to otsdb through existing `stats-otsdb` bolt.

### How to config metric collection

SNMP uses two levels of configurations. The first level specifies what `modules`  are of interest, and the second level
gives what metrics are within each module.

The default config file name for the first level is `snmp-collection.yaml`, and the individual modules file by default
reside in a `modules` sub-directory relative to `snmp-collection.yaml`. 

In the `resources` folder, there are sample `snmp-collection.yaml` and module files. Each installation could customize
to their need. Sample property files can also be found here.

