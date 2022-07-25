# Project Structure Conventions
An overview how the project is structured

## OpenKilda repository has the following top-level directory layout:
```
  │
  ├── confd                       # Directory with Confd templates
  │    └...
  │
  ├── docker                      # Directory with Dockerfile(s)
  │    ├── base                   # Base Docker images
  │    │
  │    ├── db-migration           # Migration scripts for OrientDB
  │    ├── db-mysql-migration     # Migration scripts for MySql
  │    ├── floodlight-modules     # Floodlight along with Floodlight-Modules component
  │    ├── grpc-service           # GRPC service
  │    ├── northbound             # Northbound service
  │    ├── server42               # Server42 Control and Stats components
  │    ├── wfm                    # WFM (Storm Topologies) submitter
  │    │
  │    ├── elasticsearch          # Elasticseach (applicable to all-in-one environemnts)
  │    ├── hbase                  # HBase (applicable to all-in-one environemnts)
  │    ├── kafka                  # Kafka (applicable to all-in-one environemnts)
  │    ├── lab-service            # KildaLab and Traffgen (applicable to all-in-one environemnts)
  │    ├── logstash               # Logstash (applicable to all-in-one environemnts)
  │    ├── opentsdb               # OpenTSDB (applicable to all-in-one environemnts)
  │    ├── orientdb               # OrientDB (applicable to all-in-one environemnts)
  │    ├── zookeeper              # Zookeeper (applicable to all-in-one environemnts)
  │    └...
  │
  ├── docs
  │    ├── design                 # Project and feature design documentation
  │    ├── guidelines             # OpenKilda developer's guidelines and conventions
  │    └...
  │  
  ├── src-cpp                     # Source code of C++ -based components
  │    ├── server42               # Server42 source code.
  │    │    ├── README.md
  │    │    └...
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
  │    ├── testing                     # Functional, performance and other tests
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
  ├── generated.mk                # Generated part of the Makefile (Confd template is used)
  │  
  ├── docker-compose.yml
  │  
  ├── CHANGELOG.md                # A log of made changes and release notes
  ├── LICENSE.txt
  └── README.md
```

## Directory Overview
### Source files
Source files of system components / modules are placed under `src-java`, `src-gui`, `src-cpp` and `src-python` in a corresponding sub-folder (e.g. `src-java/northbound-service`). 

The structure of component folders should correspond requirements or best-practices of the tool used to build it.

Confd and Docker contains templates and build scripts for deployable components.

### Shared Components
If a system component / module is shared among others, it should be placed under the root of `src-java` or `src-python` (e.g. `src-java/kilda-configuration`). 

### Automated tests
Unit tests are located next to the source code according to the requirements or best-practices of the tool used to build the component.

Automated functional and performance tests are placed into the `testing` folder.

### Documentation
System-wide and component specific documentation is placed under the `docs` folder. 

### Database migration scripts
Migration scripts are grouped by target database (OrientDB, MySQL) and placed under the `docker` folder (e.g. `docker/db-migration`)..

### Tools and utilities
See the `tools` folder.
