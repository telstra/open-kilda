# WFM - WorkFlow Manager

This project holds the Storm topologies that are used to implement
the workflow management aspects of Kilda.

# Deployment

To start topology in `local` mode:
```bash
java -cp JAR.FILE CLASS --local arguments...
```

To submit topology into storm:
```bash
storm jar JAR.FILE class arguments...
```

## Topology CLI arguments

In topology submit command you can pass arguments that will be passed into "main" call. 

Supported arguments list:
* `--name=NAME` - override topology name. If don't passed topology name constructed from topology class name.
* `--local` - inform topology that is must run into "local" mode. I.e. submit topology into 
  org.apache.storm.LocalCluster.
* `--local-execution-time=TIME` - used only in combination with `--local`. Define how long (in seconds) topology will be
  executed. `TIME` argument parsed as float number.
* `CONFIG` - path to properties file. This properties will override compiled in properties. You can pass more than one
  file to pass override on override on override... and so on. 

## Configuration

All topology options are defined as properties. There is compiled in JAR set of properties that provide a reasonable
default values. You can pass more properties files(via CLI) to override this defaults. 

Properties have different scope that depends from used prefix. Lets show it on option `opentsdb.hosts`.
* `opentsdb.hosts` - this is `global` scope that will be used by all topologies
* `$name.opentsdb.hosts` - this is `name` scope.  $name is passed from CLI `--name` argument. Or constructed from
  topology class name if `--name` is missing.
* `defaults.statstopology.opentsdb.hosts` - this `topology` scope. In this scope, option `opentsdb.hosts` will be used 
  only by StatsTopology. Topology name `statstopology` constructed from topology class name.
  
Property lookup done in following order: `name scope`, `topology scope`. `global scope`. First found is used. This 
approach allow to pass options bounded to specific name, to specific topology and unbounded/globally. 

# Developers

## Debugging Tips

A lot of message passing is done through kafka topics.

### WFM Debugging

#### Deploying the Kafka Splitter

* assuming you've built the all-in-one jar, from within `services/wfm`: 
    * option 1: ``` mvn assembly:assembly -DskipTests```
    * option 2: ``` mvn assembly:assembly```
* you can deploy the topology (with kilda running):
    ```
    storm jar target/WorkflowManager-1.0-SNAPSHOT-jar-with-dependencies.jar \
    org.openkilda.wfm.topology.event.OFEventSplitterTopology \
    --name splitter-1
    ```

### Kafka Debugging

#### Viewing Topics
One way to look at what is going on in a topic:

* Template: 
```kafka-console-consumer --bootstrap-server <kafka host:port> --topic <name of topic> --from-beginning```
* Example: 
```kafka-console-consumer --bootstrap-server localhost:9092 --topic kilda.speaker --from-beginning```

#### Producing Messages on Topics

* Example:
    ```kafka-console-producer --broker-list localhost:9092 --topic kilda.speaker```

### Storm Debugging

#### Configuring Developer Environment

* On the mac, you can use brew to install storm. That'll give you the `storm` CLI.
* Ensure you configure ~/.storm/storm.yaml so that it points to your storm cluster.
    * Normally, this is the kilda cluster.
    * The storm.yaml file should look like this at a minimum:
        ```
        nimbus.seeds: ["127.0.0.1"]
        ```
* With the CLI installed, you should be able to run the following kinds of commands:
    ```
    storm list
    storm jar <jar.file> <class> [arguments]  # ie deploy a topology
    stork kill <topology name>
    ```

#### Viewing Logs
Whereas you should be able to look at logs through the storm UI (ie localhost:8888), 
you can also look at the log files directly on the storm cluster:

* connect to the supervisor: ```docker-compose exec storm-supervisor /bin/bash```
* cd to the base of the workers ```cd /opt/storm/logs/workers-artifacts```
    * __NB: `workers-artifacts` will exist if you've deployed a topology; otherwise maybe.__

## Testing tips

### Unit tests

Running JUnit target with coverage from the IDE builds coverage and should integrate data with the sources.

The command __mvn clean package__ builds unit tests and code coverage reports for using by external apps.

Generated reports are stored in:
* ```target/jacoco``` - jacoco reports
* ```target/junit``` - junit reports
* ```target/coverage-reports``` - coverage data files

Jacoco generates a site-package in ```target/jacoco``` directory.
It could be opened in any browser (```target/jacoco/index.html```).

Junit reports could be used by external services (CI/CD for example) to represent test results.

Generated coverage data from the ```target/coverage-reports``` directory could also be opened in IDE to show the coverage.
