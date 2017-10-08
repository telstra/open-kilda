#!/bin/bash
# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#


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

install_basics() {
    echo "--•--•--•--•--•--•--•--•--"
    echo "--•--•-   BASICS   -•--•--"
    echo "--•--•--•--•--•--•--•--•--"
    apt-get install -y apt-utils
    apt-get install -y --no-install-recommends \
        sudo \
        curl \
        vim \
        software-properties-common \
        git

}

install_networking() {
    echo "--•--•--•--•--•--•--•--•--"
    echo "--•--•- NETWORKING -•--•--"
    echo "--•--•--•--•--•--•--•--•--"
    apt-get install -y --no-install-recommends \
        iproute2 \
        iputils-ping \
        net-tools \
        tcpdump
}

install_docker(){
    echo "--•--•--•--•--•--•--•--•--"
    echo "--•--•--  DOCKER  --•--•--"
    echo "--•--•--•--•--•--•--•--•--"

    apt-get update
    apt-get install -y --no-install-recommends \
        apt-transport-https \
        ca-certificates \
        curl \
        software-properties-common

    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
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

    curl -L https://github.com/docker/compose/releases/download/1.15.0/docker-compose-`uname -s`-`uname -m` -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
    docker-compose --version
}

install_java(){
    echo "--•--•--•--•--•--•--•--•--"
    echo "--•--•--   JAVA   --•--•--"
    echo "--•--•--•--•--•--•--•--•--"

    apt-get update

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

    apt-get install -y --no-install-recommends \
        python \
        python-pip \
        python-software-properties

    pip install --upgrade pip
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

#cleanup() {
#    # apt-get clean
#    # rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
#}

main() {
    echo "--•--•--•--•--•--•--•--•--•--•--•--•--•--•--•--•--"
    echo "Install ALL software for this OpenKilda maxinet VM"
    echo "--•--•--•--•--•--•--•--•--•--•--•--•--•--•--•--•--"
    update
    install_basics
    install_python
    install_networking
    install_docker
    install_java
    install_maven
    install_ansible
#    cleanup
}

main
