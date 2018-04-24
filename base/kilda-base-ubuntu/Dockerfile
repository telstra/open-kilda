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

FROM ubuntu:xenial

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update

#############################
#Base container configuration
#############################
RUN apt-get autoremove

#####################
#Install Ansible and APT deps
#####################
RUN apt-get install -y build-essential apt-utils libffi-dev libssl-dev software-properties-common
RUN apt-get install -y curl ssh iputils-ping
RUN apt-get install -y python python-dev python-pip
RUN pip install --upgrade pip

################
#Install Ansible
################
RUN pip install ansible
RUN chmod +x /usr/local/bin/ansible*
RUN mkdir /etc/ansible
#Sets the default context of Ansible to be the local container
ADD config/hosts /etc/ansible/hosts 

################################
#Install Java8 in base container
################################
RUN add-apt-repository ppa:webupd8team/java \
    && apt-get update
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
RUN apt-get install -y oracle-java8-installer
ENV JAVA_VER 8
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle
RUN echo "export JAVA_HOME=/usr/lib/jvm/java-8-oracle" >> ~/.bashrc

RUN rm -rf /var/lib/apt/lists/*
RUN apt-get clean
RUN apt clean
ADD app /app
RUN chmod 777 /app/*
