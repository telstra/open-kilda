---
KILDA CONTROLLER
---
[![Build Status](https://travis-ci.org/telstra/open-kilda.svg?branch=develop)](https://travis-ci.org/telstra/open-kilda)[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=org.openkilda%3Akilda-parent&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.openkilda%3Akilda-parent)

## Introduction

### Prerequisites

The followings are required for building Kilda controller:
 - Maven 3.3.9+
 - JDK8
 - Python 2.7+
 - Python 3.5+
 - Docker Compose 1.20.0+
 - GNU Make 4.1+

You need to rise maven RAM limit at least up to 1G.

```export MAVEN_OPTS="-Xmx1g -XX:MaxPermSize=128m"```

Some build steps require Python2. If Python3 is default in your system please use `virtualenv`. Ensure that you have `tox` installed:

```
virtualenv --python=python2 .venv
. .venv/bin/activate
pip install tox
```

Also, don't forget to install confd. This tool is used for creating config/properties files from templates. To install it execute the following command:

```
wget https://github.com/kelseyhightower/confd/releases/download/v0.16.0/confd-0.16.0-linux-amd64 -O /usr/local/bin/confd
chmod +x /usr/local/bin/confd
```

### How to build Kilda Controller

From the base directory run the following command:

```
make build-latest
```

### How to clean Kilda Controller

From the base directory run the following command:

```
make clean
```

### How to run Kilda Controller

__NB: To run Kilda, you should have built it already (ie the previous section).__
This is particularly important because docker-compose will expect some of the
containers to already exist.

From the base directory run these commands:

1. ```docker-compose up```

or

1. ```make up-test-mode```

### How to debug Kilda Controller components

An important aspect of troubleshooting errors and problems in your code is to avoid them in the first place. It's not
always easy enough so we should have a reliable mechanism. Adding any diagnostic code may be helpful, but there are more
convenient ways. Just a few configuration changes and you'll be able to use a debug toolkit.
As a basis, let's take the northbound component. This is a simple REST application providing the interface for interaction
with the switch, link, flow, feature, health-check controllers. The first thing that we need to do is to add

```"-agentlib:jdwp=transport=dt_socket,address=50505,suspend=n,server=y"```

to the ```CMD``` block in ```services/src/northbound/Dockerfile```, where ```50505``` is the port we’ll use for debugging.
It can be any port, it’s up to us. The final file will be the following:

```
FROM kilda/base-ubuntu
ADD target/northbound.jar /app/
WORKDIR /app
CMD ["java","-agentlib:jdwp=transport=dt_socket,address=50505,suspend=n,server=y","-jar","northbound.jar"]
```

Since debugging is done over the network, that also means we need to expose that port in Docker. For that purpose we need
to add  ```"50505:50505"``` to the northbound ```ports``` block in ```docker-compose.yml``` as in example below.  

```
northbound:
  container_name: openkilda-northbound
  build:
    context: services/src/northbound/
  ports:
    - '8088:8080'
    - '50505:50505'
  ...
```

After making those changes we need to configure remote debug in IntelliJ Idea: navigate to ```Edit Configurations -> Remote```
and set up the debug port as ```50505```. This completes the main part of the configuration.

Next, we just run ```docker-compose up```. If everything above was done correctly you must see:

```"java -agentlib:jdwp=transport=dt_socket,address=50505,suspend=n,server=y -jar northbound.jar"```

in the command column for the open-kilda_northbound. The command ```docker ps -a --no-trunc | grep northbound``` could
be helpful. Also check open-kilda_northbound logs, the log record

```Listening for transport dt_socket at address: 50505```

must be presented.

After all these steps you just need to run the debugger. Console log should contain the following message:

```Connected to the target VM, address: 'localhost:50505', transport: 'socket'```

To check how debugging works we need to:
- set up a breakpoint;
- make a call to execute some functionality;

In some cases, we must have an approach for debugging a deploy process for a couple (or more) components that interact with each other. Let's
suppose both of them work under docker and some component doesn't belong to us and provided as a library. The typical case:
WorkflowManager (further WFM) and Storm. The approach that is going to be used is almost the same as for northbound but there are
nuances.
First of all, we need to check which version of Storm is used in Open Kilda Controller. For that open ```services/storm/Dockerfile```
and find the version of Storm. In our case, the Storm version is ```1.1.0```. To be able to debug Storm we have to clone
the sources from the GitHub repo ```https://github.com/apache/storm.git``` and switch to the release ```1.1.0```.
```git checkout -b 1.1.0 e40d213```. Information about releases can be found here ```https://github.com/apache/storm/releases/```

Then go to ```services/wfm/Dockerfile``` and add ```ENV STORM_JAR_JVM_OPTS "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=50506"```
The final file should be as in example below:

```
FROM kilda/storm:latest

ENV STORM_JAR_JVM_OPTS "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=50506"
...

```

And it only remains to add a port ```50506``` for the WFM contaner as in the example below:

```
wfm:
  container_name: wfm
  build:
    context: services/wfm
  ...
  ports:
    - "50506:50506"
```

Then we should configure remote debugging in IntelliJ Idea and the set up the debug port as ```50506```. After executing
```docker-compose up``` you should see the following log record ```Listening for transport dt_socket at address: 50506```
in the WFM logs. As soon as you see it run the debugger - you'll able to debug both components: WFM and Storm.

In order to debug a topology, for example, ```OfEventWfmTopology```, we should navigate to Maven Projects, Profiles and toggle ```local``` checkbox. Then open ```OfEventWfmTopology``` application debug configuration and add ```--local``` to Program arguments, execute ```docker-compose up``` and run in the debug mode ```OfEventWfmTopology```.

### How to run tests
Please refer to the [Testing](https://github.com/telstra/open-kilda/wiki/Testing)
section on our Wiki.

### How to run floodlight-modules locally

From the base directory run these commands:

1. ```make build-floodlight```
2. ```make run-floodlight```

### How to build / test locally without containers

Start with the following

1. ```make unit```

From there, you can go to specific projects to build / develop / unit test.
Just follow the _make unit_ trail.  Most projects have a maven target.

__NB: Several projects have a dependency on the maven parent; look at make unit__

### How to build / test key use cases

Look in the `base/hacks/usecase` directory and you'll find several makefiles that will assist
with the development and testing of that use case.

As an example, you can execute the following command for more information on the __network
discovery__ use case:

```
make -f base/hacks/usecase/network.disco.make help

# or

cd base/hacks/usecase
make -f network.disco.make help
```


### How to use a VM to do development

VirtualBox and Vagrant are popular options for creating VMs.
A VM may be your best bet for doing development with Kilda.
There are a set of files in the source tree that will facilitate this.

* __NB1: Ensure you have VirtualBox and Vagrant installed and on the path__
* __NB2: You'll re-clone kilda so that there aren't any write permission issues
    between the guest VM and the host.__

Steps:

1. From the root directory, look at the Vagrantfile; feel free to change its parameters.
2. `vagrant up` - create the VM; it'll be running after this step.
3. `vagrant ssh` - this will log you into the vm.
4. `ssh-keygen -t rsa -C "your_email@example.com"` - you'll use this for GitHub.  Press
<return> for each question; three in total.
5. Add the ~/.ssh/id-rsa.pub key to your GitHub account so that you can clone kilda
```bash
cat ~/.ssh/id_rsa.pub
```
6. Clone and Build
```
# NB: Instead of putting it in vm-dev, you can use /vagrant/vm-dev
#     This has the added benefit that the code will appear outside the VM
#     i.e. /vagrant is shared with the same directory as the Vagrantfile
git clone git@github.com:<your_github_account>/open-kilda.git vm-dev
cd vm-dev
git checkout mvp1rc
make build-base
docker-compose build
make unit
make up-test-mode
make atdd
```

### How to use confd for config/properties templating

Pre-requirements: you need confd version v0.14.0+ for processing yams/json as backend. You can download it from [official confd site](https://github.com/kelseyhightower/confd/blob/master/docs/installation.md)

We have confd for managing config/properties files from templates. Confd configs, templates and variable file stored in confd/ folder.
`confd/conf.d/*.toml` - files with desctiption how to process templates (src. path, dst.path.... etc)
`confd/templates/*/*.tmpl` - templates in go-template format
`confd/vars/main.yaml` - file with all variables substituted in templates

#### How should I add new template

1. create and place template file to confd/templates/ folder
2. create and place template description in confd/conf.d/ filder
3. change (if needed) vars in confd/main.yaml
4. run: `make update-props-dryrun` for checking that templates can be processed
5. run: `make update-props` for applying templates

#### Common use cases
An exmaplte, you already have neo4j server, and want to use it instead of dockerized neo4j. You can add neo4j endpoints to confd/vars/main.yaml and create properties template for services which use neo4j:

confd/conf.d/topology_engine.application.toml:
```
[template]
src = "topology-engine/topology_engine.ini.tmpl"
dest = "services/topology-engine/queue-engine/topology_engine.ini"
keys = [ "/" ]
mode = "0644"
```

confd/vars/main.yaml:
```
kilda_neo4j_host: "neo4j"
kilda_neo4j_user: "neo4j"
kilda_neo4j_password: "temppass"
```

confd/templates/topology-engine/topology_engine.ini.tmpl
```
[kafka]
consumer.group={{ getv "/kilda_kafka_te_consumer_group" }}
flow.topic={{ getv "/kilda_kafka_topic_flow" }}
speaker.topic={{ getv "/kilda_kafka_topic_speaker" }}
topo.eng.topic={{ getv "/kilda_kafka_topic_topo_eng" }}
northbound.topic={{ getv "/kilda_kafka_topic_northbound" }}
bootstrap.servers={{ getv "/kilda_kafka_hosts" }}
environment.naming.prefix = {{ getv "/kilda_environment_naming_prefix" }}

[gevent]
worker.pool.size={{ getv "/kilda_worker_pool_size" }}

[zookeeper]
hosts={{ getv "/kilda_zookeeper_hosts" }}

[neo4j]
host={{ getv "/kilda_neo4j_host" }}
user={{ getv "/kilda_neo4j_user" }}
pass={{ getv "/kilda_neo4j_password" }}
socket.timeout=30
```

In this example we will generate file services/topology-engine/queue-engine/topology_engine.properties
from template confd/templates/topology-engine/topology_engine.ini.tmpl

### How enable travis CI
Someone with admin rights should log in using github account to https://travis-ci.org and on the page
https://travis-ci.org/profile/telstra activate telstra/open-kilda repository.
All configurations for travis are located in .travis.yml. For adding new scripts you should create new line under script parameter.
```
script:
- make run-test
- new command
```
