FROM ubuntu:wily
RUN apt-get update
RUN apt-get install -y build-essential libffi-dev libssl-dev python python-dev python-pip ssh 
RUN pip install --upgrade pip
RUN pip install ansible
RUN chmod +x /usr/local/bin/ansible*