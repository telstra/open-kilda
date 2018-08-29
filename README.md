---
KILDA CONTROLLER
---
[![Build Status](https://travis-ci.org/telstra/open-kilda.svg?branch=develop)](https://travis-ci.org/telstra/open-kilda)[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=org.openkilda%3Akilda-parent&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.openkilda%3Akilda-parent)

## Introduction

### Prerequisites
You need to rise maven RAM limit at least up to 1G.

```export MAVEN_OPTS="-Xmx1g -XX:MaxPermSize=128m"```

Some build steps require Python2. If Python3 is default in your system please use `virtualenv`. Ensure that you have `tox` installed:

```
virtualenv --python=python2 .venv
. .venv/bin/activate
pip install tox
```

Also, don't forget to install ansible. This tool is used for creating config/properties files from templates. To install it execute the following command:

```
pip install ansible
```

### How to Build Kilda Controller

From the base directory run the following command:

```
make build-latest
```

### How to run Kilda

__NB: To run Kilda, you should have built it already (ie the previous section).__
This is particularly important because docker-compose will expect some of the
containers to already exist.

From the base directory run these commands:

1. ```docker-compose up```

or

1. ```make up-test-mode```

### How to run ATDD

Steps:
1. Build Kilda controller. See *"How to Build Kilda Controller"* section.
2. Run Kilda controller in *"test mode"*. ```make up-test-mode```
3. Update your /etc/hosts file. Replace ```127.0.0.1 localhost``` to 
   ```127.0.0.1    localhost kafka.pendev```
4. Run ATDD using ```make atdd``` command.

### How to run floodlight-modules locally

From the base directory run these commands:

1. ```make build-floodlight```
2. ```make run-floodlight```

### How to build / test locally without containers

Start with the following

1. '''make unit'''

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
cache.topic={{ getv "/kilda_kafka_topic_topo_cache" }}
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
