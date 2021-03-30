# Storm Topology Management

## Introduction

There are a few patterns to how the Storm Topologies work in Kilda. This document will do its best to describe the patterns. If what you're looking for isn't documented yet, please add to this document.

## Removing a Storm Topology

### Removing Topology Makefile Targets

Each topology has a start/stop target in the wfm Makefile. Please remove the topology targets:

    services/wfm/Makefile

### Removing Topology Health Checks

The Northbound API has a target for reporting back on the health of storm topologies. Initially this is just operational/non-operational status.

The call to Northbound uses the following enum type to figure out which services to call:

    org.openkilda.messaging.ServiceType

That enum is leveraged by:

     org.openkilda.northbound.service.impl.HealthCheckImpl
     
