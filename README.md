---
KILDA CONTROLLER
---
[![Build Status](https://github.com/telstra/open-kilda/actions/workflows/unittest.yml/badge.svg)](https://github.com/telstra/open-kilda/actions/workflows/unittest.yml)[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=org.openkilda%3Akilda-parent&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.openkilda%3Akilda-parent)

## Introduction

Note that the build process will install additional packages. It is recommended that you build on a virtual machine.

### Prerequisites

The followings are required for building Kilda controller:
 - Gradle 7.0+
 - Maven 3.3.9+
 - JDK8
 - Python 3.6+
 - Docker 19.03.3+
 - Docker Compose 1.20.0+
 - GNU Make 4.1+
 - Open vSwitch 2.9+

#### Python dependency notice

We do not recommend upgrading pip and install docker-compose using the methods described below, bypassing the packer managers. Instead, please read the documentation for installing the [pip](https://pip.pypa.io/en/stable/installation/#upgrading-pip) and the [docker-compose](https://docs.docker.com/compose/install/).


#### Dependency installation on Ubuntu 18.04 
For running virtual environment you additionally need linux kernel 4.18+ for OVS meters support. A virtual environment 
in that sentence means docker instance with open-vswitch in it. We use that for functional testing.
```
sudo apt install maven make openjdk-8-jdk openvswitch-switch python3-pip linux-generic-hwe-18.04 tox
```
```
sudo pip3 install --upgrade pip
sudo pip3 install docker-compose
```
#### Dependency installation on Ubuntu 20.04

```sudo apt install maven make openjdk-8-jdk openvswitch-switch python3-pip tox```

```sudo pip3 install docker-compose```

#### Gradle
You can either install Gradle, or use Gradle wrapper:
 - Option 1 - Use Gradle wrapper. The Kilda repository contains an instance of Gradle Wrapper 
 which can be used straight from here without further installation.
 - Option 2 - Install Gradle (ensure that you have gradle 7.0 or later) - https://gradle.org/install/


#### Docker
Note that your build user needs to be a member of the docker group for the build to work. 
Do that by adding the user to /etc/groups and logging out and back in again.

##### Basic installation instruction from Docker site

```sudo apt-get install apt-transport-https ca-certificates curl gnupg lsb-release
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io
sudo usermod -aG docker $USER
# re-login for apply usermod command
```

#### Maven
You also need to increase the maven RAM limit at least up to 1G.

```export MAVEN_OPTS="-Xmx1g -XX:MaxPermSize=128m"```


### How to build Kilda Controller

From the base directory run the following command:

```
make build-stable
```

Note that additional Ubuntu packages will be installed as part of the build process.

### How to clean Kilda Controller

From the base directory run the following command:

```
make clean
```

### How to run Kilda Controller

__NB: To run Kilda, you should have built it already (ie the previous section).__
This is particularly important because docker-compose will expect some of the
containers to already exist.

From the base directory run the following command:

```
make up-test-mode
```

### How to create a virtual topology for test
```
make test-topology
```

### How to run Kilda Controller in blue-green mode

Blue-green mode is an implementation of zero downtime feature. In this mode you
run two versions of kilda: old one(blue) and new one(green).
And switch blue to green at some moment.

__First of all you need to build two sets of images.__

To build blue version of Kilda you need to run:
```
make build-stable
```

To build green version of Kilda you need to run:
```
make build-latest
```

These two commands build images with tags `stable` and `latest`.
These tags will be used to run kilda in blue mode (from stable images)
or in green mode (for latest images).  

__There are 3 new commands to run kilda in blue-green mode:__ 

Following command run Kilda in blue mode from stable images.
Also it runs all common components like zookeeper, database, kafka, etc.  
```
make up-stable
```

Next command run green version of Kilda from the latest images.
Common components wouldn't be rerun (we started them by previous command).
Also floodligth 1 wouldn't be rerun (only floodlight 2).
Floodlight 1 will stay on blue mode.   
```
make up-green
```

Next command is used to test rollbacks. It runs stable components in blue mode.
The difference with `make up-stable` is that command wouldn't start common components
(like zookeeper, kafka, etc) and floodlight 2 (it stays in green mode). 

```
make up-blue
``` 

### How to debug Kilda Controller components

An important aspect of troubleshooting errors and problems in your code is to avoid them in the first place. It's not
always easy enough so we should have a reliable mechanism. Adding any diagnostic code may be helpful, but there are more
convenient ways. Just a few configuration changes and you'll be able to use a debug toolkit.
As a basis, let's take the northbound component. This is a simple REST application providing the interface for interaction
with the switch, link, flow, feature, health-check controllers. The first thing that we need to do is to add

```"-agentlib:jdwp=transport=dt_socket,address=50505,suspend=n,server=y"```

to the ```CMD``` block in ```docker/northbound/Dockerfile```, where ```50505``` is the port we’ll use for debugging.
It can be any port, it’s up to us. The final file will be the following:

```
ARG base_image=kilda/base-ubuntu
FROM ${base_image}

ADD BUILD/northbound/libs/northbound.jar /app/
WORKDIR /app
CMD ["java", "-XX:+PrintFlagsFinal", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-agentlib:jdwp=transport=dt_socket,address=50505,suspend=n,server=y", "-jar", "northbound.jar"]
```

Since debugging is done over the network, that also means we need to expose that port in Docker. For that purpose we need
to add  ```"50505:50505"``` to the northbound ```ports``` block in ```docker-compose.yml``` as in example below.  

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

After making those changes we need to configure remote debug in IntelliJ IDEA: navigate to ```Edit Configurations -> Remote```
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
First of all, we need to check which version of Storm is used in Open Kilda Controller. For that open ```docker/storm/Dockerfile```
and find the version of Storm. In our case, the Storm version is ```1.1.0```. To be able to debug Storm we have to clone
the sources from the GitHub repo ```https://github.com/apache/storm.git``` and switch to the release ```1.1.0```.
```git checkout -b 1.1.0 e40d213```. Information about releases can be found here ```https://github.com/apache/storm/releases/```

Then go to ```docker/wfm/Dockerfile``` and add ```ENV STORM_JAR_JVM_OPTS "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=50506"```
The final file should be as in example below:

```
ARG base_image=kilda/storm:latest
FROM ${base_image}

ENV STORM_JAR_JVM_OPTS "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=50506"
...

```

And it only remains to add a port ```50506``` for the WFM contaner as in the example below:

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

Then we should configure remote debugging in IntelliJ IDEA and the set up the debug port as ```50506```. After executing
```docker-compose up``` you should see the following log record ```Listening for transport dt_socket at address: 50506```
in the WFM logs. As soon as you see it run the debugger - you'll able to debug both components: WFM and Storm.

In order to debug a topology, for example, ```NetworkTopology```: 
create (or open if already exists) ```NetworkTopology.main``` application debug configuration and add ```--local``` to Program arguments, 
execute ```docker-compose up``` and run in the debug mode ```NetworkTopology.main```.

### How to run tests
Please refer to the [Testing](https://github.com/telstra/open-kilda/wiki/Testing)
section on our Wiki.

### How to build / test locally without containers

Start with the following

```
make unit
```

From there, you can go to specific projects to build / develop / unit test.
Just follow the _make unit_ trail.  Most projects have a gradle ```build``` or maven ```target```.

### How to build / test key use cases

Look in the `docker/hacks/usecase` directory and you'll find several makefiles that will assist
with the development and testing of that use case.

As an example, you can execute the following command for more information on the __network
discovery__ use case:

```
make -f docker/hacks/usecase/network.disco.make help
```
or
```
cd docker/hacks/usecase
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
```

### How to use confd for config/properties templating

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
An example, you already have orientdb server, and want to use it instead of dockerized orientdb.
You can add orientdb endpoints to confd/vars/main.yaml and create properties template for services which use orientdb:

confd/conf.d/base-storm-topology.topologies.toml:
```
[template]
src = "base-storm-topology/topology.properties.tmpl"
dest = "src-java/base-topology/base-storm-topology/src/release/resources/topology.properties"
keys = [ "/" ]
mode = "0644"
```

confd/vars/main.yaml:
```
kilda_orientdb_hosts: "odb1.pendev,odb2.pendev,odb3.pendev"
kilda_orientdb_hosts_single: "odb1.pendev"
kilda_orientdb_user: "kilda"
kilda_orientdb_password: "kilda"
```

confd/templates/base-storm-topology/topology.properties.tmpl
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

In this example we will generate file ```src-java/base-topology/base-storm-topology/src/release/resources/topology.properties```
from template ```confd/templates/base-storm-topology/topology.properties.tmpl```
