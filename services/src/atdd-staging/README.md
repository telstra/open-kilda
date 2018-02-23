# Acceptance tests for Staging environment
This module holds the Acceptance tests designed to be run against the staging environment.

# Deployment
## Confirguration
### Topology
The tests require a network topology definition provided in a document.

The topology definition format:

```
{
   "type": "TOPOLOGY",
   "switches": [
     {
       "dpid": "00:00:00:00:00:01",
       "num_of_ports": 1,
       "links": [
         {
           "latency": 10,
           "local_port": 0,
           "peer_switch": "00:00:00:00:02",
           "peer_port": 0
         }
       ]
     },
     {
       "dpid": "00:00:00:00:00:02",
       "num_of_ports": 1,
       "links": [
         {
           "latency": 10,
           "local_port": 0,
           "peer_switch": "00:00:00:00:01",
           "peer_port": 0
         }
       ]
     }
   ]
 }
```

# Developers
## How to run 
### ATDD tests
The command __mvn clean package__ builds the executable artifact.

The following command runs the tests:

    java -cp "target/atdd-staging-1.0-SNAPSHOT.jar:target/lib/*" cucumber.api.cli.Main

Generated reports are stored in:
* ```cucumber-report.json``` - Cucumber report

### Unit tests

    mvn test

Generated reports are stored in:
* ```target/cucumber-reports``` - Cucumber reports
* ```target/surefire-reports``` - JUnit reports
* ```target/site/jacoco``` - JaCoCo / coverage reports, which can be opened in any browser (```target/site/jacoco/index.html```).

