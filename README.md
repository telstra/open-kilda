---
OPEN KILDA SDN CONTROLLER
---
[![Build Status](https://github.com/telstra/open-kilda/actions/workflows/unittest.yml/badge.svg)](https://github.com/telstra/open-kilda/actions/workflows/unittest.yml)[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=org.openkilda%3Akilda-parent&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.openkilda%3Akilda-parent)

## Introduction

OpenKilda is a Web-Scale Software-Defined Networking controller. OpenKilda is capable of manage traffic on tens of thousands of switches simultaneously, 
control millions of flows, and provide sub-second network telemetry. OpenKilda provides a variety of features, such as:
- manual and automatic management of L2 services: point-to-point flows, Y-flows, etc.;
- advanced features for some types of services: mirroring, data gathering, path pinning, etc.;
- path computation engine: calculating paths based on different cost functions;
- resilient mechanisms: quick reaction on changes on hardware level, paths recalculations, diverse flow groups to ensure back-up routes;
- parallelism: flexible distributed computation, easy-to-change configuration;
- visualized telemetry and reporting;
- REST API and GUI to configure and manage OpenKilda capabilities;
- and more!

## Important notes

### Deprecation
Since the release 1.126.2 (December 2022), single-table mode for switches became deprecated. New features will be designed
only for multi-table mode. Current features will support single table mode till July 1, 2023. After that, single table
mode support might be removed from any feature.

## How to build and deploy OpenKilda

The build process described below requires to install a number of packages. It is recommended that you build OpenKilda on a virtual machine.

### Prerequisites

The following packages are required for building OpenKilda controller:
 - Gradle 7.0+
 - Maven 3.3.9+
 - JDK 8+
 - Python 3.6+
 - Docker 19.03.3+
 - Docker Compose 1.20.0+
 - GNU Make 4.1+
 - Open vSwitch 2.9+

#### Python dependency notice

We do not recommend upgrading pip and install docker-compose using the methods described below, bypassing the packer managers. Instead, please read the documentation for installing the [pip](https://pip.pypa.io/en/stable/installation/#upgrading-pip) and the [docker-compose](https://docs.docker.com/compose/install/).


#### Dependency installation on Ubuntu 18.04 
For running a virtual environment (that is a Docker instance with the Open vSwitch service) it is required to have Linux kernel 4.18+ for OVS meters support.
The following commands will install necessary dependencies on Ubuntu 18.04:
```shell
sudo apt install maven make openjdk-8-jdk openvswitch-switch python3-pip linux-generic-hwe-18.04 tox rsync
```
```shell
sudo pip3 install --upgrade pip
sudo pip3 install docker-compose
```
#### Dependency installation on Ubuntu 20.04
The following commands will install necessary dependencies on Ubuntu 20.04:
```shell
sudo apt install maven make openjdk-8-jdk openvswitch-switch python3-pip tox rsync
```

```shell
sudo apt install maven make openjdk-8-jdk openvswitch-switch tox
```

To avoid version conflict you can install python3-pip with the official script. To do it, you need to download script:

```shell
wget https://bootstrap.pypa.io/get-pip.py
```

and then run it:


```shell
sudo python3 get-pip.py
```

After pip installation you can install Docker compose:

```shell
sudo pip3 install docker-compose
```

#### Gradle
You can either install Gradle, or use Gradle wrapper:
 - Option 1: Use Gradle wrapper. The OpenKilda repository contains an instance of Gradle Wrapper 
 which can be used straight from here without further installation.
 - Option 2: Install Gradle 7.0 or later versions - https://gradle.org/install/


#### Docker
Note that your build user needs to be a member of the docker group for the build to work. 
Do that by adding the user to /etc/groups, logging out, and logging in back again.

##### Basic installation instruction from Docker site

```shell
sudo apt-get install ca-certificates curl gnupg lsb-release
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker $USER
# re-login to apply the usermod command
```

#### Maven
You also need to increase the maven RAM limit at least up to 1G.

```shell
export MAVEN_OPTS="-Xmx1g -XX:MaxPermSize=128m"
```

### How to build OpenKilda Controller

From the base directory, execute the following command:

```shell
make build-stable
```

Note that additional Ubuntu packages will be installed as a part of the build process.

### How to clean OpenKilda Controller

From the base directory, execute the following command:

```shell
make clean
```

### How to run OpenKilda Controller

__NB: To run OpenKilda, you should have built it already (see the previous section).__
This is particularly important because docker-compose will expect that some specific containers already exist.

From the base directory, execute the following command:

```shell
make up-stable
```

### How to create a virtual topology for test
```shell
make test-topology
```

### How to run OpenKilda Controller in blue-green mode

Blue-green mode is an implementation of zero downtime feature. In this mode, you have
two versions of OpenKilda running: the old one (blue) and the new one (green).
Then, at some point in time switch blue to green.

__First of all you need to build two sets of images.__

To build blue version of OpenKilda you need to run:
```shell
make build-stable
```

To build a green version of OpenKilda you need to run:
```shell
make build-latest
```

These two commands build images with tags `stable` and `latest`.
These tags will be used to run OpenKilda in blue mode (from stable images)
or in green mode (for latest images).  

__There are 3 new commands to run OpenKilda in blue-green mode:__ 

The following command runs OpenKilda in blue mode from stable images.
It starts all common components like Zookeeper, database, Kafka, etc.  
```shell
make up-stable
```

The next command starts the green version of OpenKilda from the latest images.
Common components wouldn't be restarted (we started them using the previous command).
Floodlight 1 wouldn't be restarted; Floodlight 1 will stay on blue mode.   
Only Floodlight 2 will be restarted.
```shell
make up-green
```

The next command is used to test rollbacks. It runs stable components in blue mode.
The difference with `make up-stable` is that this command wouldn't start common components
(like Zookeeper, Kafka, etc) and Floodlight 2 (it stays in green mode). 

```shell
make up-blue
``` 

### How to debug OpenKilda Controller components

An important aspect of troubleshooting errors and problems in your code is to avoid them in the first place. It's not
always that easy, so we should have reliable tools that help in resolving problems. Adding any diagnostic code may be helpful, but there are more
convenient ways. Just a few configuration changes will allow us to use a debugging toolkit.
As an example, let's take the northbound component. This is a simple REST application providing the interface for interaction
with switches, links, flows, features, health-check controllers, and so on. The first thing that we need to do is to add

```
"-agentlib:jdwp=transport=dt_socket,address=50505,suspend=n,server=y"
```

to the ```CMD``` block in ```docker/northbound/Dockerfile```, where ```50505``` is any arbitrary port we’ll be using to connect a debugger.
The final file will be similar to the following:

```dockerfile
ARG base_image=kilda/base-ubuntu
FROM ${base_image}

ADD BUILD/northbound/libs/northbound.jar /app/
WORKDIR /app
CMD ["java", "-XX:+PrintFlagsFinal", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-agentlib:jdwp=transport=dt_socket,address=50505,suspend=n,server=y", "-jar", "northbound.jar"]
```

Since debugging is done over the network, that also means we need to expose that port in Docker. For that purpose we need
to add  ```"50505:50505"``` to the northbound the ```ports``` block in ```docker-compose.yml```: 

```
northbound:
  container_name: openkilda-northbound
  build:
    context: docker
    dockerfile: northbound/Dockerfile
  ports:
    - '8088:8080'
    - '50505:50505'
  ...
```

After making these changes, we need to configure a remote debugging in a debugger. For example, in IntelliJ IDEA: navigate to ```Edit Configurations -> Remote```
and set up the debug port as ```50505```. For more details, please see the documentation of a particular debugger application.

Next, we just run ```docker-compose up```. If everything above was done correctly you must see:

```
"java -agentlib:jdwp=transport=dt_socket,address=50505,suspend=n,server=y -jar northbound.jar"
```

in the command column for the open-kilda_northbound. To see the status of the northbound container, we can use the command ```docker ps -a --no-trunc | grep northbound```.
Also, we can check the log record in open-kilda_northbound logs. If the debugger in this container is ready, the following entry will be present in the log:
```
Listening for transport dt_socket at address: 50505
```

Now we can run the debugger. The console log in the debugger should contain the following message:

```
Connected to the target VM, address: 'localhost:50505', transport: 'socket'
```

To check how debugging works we need to:
- set up a breakpoint,
- make a call to execute some functionality.

In some cases, we would like to debug two or more components that interact with each other. Suppose we have two components working under Docker 
and one of them doesn't belong to us and provided as a library. The typical case: WorkflowManager (further WFM) and Apache Storm.
The approach that is going to be used is almost the same as for northbound but there are nuances.
First of all, we need to check which version of Storm is used in OpenKilda Controller. For that open ```docker/storm/Dockerfile```
and find the version of Storm. In our case, the Storm version is ```1.1.0```. To be able to debug Storm we have to clone
the sources from the GitHub repo ```https://github.com/apache/storm.git``` and switch to the release ```1.1.0```.
```git checkout -b 1.1.0 e40d213```. Information about releases can be found here ```https://github.com/apache/storm/releases/```

Then, go to ```docker/wfm/Dockerfile``` and add ```ENV STORM_JAR_JVM_OPTS "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=50506"```
The final file will look similar to the following:

```dockerfile
ARG base_image=kilda/storm:latest
FROM ${base_image}

ENV STORM_JAR_JVM_OPTS "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=50506"
...
```

Next, it only remains to add the port ```50506``` in the WFM container section in the dockerfile as in the example below:

```
wfm:
  container_name: wfm
  build:
    context: docker
    dockerfile: wfm/Dockerfile
  ...
  ports:
    - "50506:50506"
```

After executing ```docker-compose up``` you should see the following log record ```Listening for transport dt_socket at address: 50506```
in the WFM logs.

Now, after we configure the remote debugger to connect to the port ```50506```, we'll be able to debug both components: WFM and Storm.
For example, in IntelliJ IDEA: navigate to ```Edit Configurations -> Remote``` and set up the debug port as ```50506```.
For more details, please see the documentation of a particular debugger application.

In order to debug a topology, for example, ```NetworkTopology```: 
create (or open if already exists) ```NetworkTopology.main``` application debug configuration and add ```--local``` to Program arguments, 
execute ```docker-compose up``` and run in the debug mode ```NetworkTopology.main```.

### How to run tests
Please refer to the [Testing](https://github.com/telstra/open-kilda/wiki/Testing) section on our Wiki.

### How to build / test locally without containers

Start with the following:

```shell
make unit
```

From there, you can go to specific projects to build / develop / unit test.
Just follow the _make unit_ trail.  Most projects have a gradle ```build``` or maven ```target```.

### How to build / test key use cases

Look in the `docker/hacks/usecase` directory. You'll find several makefiles that will assist
with the development and testing of that use case.

As an example, you can execute the following command for more information on the __network
discovery__ use case:

```shell
make -f docker/hacks/usecase/network.disco.make help
```
or
```shell
cd docker/hacks/usecase
make -f network.disco.make help
```


### How to use a VM to do development

VirtualBox and Vagrant are popular options for creating VMs.
A VM may be your best bet for doing development with OpenKilda.
There are a set of files in the source tree that will facilitate this.

* __NB1: Ensure you have VirtualBox and Vagrant installed and on the path__
* __NB2: It is recommended to clone OpenKilda again, to ensure there are no any write permission issues
    between the guest VM and the host.__

Steps:

1. From the root directory, look at the Vagrantfile; feel free to change its parameters.
2. `vagrant up`: create the VM; it'll be running after this step.
3. `vagrant ssh`: this will log you into the vm.
4. `ssh-keygen -t rsa -C "your_email@example.com"`: you'll use this for GitHub.
5. Add the ~/.ssh/id-rsa.pub key to your GitHub account so that you can clone OpenKilda.
6. Clone and Build
```
# NB: Instead of putting it in vm-dev, you can use /vagrant/vm-dev
#     This has the added benefit that the code will appear outside of the VM
#     because /vagrant is a shared directory with the same directory on the host where the Vagrantfile is located.
git clone git@github.com:<your_github_account>/open-kilda.git vm-dev
cd vm-dev
git checkout mvp1rc
make build-base
docker-compose build
make unit
make up-test-mode
```

### How to use confd for config/properties templating

We have confd for managing config/properties files from templates. Confd configs, templates and variable file stored in confd/ folder.
`confd/conf.d/*.toml`: files with the description on how to process templates (src. path, dst.path.... etc).
`confd/templates/*/*.tmpl`: templates in the go-template format.
`confd/vars/main.yaml`: a file with all variables to be substituted in templates.

#### How can I add a new template

1. Create and place the template file to `confd/templates/` directory.
2. Create and place the template description in `confd/conf.d/` directory.
3. Change vars in `confd/main.yaml` if needed.
4. Execute: `make update-props-dryrun` for verifying that the templates can be processed.
5. Execute: `make update-props` for applying the templates.

#### How to use a stand-alone OrientDB server?
Let's suppose, you have an OrientDB server, and you want to use it instead of the dockerized OrientDB.
You can add OrientDB endpoints to `confd/vars/main.yaml` and create properties template for services which use OrientDB:

`confd/conf.d/base-storm-topology.topologies.toml`:
```toml
[template]
src = "base-storm-topology/topology.properties.tmpl"
dest = "src-java/base-topology/base-storm-topology/src/release/resources/topology.properties"
keys = [ "/" ]
mode = "0644"
```

`confd/vars/main.yaml`:
```yaml
kilda_orientdb_hosts: "odb1.pendev,odb2.pendev,odb3.pendev"
kilda_orientdb_hosts_single: "odb1.pendev"
kilda_orientdb_user: "kilda"
kilda_orientdb_password: "kilda"
```

`confd/templates/base-storm-topology/topology.properties.tmpl`
```
...
{{if not (exists "/single_orientdb")}}
orientdb.url=remote:{{ getv "/kilda_orientdb_hosts" }}/{{ getv "/kilda_orientdb_database" }}
{{else}}
orientdb.url=remote:{{ getv "/kilda_orientdb_hosts_single" }}/{{ getv "/kilda_orientdb_database" }}
{{end}}
orientdb.user = {{ getv "/kilda_orientdb_user" }}
orientdb.password = {{ getv "/kilda_orientdb_password" }}
...
```

In this example, we will generate the file ```src-java/base-topology/base-storm-topology/src/release/resources/topology.properties```
from the template ```confd/templates/base-storm-topology/topology.properties.tmpl```
