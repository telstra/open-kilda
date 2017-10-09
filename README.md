---
KILDA CONTROLLER
---

## Introduction

### How to Build Kilda Controller

From the base directory run these commands:

1. ```make build-base```
2. ```docker-compose build```

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
