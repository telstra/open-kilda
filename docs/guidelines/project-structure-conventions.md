# Project Structure Conventions
An overview how the project is structured

## OpenKilda repository has the following top-level directory layout:
```
  │
  ├── confd                       # Directory with Confd templates
  │    └...
  │
  ├── docker                      # Directory with Dockerfile(s)
  │    ├── base
  │    ├── floodlight-modules     # Dockerfile for Floodlight-Modules component
  │    ├── kafka                  # Dockerfile for Kafka image (applicable to all-in-one environemnts)
  │    ├── neo4j                  # Dockerfile for Neo4j image (applicable to all-in-one environemnts)
  │    ├── network-topology       # Dockerfile for Network Topology submitter
  │    ├── flow-topology          # Dockerfile for Flow Topology submitter
  │    └...
  │
  ├── docs
  │    ├── design                 # Project and feature design documentation
  │    ├── guidelines             # OpenKilda developer's guidelines and conventions
  │    └...
  │  
  ├── src-gui                     # OpenKilda GUI source code
  │    ├── README.md
  │    └...
  │  
  ├── src-java                    # Source code of Java-based components
  │    ├── build.gradle
  │    ├── settings.gradle
  │    ├── README.md
  │    │
  │    ├── checkstyle             # Project-wide checkstyle plugin configuration
  │    ├── kilda-configuration    
  │    │    ├── build.gradle
  │    │    ├── README.md
  │    │    └── src
  │    │         ├── checkstyle   # Module specific checkstyle plugin configuration
  │    │         ├── main         # Module production source code
  │    │         └── test         # Module unit tests
  │    │
  │    ├── northbound-service
  │    │    ├── README.md
  │    │    ├── northbound-api
  │    │    │    ├── build.gradle
  │    │    │    ├── README.md
  │    │    │    └── src
  │    │    │         ├── checkstyle   # Module specific checkstyle plugin configuration
  │    │    │         ├── main         # Module production source code
  │    │    │         └── test         # Module unit tests
  │    │    └── northbound
  │    │         ├── build.gradle
  │    │         ├── README.md
  │    │         └── src
  │    │              ├── checkstyle   # Module specific checkstyle plugin configuration
  │    │              ├── main         # Module production source code
  │    │              └── test         # Module unit tests
  │    │
  │    ├── base-topology               # Shared components / classes for Storm topologies 
  │    │    ├── base-messaging
  │    │    │    └...
  │    │    └── base-storm-topology
  │    │         └...
  │    │
  │    ├── network-topology            # Network topology source code
  │    │    ├── network-messaging
  │    │    │    └...
  │    │    └── network-storm-topology
  │    │         └...
  │    │
  │    ├── testing                     # Functional, ATDD and other tests
  │    │    ├── test-library
  │    │    │    └...
  │    │    ├── functional-tests
  │    │    │    └...
  │    │    └── performance-tests
  │    │         └...
  │    └...
  │  
  ├── src-python                  # Source code of Python-based components.
  │    ├── lab-service            # Lab-service's source code.
  │    │    ├── README.md
  │    │    └...
  │    │
  │    └── lock-keeper            # Lock Keeper's source code
  │         └...
  │  
  ├── tools                       # Various tools and utilities used in the development and support process
  │    └...
  │  
  ├── Makefile                    # Script for local builds
  ├── docker-compose.yml
  ├── Jenkinsfile                 # Scripts used for Continuous Integration
  ├── .travis.yml                 # Scripts used for Continuous Integration
  │  
  ├── CHANGELOG.md                # A log of made changes and release notes
  ├── LICENSE.txt
  └── README.md
```

## Directory Overview
### Source files
Source files of system components / modules are placed under `src-java` and `src-python` in a corresponding sub-folder (e.g. `src-java/northbound-service`). 

The structure of component folders should correspond requirements or best-practices of the tool used to build it.

Confd and Docker contains templates and build scripts for deployable components.

### Shared Components
If a system component / module is shared among others, it should be placed under the root of `src-java` or `src-python` (e.g. `src-java/kilda-configuration`). 

### Automated tests
Unit tests are located next to the source code according to the requirements or best-practices of the tool used to build the component.

Automated functional and ATDD tests are placed into the `testing` folder.

### Documentation
System-wide and component specific documentation is placed under the `docs` folder. 

### Database migration scripts
>TBD

### Tools and utilities
See the `tools` folder.
