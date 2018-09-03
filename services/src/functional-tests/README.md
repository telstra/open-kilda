# Functional tests
This module holds Functional tests designed to be run against staging OR virtual environment.

# How to run 
### Virtual (local Kilda)
- Spawn your Kilda env locally by running
```
make build-latest 
make up-test-mode
```
- Check your `kilda.properties`. It should point to your localhost environments.  
`spring.profiles.active` should be set to `virtual`.
- Check your `topology.yaml`. This is a file which will be used to spawn a virtual
topology used by all the tests.
- You can now run tests from IDE or run  
`mvn clean test -Pfunctional`

### Hardware (Staging)
- Check your `kilda.properties`. It should point to your staging environments.  
`spring.profiles.active` should be set to `hardware`.
- Check your `topology.yaml`. It should represent your actual hardware topology.
- You can now run tests from IDE or run  
`mvn clean test -Pfunctional`

## Artifacts
* Logs - ```target/logs```
* Reports - ```target/spock-reports```

# Deployment
## Confirguration
### Topology
The tests require a network topology definition provided.
For hardware(staging) topology this definition should represent the actual state of the hardware topology
For virtual(mininet) topology this definition will serve as a guide for creating a virtual topology.

The topology definition format:
```
TBD    
```

### Kilda configuration
The tests require Kilda configuration provided. See `kilda.properties.example`

