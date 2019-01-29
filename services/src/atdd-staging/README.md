# Acceptance tests for Staging environment
This module holds the Acceptance tests designed to be run against the staging environment.

# Deployment
## Confirguration
### Topology
The tests require a network topology definition provided.

The topology definition format:
```
switches:
    - name: sw1
      dp_id: 00:00:00:00:00:01
      of_version: OF_13
      status: active
      out_ports:
        - port: 10
          vlan_range: 1..10, 22, 35..40

    - name: sw2
      dp_id: 00:00:00:00:00:02
      of_version: OF_13
      status: skip

    - name: sw3
      dp_id: 00:00:00:00:00:03
      of_version: OF_13
      status: active
      out_ports:
        - port: 12
          vlan_range: 1..10

isls:
    - src_switch: sw1
      src_port: 1
      dst_switch: sw3
      dst_port: 1
      max_bandwidth: 10000

    - src_switch: sw1
      src_port: 2
      dst_switch: sw2
      dst_port: 2
      max_bandwidth: 10000

    - src_switch: sw2
      src_port: 3
      dst_switch: sw3
      dst_port: 3
      max_bandwidth: 10000

traffgens:
    - name: tg1
      control_endpoint: http://192.168.0.1:80/
      switch: sw1
      switch_port: 10
      status: active

    - name: tg2
      control_endpoint: http://192.168.0.2:80/
      switch: sw3
      switch_port: 12
      status: active
      
traffgen_config:
    address_pool_base: 192.168.1.0
    address_pool_prefix_len: 20      
```

### Kilda configuration
The tests require Kilda configuration provided in the format:
```
northbound.endpoint=http://localhost:8088
northbound.username=kilda
northbound.password=kilda

topology-engine-rest.endpoint=http://localhost:80
topology-engine-rest.username=kilda
topology-engine-rest.password=kilda

floodlight.endpoint=http://localhost:8081
floodlight.username=kilda
floodlight.password=kilda
```

# Developers
## How to run 
### ATDD-Staging tests
The command __mvn clean package__ builds the executable artifact.

The following command runs the tests:

    java -cp "target/atdd-staging-1.0-SNAPSHOT.jar:target/lib/*" \
        -Dkilda.config.file=kilda.properties \
        -Dtopology.definition.file=topology.yaml \
        cucumber.api.cli.Main

Generated reports are stored in:
* ```cucumber-reports``` - Cucumber reports

### Unit tests

    mvn test

Generated reports are stored in:
* ```target/cucumber-reports``` - Cucumber reports
* ```target/surefire-reports``` - JUnit reports
* ```target/site/jacoco``` - JaCoCo / coverage reports, which can be opened in any browser (```target/site/jacoco/index.html```).

## Importing the project into IDE

Lombok

We use this tool to remove redundant boilerplate code you often find in data classes such as getters, setters, all args constructors, etc.


To use, go to Android Studio -> Preferences -> Plugins -> Browse repositories... -> Search Lombok Plugin -> Install Plugin
More on Lombok 