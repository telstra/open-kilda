FROM ubuntu:xenial

RUN apt-get update

#############################
#Base container configuration
#############################
RUN DEBIAN_FRONTEND=noninteractive apt-get autoremove

#####################
#Install Ansible deps
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
RUN apt-get install -y openjdk-8-jdk
ENV export JAVA_VER=8
ENV export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
RUN echo "export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64" >> ~/.bashrc
RUN echo "export JAVA_VER=8" >> ~/.bashrc

RUN apt-get clean
ADD app /app
RUN chmod 777 /app/*
