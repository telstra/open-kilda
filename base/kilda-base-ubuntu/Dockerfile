FROM ubuntu:wily
RUN apt-get update
RUN apt-get install -y build-essential libffi-dev libssl-dev python python-dev python-pip ssh software-properties-common
RUN pip install --upgrade pip
RUN pip install ansible
RUN chmod +x /usr/local/bin/ansible*
RUN add-apt-repository ppa:webupd8team/java -y
RUN apt-get update
RUN echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
RUN apt-get install -y oracle-java8-installer
RUN apt-get clean
