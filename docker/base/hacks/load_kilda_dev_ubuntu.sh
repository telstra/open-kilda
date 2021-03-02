#!/usr/bin/env bash
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


echo ##########################
echo ##  DOCKER
echo ##########################

export DEBIAN_FRONTEND=noninteractive

sudo apt-get update
sudo apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    software-properties-common

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo apt-key fingerprint 0EBFCD88

sudo add-apt-repository \
   "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
   $(lsb_release -cs) \
   stable"

sudo apt-get update
sudo apt-get install -y docker-ce
sudo groupadd docker
sudo usermod -aG docker $USER
docker run hello-world

echo ##########################
echo ##  JAVA
echo ##########################

sudo apt-get update

# need the following install in order to do add-apt-repository
sudo apt-get install -y software-properties-common python-software-properties

export JAVA_VER=8
export JAVA_HOME=/usr/lib/jvm/java-8-oracle

# need to silence the "accept license"
sudo echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections
sudo echo debconf shared/accepted-oracle-license-v1-1 seen true | sudo debconf-set-selections
sudo add-apt-repository -y ppa:webupd8team/java

sudo apt-get update

sudo apt-get install -y oracle-java8-installer
# to guarantee java 8 is the default .. just installing it doesn't make this guarantee
sudo apt-get install -y oracle-java8-set-default

sudo update-java-alternatives -s java-8-oracle
sudo echo "export JAVA_HOME=/usr/lib/jvm/java-8-oracle" >> ~/.bashrc
sudo apt-get clean && sudo rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

echo ##########################
echo ##  MAVEN
echo ##########################

sudo apt-get update
sudo apt-get install -y maven
sudo echo "export MAVEN_VERSION=3.3.9" >> ~/.bashrc
sudo echo "export MAVEN_HOME=/usr/share/maven" >> ~/.bashrc

sudo apt-get clean && sudo rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

echo ##########################
echo ##  PYTHON ETC
echo ##########################

sudo apt-get update
sudo apt-get install -y apt-utils python python-pip tox
sudo pip install --upgrade pip
sudo     pip install docker-compose

