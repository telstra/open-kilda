# Project Structure Conventions
An overview how the project is structured

## OpenKilda repository has the following top-level directory layout:
```
  │
  ├── confd                       # Directory with Confd templates
  │   ├── conf.d                  # Directory with .toml templates for different components  
  │   ├── templates               # Directory with .tmpl  templates for different components
  │   └── vars
  │       ├── blue-mode.yaml
  │       ├── docker-compose.yaml
  │       ├── green-mode.yaml
  │       ├── main.yaml
  │       └── test-vars.yaml
  │  
  ├── docker
  │   ├── base                   # Base Docker images
  │   ├── confd
  │   ├── db-migration           # Migration scripts for OrientDB
  │   ├── db-mysql-migration     # Migration scripts for MySql
  │   ├── elasticsearch          # Elasticseach (applicable to all-in-one environemnts)
  │   ├── floodlight-modules     # Floodlight along with Floodlight-Modules component
  │   ├── grpc-service           # GRPC service
  │   ├── grpc-stub
  │   ├── hbase                  # HBase (applicable to all-in-one environemnts)
  │   ├── kafka                  # Kafka (applicable to all-in-one environemnts)
  │   ├── lab-service            # KildaLab and Traffgen (applicable to all-in-one environemnts)
  │   ├── lock-keeper
  │   ├── logstash               # Logstash (applicable to all-in-one environemnts)
  │   ├── northbound             # Northbound service
  │   ├── opentsdb               # OpenTSDB (applicable to all-in-one environemnts)
  │   ├── orientdb               # OrientDB (applicable to all-in-one environemnts)
  │   ├── server42               # Server42 Control and Stats components
  │   ├── storm
  │   ├── wfm                    # WFM (Storm Topologies) submitter
  │   └── zookeeper              # Zookeeper (applicable to all-in-one environemnts)
  │
  ├── docs
  │   ├── contrib                # How to contribute agreements
  │   ├── design                 # Project and feature design documentation
  │   ├── gui
  │   └── guidelines             # OpenKilda developer's guidelines and conventions
  │  
  ├── src-cpp                    # Source code of C++ -based components
  │   └── server42               # Server42 source code.
  │  
  ├── src-gui                    # OpenKilda GUI source code  
  │  
  ├── src-java                         # Source code of Java-based components.
  │   │                                # Files:
  │   ├── build.gradle
  │   ├── settings.gradle
  │   ├── lombok.config
  │   │
  │   │                                # Directories:
  
  │   ├── blue-green                   # Zero downtime switching to other version of OpenKilda.
  │   ├── checkstyle                   # Project-wide checkstyle plugin configuration.
  │   │
  │   │                                # Storm topologies:
  │   ├── base-topology                #
  │   ├── connecteddevices-topology    #
  │   ├── floodlightrouter-topology    #
  │   ├── floodlight-service           #
  │   ├── flowhs-topology              #
  │   ├── flowmonitoring-topology      #
  │   ├── history-topology             #
  │   ├── isllatency-topology          #
  │   ├── nbworker-topology            #
  │   ├── network-topology             #
  │   ├── opentsdb-topology            #
  │   ├── ping-topology                #
  │   ├── portstate-topology           #
  │   ├── reroute-topology             #
  │   ├── stats-topology               #
  │   ├── swmanager-topology           #
  │   │
  │   │                                # OpenKilda business logic:
  │   ├── kilda-configuration          # Configuration of messaging systems: Kafka, Zookeeper.
  │   ├── kilda-model                  # Classes representing business objects such as flows, paths, meters, and so on.
  │   ├── kilda-pce                    # Path Computation Engine.
  │   ├── kilda-persistence-api        # Persistence layer implementation: Transation Manager, Repositories, Contexts.  
  │   ├── kilda-persistence-hibernate  #
  │   ├── kilda-persistence-orientdb   # OrientDB repositories and factories. 
  │   ├── kilda-persistence-tinkerpop  # Ferma repositories and configuration.
  │   ├── kilda-reporting              # Dashboard logger.
  │   ├── kilda-utils                  # 
  │   │   ├── janitor                  # FlowJanitor: helps to discover and manipulate with flows on a working system.
  │   │   └── stubs                    # 
  │   ├── northbound-service           # Service allowing to communicate with OpenKilda via REST API.
  │   │   ├── northbound               # REST controllers and service classes.
  │   │   └── northbound-api           # Classes representing Request and Responses.
  │   │                                #
  │   │                                # OpenKilda business logic:
  │   ├── grpc-speaker                 #
  │   ├── projectfloodlight            #
  │   ├── rule-manager                 #
  │   ├── server42                     #
  │   │
  │   └── testing                      # Functional, performance, and other tests.
  │       ├── functional-tests         #
  │       ├── performance-tests        #
  │       └── test-library             #
  │
  ├── src-python                  # Source code of Python-based components.
  │   ├── grpc-stub
  │   ├── lab-service            # Lab-service's source code.
  │   └── lock-keeper            # Lock Keeper's source code.
  │     
  ├── tools                       # Various tools and utilities used in the development and support process.
  │    └...
  │  
  ├── Makefile                    # Script for local builds.
  ├── generated.mk                # Generated part of the Makefile (Confd template is used).
  │  
  ├── CHANGELOG.md                # A log of made changes and release notes.
  ├── LICENSE.txt
  └── README.md
```

## Directory Overview
### Source files
Source files of system components are placed under `src-java`, `src-gui`, `src-cpp` and `src-python` in a corresponding sub-folder (e.g. `src-java/northbound-service`). 

The structure of component folders should correspond requirements or best-practices of the tool used to build it.

Confd and Docker contains templates and build scripts for deployable components.

### Shared Components
If a system component is shared among others, it should be placed under the root of `src-java` or `src-python` (e.g. `src-java/kilda-configuration`). 

### Automated tests
Unit tests are located next to the source code according to the requirements or best-practices of the tool used to build the component.

Automated functional and performance tests are placed into the `testing` folder.

### Documentation
System-wide and component-specific documentation is placed under the `docs` folder. 

### Database migration scripts
Migration scripts are grouped by target database (OrientDB, MySQL) and placed under the `docker` folder (e.g. `docker/db-migration`).

### Tools and utilities
Project-wise tools are places under `/tools/`. Component-specific tools are places under their source root directory.
For example, `FlowJanitor` is used only in the context of OpenKilda and is placed under the `src-java/kilda-utils`.
