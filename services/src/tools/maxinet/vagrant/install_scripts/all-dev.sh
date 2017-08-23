#!/bin/bash

# ==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==
# INTRODUCTION:
# - This script will setup key underpinnings for the whole VM.
# ==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==•==

export DEBIAN_FRONTEND=noninteractive
SSH_USER=${SSH_USERNAME:-fred}


update() {
    echo "--•--•--•--•--•--•--•--•--"
    echo "--•--•--  UPDATES --•--•--"
    echo "--•--•--•--•--•--•--•--•--"
    apt-get update
    apt-get upgrade -y
}

install_networking() {
    echo "--•--•--•--•--•--•--•--•--"
    echo "--•--•- NETWORKING -•--•--"
    echo "--•--•--•--•--•--•--•--•--"
    apt-get install -y apt-utils git sudo net-tools
    apt-get install -y --no-install-recommends \
        curl \
        iproute2 \
        iputils-ping \
        net-tools \
        tcpdump \
        vim
}

install_docker(){
    echo "--•--•--•--•--•--•--•--•--"
    echo "--•--•--  DOCKER  --•--•--"
    echo "--•--•--•--•--•--•--•--•--"

    apt-get update
    apt-get install -y \
        apt-transport-https \
        ca-certificates \
        curl \
        software-properties-common

    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
    apt-key fingerprint 0EBFCD88

    add-apt-repository \
       "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
       $(lsb_release -cs) \
       stable"

    apt-get update
    apt-get install -y docker-ce
    groupadd docker
    usermod -aG docker ${SSH_USER}

    docker run hello-world
}

install_java(){
    echo "--•--•--•--•--•--•--•--•--"
    echo "--•--•--   JAVA   --•--•--"
    echo "--•--•--•--•--•--•--•--•--"

    apt-get update

    # need the following install in order to do add-apt-repository
    apt-get install -y software-properties-common python-software-properties

    export JAVA_VER=8
    export JAVA_HOME=/usr/lib/jvm/java-8-oracle

    # need to silence the "accept license"
    echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
    echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections
    add-apt-repository -y ppa:webupd8team/java

    apt-get update

    apt-get install -y oracle-java8-installer
    # to guarantee java 8 is the default .. just installing it doesn't make this guarantee
    apt-get install -y oracle-java8-set-default

    update-java-alternatives -s java-8-oracle
    echo "export JAVA_HOME=/usr/lib/jvm/java-8-oracle" >> ~/.bashrc
}

install_maven(){
    echo "--•--•--•--•--•--•--•--•--"
    echo "--•--•--  MAVEN   --•--•--"
    echo "--•--•--•--•--•--•--•--•--"

    apt-get install -y maven
    echo "export MAVEN_VERSION=3.3.9" >> ~/.bashrc
    echo "export MAVEN_HOME=/usr/share/maven" >> ~/.bashrc
}

install_python(){
    echo "--•--•--•--•--•--•--•--•--"
    echo "--•--•--  PYTHON  --•--•--"
    echo "--•--•--•--•--•--•--•--•--"

    apt-get install -y apt-utils python python-pip
    pip install --upgrade pip
    pip install docker-compose
}


install_ansible() {
    echo "--•--•--•--•--•--•--•--•--"
    echo "--•--•--  ANSIBLE --•--•--"
    echo "--•--•--•--•--•--•--•--•--"

    apt-get install -y ansible aptitude
    # Patch config file if necessary
    grep "localhost ansible_connection=local" /etc/ansible/hosts >/dev/null
    if [ $? -ne 0 ]; then
    echo "localhost ansible_connection=local" | tee -a /etc/ansible/hosts
    fi
}

cleanup() {
    apt-get clean
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
}

main() {
    echo "--•--•--•--•--•--•--•--•--•--•--•--•--•--•--•--•--"
    echo "Install ALL software for this OpenKilda maxinet VM"
    echo "--•--•--•--•--•--•--•--•--•--•--•--•--•--•--•--•--"
    update
    install_networking
    install_docker
    #install_java
    #install_maven
    #install_python
    #install_ansible
    cleanup
}

main
