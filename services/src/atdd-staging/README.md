# Acceptance tests for Staging environment
This module holds the Acceptance tests designed to be run against the staging environment.

# Deployment
## Confirguration
### Topology
The tests require a network topology definition provided.

The topology definition format:
```
{
    "nodes": [
        {
            "name": "00:00:00:00:00:01",
            "outgoing_relationships": [
                "00:00:00:00:00:02"
            ]
        },
        {
            "name": "00:00:00:00:00:02",
            "outgoing_relationships": [
                "00:00:00:00:00:01"
            ]
        }
    ]
}
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
### ATDD tests
The command __mvn clean package__ builds the executable artifact.

The following command runs the tests:

    java -cp "target/atdd-staging-1.0-SNAPSHOT.jar:target/lib/*" cucumber.api.cli.Main

Generated reports are stored in:
* ```cucumber-reports``` - Cucumber reports

### Unit tests

    mvn test

Generated reports are stored in:
* ```target/cucumber-reports``` - Cucumber reports
* ```target/surefire-reports``` - JUnit reports
* ```target/site/jacoco``` - JaCoCo / coverage reports, which can be opened in any browser (```target/site/jacoco/index.html```).

