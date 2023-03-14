# FL Statistics

## Goals
- Ability to gather statistics from switches more often
- Scale up numbers of switches

## The idea
Make a separate dedicated FL instance for statistics.

## Implementation
There are two FL instances: Management and Statistics.
- FL Statistics (FL Stats) must only collect statistics from the switches.
- FL Management (FL Mgmt) do the other work and can collect statistics as well in the case when a
switch is not connected to FL Stats.

A new topology "StatisticsRouter" was added between the "Statistics" topology and "Router"
topology. Statistics router receives a request from "Statistics" topology and makes two
separate requests for FL Mgmt and FL Stats. It puts exclude switch list into the request to 
FL Mgmt to avoid double statistics collection.

Additionally, the Statistics router sends a request to FL Stats for keeping a list of connected switches. Excluding 
rules will be based on this list. A FL Stats response with the list of connected switches contains the "controllerId". 
This allows us to have multiple FL Stats instances.

### Kafka topics participate in statistics collection.
![kafka-topics](./fl_statistics_kafka_topics.png)

### Sequence diagram
![sequence](./fl_statistics_sequence.png)
