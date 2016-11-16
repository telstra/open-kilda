FROM ubuntu:wily
RUN apt-get update

#############################
#Base container configuration
#############################
RUN DEBIAN_FRONTEND=noninteractive apt-get autoremove

#####################
#Install Ansible deps
#####################
RUN apt-get install -y build-essential libffi-dev libssl-dev python python-dev python-pip ssh software-properties-common apt-utils
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
RUN add-apt-repository ppa:webupd8team/java -y
RUN apt-get update
RUN echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
RUN apt-get install -y oracle-java8-installer
RUN apt-get clean
ADD app /app
RUN chmod 777 /app/*
