# WFM - WorkFlow Manager

This project holds the Storm topologies that are used to implement
the workflow management aspects of Kilda.

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
    org.bitbucket.openkilda.wfm.OFEventSplitterTopology \
    splitter-1
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
    storm jar ..  # ie deploy a topology
    stork kill <topology name>
    ```

#### Viewing Logs
Whereas you should be able to look at logs through the storm UI (ie localhost:8888), 
you can also look at the log files directly on the storm cluster:

* connect to the supervisor: ```docker-compose exec storm_supervisor /bin/bash```
* cd to the base of the workers ```cd /opt/storm/logs/workers-artifacts```
    * __NB: `workers-artifacts` will exist if you've deployed a topology; otherwise maybe.__

## Testing tips

### Unit tests

Running JUnit target with coverage from the IDE builds coverage and should integrate data with the sources.

The command __mvn clean package__ builds unit tests and code coverage reports for using by external apps.

Generated reports are stored in:
* ```reports/jacoco``` - jacoco reports
* ```reports/junit``` - junit reports
* ```target/coverage-reports``` - coverage data files

Jacoco generates a site-package in ```reports/jacoco``` directory.
It could be opened in any browser (```reports/jacoco/index.html```).

Junit reports could be used by external services (CI/CD for example) to represent test results.

Generated coverage data from the ```target/coverage-reports``` directory could also be opened in IDE to show the coverage.
