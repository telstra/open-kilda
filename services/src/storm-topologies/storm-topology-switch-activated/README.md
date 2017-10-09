------
STORM TOPOLOGY - SWITCH ACTIVATED
------

# INTRODUCTION

This project contains the "Switch Activated" Kilda Storm Topology.

# DEVELOPER ONBOARDING

TODO

## BUILDING, IMAGING, RUNNING

### Building - Options

### Logging
- SLF4J and Logback are used. The configuration file is in src/main/resources/logback.xml

### Troubleshooting

#### Maven Build: Maven invalid LOC header (bad signature)
One of the dependent libraries is corrupt. Delete from ~/.m2/repositories and run maven again.

## DEVELOPING

### Key libraries

A good place to start is to look at the pom.xml file to see what libraries are included. 
That'll also give the latest set.  We document a few here.

1. [Storm](http://storm.apache.org/releases/1.0.2/index.html) - Apache Storm.
1. [Guice](http://curator.apache.org/getting-started.html) - Google Guice.


## TESTING

Start Kafka, ZooKeeper and a message producer.

From 

```
REM window 1
cd C:\Apps\kafka_2.10-0.10.1.1\bin\windows
zookeeper-server-start.bat ..\..\config\zookeeper.properties

REM window 2
cd C:\Apps\kafka_2.10-0.10.1.1\bin\windows
kafka-server-start.bat ..\..\config\server.properties

REM window 3
cd C:\Apps\kafka_2.10-0.10.1.1\bin\windows
kafka-console-producer.bat --broker-list localhost:9092 --topic kilda-switch-activated
```

Start the mock REST servers "OfsMockServer" and "TopologyEngineMockServer" Eclipse Run Configurations.
Run the "Local Topology Runner - Switch Activated" Eclipse Run Configuration.

There are also some unit tests that you can run.

# ARCHITECTURE

## Platform components

### Storm

#### New to Storm

A good place to start is by looking at the code examples in the [github project](https://github.com/apache/storm/). 
__NB: if you clone this project__, remember to do a `mvn package` at the top level before trying any examples

#### Storm CLI

Ensure you have the CLI installed; there are quite a few useful commands and things to assist development.

- http://storm.apache.org/releases/current/Command-line-client.html
	- http://storm.apache.org/releases/current/Setting-up-development-environment.html
- _Mac / Brew pro tip:_
	- `brew install storm`

# TODO

1. ~~Fix logging from Bolt.~~
1. ~~Add step name and # tasks to YAML config and use them in the code.~~
1. ~~Add Confirmation and Correlation bolts.~~ 
1. ~~First unit test.~~
1. DI for bolts? Should we use DI in bolts? Maybe not, keep bolts small and treat them as "implementations". Implementing DI for bolts might be complicated due to Storm's distributed nature. Need to look at IWorkerHook. 
1. ~~Implement Confirmation and Correlation bolts.~~
1. Add "state" to messages (Check with Carmine about possible states).
1. Add timestamps. e.g. timestamps : [src,timestamp]
1. Deployment into proper Storm cluster - "Local" vs. "Real" deployment.
1. ~~Send message to Topology engine.~~
1. Error handling for tuples that fail.
1. More unit tests! How to mock out stuff in bolts? e.g. web API access
1. Ack Kakfa messages so they are only consumed once.
1. ~~Work out how I should set group id - need different group for each spout?~~


### CONFIGURATION

Kilda uses the a YAML file for configuration values. These values are injected into Java Beans using Google Guice. Default config values are in src/main/resources/kilda-defaults.yml.


To override the defaults, create a new file and the file name as an argupment

```
mvn exec:java -Dexec.args="--storm.topology.config.overrides.file=<path>/<to>/overrides.yml"
```
