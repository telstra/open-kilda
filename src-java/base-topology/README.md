# WFM (WorkFlow Manager) - Base Topology

This subproject holds the base classes for Storm topologies that are used to implement the workflow management 
aspects of OpenKilda.

# Deployment

To start a specific topology in `local` mode:
```bash
java -cp TOPOLOGY_JAR.FILE:build/dependency-jars/* CLASS --local arguments...
```

To submit the topology into Storm:
```bash
storm jar TOPOLOGY_JAR.FILE class --jars "DEPENDENCY_JAR1.FILE,DEPENDENCY_JAR2.FILE" arguments...
```

## Topology CLI arguments

You can specify the following arguments for topology submit commands:
* `--name=NAME` - override the topology name. When this parameter is absent, the topology name is constructed using 
a file name of that topology.
* `--local` - use the topology in "local" mode: submit topology into `org.apache.storm.LocalCluster`.
* `--local-execution-time=TIME` - used only in combination with `--local`. Define how long (in seconds) topology will be
  running. `TIME` argument parsed as float number.
* `CONFIG` - a path to the properties file. These properties will override compiled in properties. You can pass more than one
  file; each next file overrides properties defined in the previous files. 

## Configuration

All topology options are defined as properties. There is a set of properties compiled in JAR that provides default values.
You can pass more properties files using CLI to override these defaults. 

Properties have different scope that depends on used prefix. Let's take `opentsdb.hosts` as an example:
* `opentsdb.hosts` - this is the `global` scope that will be used by all topologies
* `$name.opentsdb.hosts` - this is a `name` scope.  $name is passed from CLI `--name` argument. Or constructed from
  topology class name if `--name` is missing.
* `defaults.statstopology.opentsdb.hosts` - this is a `topology` scope. In this scope, option `opentsdb.hosts` will be used 
  only by StatsTopology. Topology name `statstopology` constructed from topology class name.
  
Properties lookup is done in the following order: `name scope`, `topology scope`. `global scope`. When a property is found,
lookup is finished. This approach allows to pass options bounded to a specific name, to a specific topology, or used globally. 

# Developers

## Debugging Tips

OpenKilda primarily uses Kafka topics as a messaging carrier.

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

* On Mac, you can use `brew` to install Storm. That'll give you the `storm` CLI.
* Ensure you configure ~/.storm/storm.yaml so that it points to your Storm cluster.
    * Normally, this is the OpenKilda cluster.
    * The storm.yaml file must contain the following line:
        ```
        nimbus.seeds: ["127.0.0.1"]
        ```
* After the Storm CLI is installed, you should be able to run the following kinds of commands:
    ```
    storm list
    storm jar <jar.file> <class> [arguments]  # ie deploy a topology
    stork kill <topology name>
    ```

#### Viewing Logs
It is possible to access Storm logs using Storm UI (by default localhost:8888/index.html). 
You can also look at the log files directly on the Storm cluster:

* connect to the supervisor: ```docker-compose exec storm-supervisor /bin/bash```
* navigate to logs directory (`workers-artifacts` is created when deploying a topology): ```cd /opt/storm/logs/workers-artifacts```
  

## Testing tips

### Unit tests

Running JUnit target with coverage from the IDE builds coverage and integrates data with the sources.

There are several gradle tasks in different modules that execute tests and create a report:
* `test` executes all unit test in this module and creates a report available at `build/reports/tests/index.html`
* `jacocoTestReport` executes all unit tests in this module with coverage and creates a coverage report available at:
`build/reports/jacoco/test/html/jacocoTestReport.html`
* `check`
* `jacocoTestCoverageVerification`

Junit reports could be used by external services (CI/CD for example) to represent test results.

Generated coverage data from the ```target/coverage-reports``` directory could be opened in an IDE to show the coverage.
