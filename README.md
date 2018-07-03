---
KILDA CONTROLLER
---

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

### How to Build Kilda Controller

From the base directory run these commands:

1. ```make build-base```
2. ```make compile```
3. ```docker-compose build```

### How to run Kilda

__NB: To run Kilda, you should have built it already (ie the previous section).__
This is particularly important because docker-compose will expect some of the
containers to already exist.

From the base directory run these commands:

1. ```docker-compose up```

or

1. ```make up-test-mode```

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

### How to use ansible for config/properties templating

We have ansible playbook for managing config/properties files from templates which is 
placed in templates/ folder.
This playbook contains three files with variables.
One for default options (like endpoints, all defaults should be here): templates/defaults/main.yaml
and second one for paths of template and destinations: templates/vars/path.yaml
and last one for overriding defaults: templates/vars/vars.yaml

For now default options are the same which was before this playbook implementation and suitable for
docker-compose build.

#### How should I add new template

Pre-requirements:
You have to add `localhost ansible_connection=local` to /etc/ansible/hosts

1. create and place jinja template file to templates/templates/ folder
2. add it to templates/vars/path.yaml
3. change (if needed) vars in templates/defaults/main.yaml
4. run: `make update-props-dryrun` for checking that template behaviour is ok
5. run: `make update-props` for applying templates

#### How should I change/add/override default var values
1. Add new vars, edit: templates/defaults/main.yaml
2. If you need override default vars, edit: templates/vars/vars.yaml
2. run: `make update-props-dryrun` for checking that template behaviour is ok
3. run: `make update-props` for applying templates

#### Common use cases
An exmaplte, you already have neo4j server, and want to use it instead of dockerized neo4j.
You can add neo4j endpoints to templates/defaults/main.yaml and create properties template for
services which use neo4j:

templates/defaults/main.yaml:
```
neo4j_host: "neo4j"
neo4j_user: "neo4j"
neo4j_password: "temppass"
```

templates/templates/topology-engine/topology_engine.properties.j2:
```
[kafka]
consumer.group=python-tpe-tl-consumer
topology.topic={{ kafka_topic }}
bootstrap.servers={{ kafka_hosts }}

[gevent]
worker.pool.size=1024

[zookeeper]
hosts={{ zookeeper_hosts }}

[neo4j]
host={{ neo4j_host }}
user={{ neo4j_user }}
pass={{ neo4j_password }}
```

templates/vars/path.yaml:
```
  - topology_engine_properties:
    dest: services/topology-engine/queue-engine/topology_engine.properties
    tmpl: templates/topology-engine/topology_engine.properties.j2
```

In this example we will generate file services/topology-engine/queue-engine/topology_engine.properties 
from template templates/topology-engine/topology_engine.properties.j2

### How enable travis CI
Someone with admin rights should log in using github account to https://travis-ci.org and on the page 
https://travis-ci.org/profile/telstra activate telstra/open-kilda repository.
All configurations for travis are located in .travis.yml. For adding new scripts you should create new line under script parameter.
```
script:
- make run-test
- new command
```
